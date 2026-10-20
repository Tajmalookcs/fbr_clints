package pk.fbrclients.app.ui.browser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import androidx.webkit.WebViewFeature
import androidx.webkit.WebSettingsCompat
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.mutableStateListOf
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.webkit.WebResourceResponse
import android.webkit.WebResourceError
import android.webkit.ValueCallback
import android.webkit.SslErrorHandler
import android.webkit.PermissionRequest
import android.webkit.JsResult
import android.webkit.ConsoleMessage
import android.net.http.SslError
import android.app.AlertDialog as PageDialog
import androidx.compose.ui.Alignment
import androidx.compose.material3.TextButton
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.Box
import android.os.Message
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.View
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.flow.first
import pk.fbrclients.app.data.Client
import pk.fbrclients.app.data.ClientDao
import pk.fbrclients.app.data.ElectricityDao
import pk.fbrclients.app.ui.clients.FbrTopBar
import pk.fbrclients.app.util.Actions
import pk.fbrclients.app.util.Formats

/**
 * In-app browser for IRIS and the bill / tax-certificate sites. Fills the client's
 * details into the page; never presses a button on the page. Nothing is saved:
 * cookies, storage and cache are wiped when the browser closes.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    startUrl: String,
    clientId: Long,
    reference: String?,
    clients: ClientDao,
    electricity: ElectricityDao,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    var client by remember { mutableStateOf<Client?>(null) }
    var extraLinks by remember { mutableStateOf<List<String>>(emptyList()) }
    var ready by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(0) }
    var currentUrl by remember { mutableStateOf(startUrl) }
    var menu by remember { mutableStateOf(false) }
    val prefs = remember { ctx.getSharedPreferences("browser", Context.MODE_PRIVATE) }
    var desktop by remember { mutableStateOf(prefs.getBoolean("desktop_site", true)) }
    /** Safe mode: the app makes no changes to pages (no zoom unlock, wide layout or popup fit). */
    var safeMode by remember { mutableStateOf(prefs.getBoolean("safe_mode", false)) }
    val mobileUa = remember { WebSettings.getDefaultUserAgent(ctx) }
    val webView = remember { mutableStateOf<WebView?>(null) }
    /** A window the page opened (window.open / target=_blank), shown on top. */
    val popup = remember { mutableStateOf<WebView?>(null) }

    /** "Page problems": errors the pages report, newest last (kept in memory only). */
    val problems = remember { mutableStateListOf<String>() }
    var showProblems by remember { mutableStateOf(false) }
    fun log(msg: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        problems.add("$time  $msg")
        if (problems.size > 150) problems.removeAt(0)
    }

    // File upload boxes on the page ("Choose file").
    var fileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res.resultCode, res.data))
        fileCallback = null
    }

    /** The page the fill buttons and menu act on: the popup when one is open. */
    fun active(): WebView? = popup.value ?: webView.value

    LaunchedEffect(clientId) {
        client = if (clientId > 0) clients.get(clientId) else null
        extraLinks = electricity.discos().first().flatMap { listOf(it.billUrl, it.certUrl) }.filter { it.isNotBlank() }
        ready = true
    }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    fun fill(value: String?, isPassword: Boolean, what: String) {
        val wv = active() ?: return
        if (value.isNullOrEmpty()) return toast("No $what saved for this client")
        wv.evaluateJavascript(BrowserRules.fillFocused(value, isPassword)) { r ->
            when (r?.trim('"')) {
                "ok" -> Unit
                "password-only" -> toast("That is a password box")
                else -> toast("Tap a box on the page first")
            }
        }
    }

    fun closePopup() {
        popup.value?.let { it.stopLoading(); it.destroy() }
        popup.value = null
    }

    fun fitToScreen(wv: WebView) {
        var n = 0
        while (n < 30 && wv.zoomOut()) n++
    }

    /** Settings shared by the main page and any popup window. */
    fun configure(wv: WebView, isPopup: Boolean) {
        // Android's own autofill must not offer to save client passwords.
        wv.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            userAgentString = if (desktop) BrowserRules.desktopUserAgent(mobileUa) else mobileUa
            // Like Chrome: allow pages that load a few parts over http.
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            mediaPlaybackRequiresUserGesture = true
        }
        // Don't tell sites the page is inside an app (Android adds this header otherwise).
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(wv.settings, emptySet())
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                // Frames inside a page (popups, embedded forms) always load; only whole-page moves are checked.
                if (!request.isForMainFrame) return false
                if (BrowserRules.isAllowed(url, extraLinks)) return false
                log("Opened outside the app: $url")
                // Other sites (and tel:, mailto: …) open outside the app.
                Actions.openUrl(view.context, url)
                if (isPopup && view.url.isNullOrBlank()) closePopup()
                return true
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                log("Load error ${error.errorCode} \"${error.description}\": ${request.url}")
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (response.statusCode >= 400) log("HTTP ${response.statusCode}: ${request.url}")
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                log("Security (SSL) error on ${error.url} — blocked")
                handler.cancel()
            }

            override fun onPageCommitVisible(view: WebView, url: String) {
                if (!safeMode) {
                    view.evaluateJavascript(BrowserRules.viewportJs(desktop), null)
                    view.evaluateJavascript(BrowserRules.FIT_POPUPS_JS, null)
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (!safeMode) {
                    view.evaluateJavascript(BrowserRules.viewportJs(desktop), null)
                    view.evaluateJavascript(BrowserRules.FIT_POPUPS_JS, null)
                }
                if (!isPopup) {
                    currentUrl = url
                    title = view.title.orEmpty()
                }
                val c = client
                BrowserRules.autoFill(url, c?.cnic?.let(Formats::irisUsername), c?.irisPassword, reference)?.let { js ->
                    view.evaluateJavascript(js, null)
                }
            }
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (!isPopup) progress = newProgress
            }

            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                if (m.messageLevel() == ConsoleMessage.MessageLevel.ERROR || m.messageLevel() == ConsoleMessage.MessageLevel.WARNING) {
                    log("${m.messageLevel().name.lowercase()}: ${m.message()} (${m.sourceId().substringAfterLast('/')}:${m.lineNumber()})")
                }
                return true
            }

            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    fileLauncher.launch(params.createIntent())
                    true
                } catch (e: Exception) {
                    fileCallback = null
                    log("File chooser failed: ${e.message}")
                    false
                }
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                log("Site asked for: ${request.resources.joinToString()} — not allowed")
                request.deny()
            }

            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                PageDialog.Builder(view.context).setMessage(message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                    .setOnCancelListener { result.cancel() }.show()
                return true
            }

            override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                PageDialog.Builder(view.context).setMessage(message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
                    .setOnCancelListener { result.cancel() }.show()
                return true
            }

            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                closePopup()
                log("Site opened a new window")
                val child = WebView(view.context)
                configure(child, isPopup = true)
                popup.value = child
                (resultMsg.obj as WebView.WebViewTransport).webView = child
                resultMsg.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView) {
                if (window == popup.value) closePopup()
            }
        }
        wv.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            download(wv.context, url, userAgent, contentDisposition, mimeType)
        }
    }

    BackHandler {
        val p = popup.value
        val wv = webView.value
        when {
            p != null && p.canGoBack() -> p.goBack()
            p != null -> closePopup()
            wv != null && wv.canGoBack() -> wv.goBack()
            else -> onClose()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            closePopup()
            webView.value?.apply {
                stopLoading()
                clearHistory()
                clearCache(true)
                clearFormData()
                destroy()
            }
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
        }
    }

    Scaffold(
        topBar = {
            FbrTopBar(title = title.ifBlank { BrowserRules.host(currentUrl) ?: "" }, onBack = null) {
                IconButton(onClick = { active()?.reload() }) { Icon(Icons.Filled.Refresh, contentDescription = "Reload") }
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Page check") }, onClick = {
                        menu = false
                        active()?.evaluateJavascript(BrowserRules.PAGE_CHECK_JS) { r ->
                            // The result comes back as a JSON string.
                            val text = runCatching { org.json.JSONArray("[$r]").getString(0) }.getOrDefault(r.orEmpty())
                            log("Page check (safe mode ${if (safeMode) "on" else "off"}, desktop ${if (desktop) "on" else "off"}):\n$text")
                            showProblems = true
                        }
                    })
                    DropdownMenuItem(text = { Text("Page problems (${problems.size})") }, onClick = {
                        menu = false
                        showProblems = true
                    })
                    DropdownMenuItem(text = { Text("Fit to screen") }, onClick = {
                        menu = false
                        active()?.let(::fitToScreen)
                    })
                    DropdownMenuItem(
                        text = { Text("Desktop site") },
                        trailingIcon = { Checkbox(checked = desktop, onCheckedChange = null) },
                        onClick = {
                            menu = false
                            desktop = !desktop
                            prefs.edit().putBoolean("desktop_site", desktop).apply()
                            listOfNotNull(webView.value, popup.value).forEach { wv ->
                                wv.settings.userAgentString = if (desktop) BrowserRules.desktopUserAgent(mobileUa) else mobileUa
                                wv.reload()
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Safe mode (no page changes)") },
                        trailingIcon = { Checkbox(checked = safeMode, onCheckedChange = null) },
                        onClick = {
                            menu = false
                            safeMode = !safeMode
                            prefs.edit().putBoolean("safe_mode", safeMode).apply()
                            log("Safe mode " + if (safeMode) "on" else "off")
                            listOfNotNull(webView.value, popup.value).forEach { it.reload() }
                        },
                    )
                    DropdownMenuItem(text = { Text("Save page as PDF / Print") }, onClick = {
                        menu = false
                        active()?.let { printPage(ctx, it, title) }
                    })
                    DropdownMenuItem(text = { Text("Open in Chrome") }, onClick = {
                        menu = false
                        Actions.openUrl(ctx, active()?.url ?: currentUrl)
                    })
                }
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close browser") }
            }
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.navigationBarsPadding().imePadding()) {
                    Text(
                        (client?.name?.let { "$it · " } ?: "") + "tap a box, then:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp, top = 6.dp),
                    )
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val c = client
                        if (c != null) {
                            FilledTonalButton(onClick = { fill(Formats.irisUsername(c.cnic), false, "username") }) { Text("Username") }
                            FilledTonalButton(onClick = { fill(c.irisPassword, true, "password") }) { Text("Password") }
                        }
                        if (!reference.isNullOrEmpty()) {
                            FilledTonalButton(onClick = { fill(reference, false, "reference") }) { Text("Reference") }
                        }
                        if (c != null && c.ntn.isNotBlank()) {
                            FilledTonalButton(onClick = { fill(c.ntn, false, "NTN") }) { Text("NTN") }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                if (ready) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            WebView(context).apply {
                                configure(this, isPopup = false)
                                webView.value = this
                                if (BrowserRules.isAllowed(startUrl, extraLinks)) {
                                    loadUrl(startUrl)
                                } else {
                                    Actions.openUrl(context, startUrl)
                                    onClose()
                                }
                            }
                        },
                    )
                }
            }
            // A window opened by the page, on top of it, with its own Close button.
            popup.value?.let { p ->
                key(p) {
                    Surface(Modifier.fillMaxSize(), tonalElevation = 2.dp) {
                        Column(Modifier.fillMaxSize()) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Window opened by the site", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                                TextButton(onClick = ::closePopup) { Text("Close window") }
                            }
                            AndroidView(modifier = Modifier.fillMaxSize(), factory = { p })
                        }
                    }
                }
            }
        }
    }

    if (showProblems) {
        AlertDialog(
            onDismissRequest = { showProblems = false },
            title = { Text("Page problems") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "Page: " + (active()?.url ?: currentUrl),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (problems.isEmpty()) Text("No problems reported.")
                    SelectionContainer {
                        Text(problems.joinToString("\n\n"), style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showProblems = false }) { Text("Close") } },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        clipboardText(ctx, "Page problems", (listOf("Page: " + (active()?.url ?: currentUrl)) + problems).joinToString("\n\n"))
                    }) { Text("Copy") }
                    TextButton(onClick = { problems.clear() }) { Text("Clear") }
                }
            },
        )
    }
}

/** Saves a bill / certificate file to the phone's Downloads folder. */
private fun download(ctx: Context, url: String, userAgent: String, contentDisposition: String?, mimeType: String?) {
    try {
        if (!url.startsWith("http")) {
            Toast.makeText(ctx, "This file can't be downloaded here. Use ⋮ → Save page as PDF.", Toast.LENGTH_LONG).show()
            return
        }
        val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val req = DownloadManager.Request(Uri.parse(url))
            .setMimeType(mimeType)
            .addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url).orEmpty())
            .addRequestHeader("User-Agent", userAgent)
            .setTitle(name)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
        ctx.getSystemService(DownloadManager::class.java).enqueue(req)
        Toast.makeText(ctx, "Downloading $name to Downloads", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(ctx, "Download failed. Try ⋮ → Save page as PDF.", Toast.LENGTH_LONG).show()
    }
}

/** Android's print screen, where "Save as PDF" is one of the printers. */
private fun printPage(ctx: Context, wv: WebView, title: String) {
    val pm = ctx.getSystemService(PrintManager::class.java)
    val name = title.ifBlank { "FBR Clients page" }
    pm.print(name, wv.createPrintDocumentAdapter(name), PrintAttributes.Builder().build())
}

/** Plain (not sensitive) copy, for sending page details to support. */
private fun clipboardText(ctx: Context, label: String, text: String) {
    ctx.getSystemService(android.content.ClipboardManager::class.java)
        .setPrimaryClip(android.content.ClipData.newPlainText(label, text))
    Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
}
