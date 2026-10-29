package pk.fbrclients.app.ui.browser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import androidx.core.view.doOnLayout
import androidx.webkit.WebViewCompat
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
import kotlinx.coroutines.launch
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

    /** A downloaded file waiting for the owner to confirm its name. */
    var pendingFile by remember { mutableStateOf<PendingFile?>(null) }
    /** The last saved file, with Send / Open buttons (owner, 2026-10-21). */
    var saved by remember { mutableStateOf<SavedFile?>(null) }
    var chooseWhatsApp by remember { mutableStateOf<SavedFile?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Location / camera asked for by IRIS (owner, 2026-10-29): Android asks the owner, then the page gets the answer.
    var permissionAnswer by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val ok = res.values.any { it }
        permissionAnswer?.invoke(ok)
        permissionAnswer = null
    }
    fun askAndroid(perms: Array<String>, answer: (Boolean) -> Unit) {
        if (perms.all { androidx.core.content.ContextCompat.checkSelfPermission(ctx, it) == android.content.pm.PackageManager.PERMISSION_GRANTED }) {
            answer(true); return
        }
        permissionAnswer?.invoke(false)
        permissionAnswer = answer
        permissionLauncher.launch(perms)
    }
    val registration = BrowserRules.isRegistration(startUrl)

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
            @Suppress("DEPRECATION")
            setGeolocationEnabled(true)
        }
        // Don't tell sites the page is inside an app (Android adds this header otherwise).
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(wv.settings, emptySet())
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)

        // Files the page makes itself (e.g. the IRIS return from Print) and the page's Print command.
        wv.addJavascriptInterface(
            PageBridge(
                onFile = { bytes, mime ->
                    val c = client
                    pendingFile = PendingFile.Bytes(bytes, DownloadNames.suggest(c?.name, wv.url ?: currentUrl, null, mime), mime)
                },
                onPrint = { printPage(ctx, wv, DownloadNames.suggest(client?.name, wv.url ?: currentUrl, null, "application/pdf").removeSuffix(".pdf")) },
                onError = { msg -> log("Download failed: $msg"); toast("Couldn't save this file. Try ⋮ → Save page as PDF.") },
            ),
            "FbrDownload",
        )
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            runCatching { WebViewCompat.addDocumentStartJavaScript(wv, BrowserRules.PAGE_START_JS, setOf("*")) }
        }

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
                view.evaluateJavascript(BrowserRules.PAGE_START_JS, null)
                if (!safeMode) {
                    view.evaluateJavascript(BrowserRules.viewportJs(desktop), null)
                    view.evaluateJavascript(BrowserRules.FIT_POPUPS_JS, null)
                    view.evaluateJavascript(BrowserRules.FIX_ZERO_HEIGHT_JS, null)
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (!safeMode) {
                    view.evaluateJavascript(BrowserRules.viewportJs(desktop), null)
                    view.evaluateJavascript(BrowserRules.FIT_POPUPS_JS, null)
                    view.evaluateJavascript(BrowserRules.FIX_ZERO_HEIGHT_JS, null)
                }
                if (!isPopup) {
                    currentUrl = url
                    title = view.title.orEmpty()
                }
                val c = client
                if (registration || BrowserRules.isRegistration(url)) {
                    if (c != null) view.evaluateJavascript(registrationJs(c), null)
                } else {
                    BrowserRules.autoFill(url, c?.cnic?.let(Formats::irisUsername), c?.irisPassword, reference)?.let { js ->
                        view.evaluateJavascript(js, null)
                    }
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

            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: android.webkit.GeolocationPermissions.Callback) {
                log("Site asked for location: $origin")
                if (!BrowserRules.isAllowed(origin.trimEnd('/') + "/", extraLinks)) { callback.invoke(origin, false, false); return }
                askAndroid(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)) { ok ->
                    log(if (ok) "Location allowed" else "Location not allowed")
                    callback.invoke(origin, ok, false)
                }
            }

            override fun onPermissionRequest(request: android.webkit.PermissionRequest) {
                val origin = request.origin.toString()
                log("Site asked for: " + request.resources.joinToString { it.substringAfterLast('.') } + " ($origin)")
                val camera = request.resources.filter { it == android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE }
                if (camera.isEmpty() || !BrowserRules.isAllowed(origin.trimEnd('/') + "/", extraLinks)) { request.deny(); return }
                askAndroid(arrayOf(android.Manifest.permission.CAMERA)) { ok ->
                    log(if (ok) "Camera allowed" else "Camera not allowed")
                    if (ok) request.grant(camera.toTypedArray()) else request.deny()
                }
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
            val original = runCatching { URLUtil.guessFileName(url, contentDisposition, mimeType) }.getOrNull()
            val name = DownloadNames.suggest(client?.name, wv.url ?: currentUrl, original, mimeType)
            when {
                url.startsWith("blob:") -> wv.evaluateJavascript(BrowserRules.fetchBlobJs(url, mimeType.orEmpty()), null)
                url.startsWith("data:") -> dataUrlBytes(url)?.let { (bytes, mime) ->
                    pendingFile = PendingFile.Bytes(bytes, DownloadNames.suggest(client?.name, wv.url ?: currentUrl, null, mime), mime)
                } ?: toast("Couldn't read this file. Try ⋮ → Save page as PDF.")
                url.startsWith("http") -> pendingFile = PendingFile.Web(url, userAgent, mimeType, name)
                else -> toast("This file can't be downloaded here. Try ⋮ → Save page as PDF.")
            }
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
                        active()?.let { printPage(ctx, it, DownloadNames.suggest(client?.name, it.url ?: currentUrl, null, "application/pdf").removeSuffix(".pdf")) }
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
                    saved?.let { f ->
                        Row(
                            Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Text(
                                "Saved: ${f.name}",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { saved = null }) { Icon(Icons.Filled.Close, contentDescription = "Hide") }
                        }
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            androidx.compose.material3.Button(onClick = {
                                val apps = whatsAppsInstalled(ctx)
                                when (apps.size) {
                                    0 -> toast("WhatsApp is not installed")
                                    1 -> sendOnWhatsApp(ctx, f, apps[0], client?.mobile)
                                    else -> chooseWhatsApp = f
                                }
                            }) { Text("WhatsApp client") }
                            androidx.compose.material3.OutlinedButton(onClick = { shareFile(ctx, f) }) { Text("Share…") }
                            androidx.compose.material3.OutlinedButton(onClick = { openFile(ctx, f) }) { Text("Open") }
                        }
                        androidx.compose.material3.HorizontalDivider(Modifier.padding(top = 6.dp))
                    }
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
                        if (c != null && registration) {
                            if (c.cnic.length == 15) FilledTonalButton(onClick = { fill(c.cnic, false, "CNIC") }) { Text("CNIC") }
                            FilledTonalButton(onClick = { fill(c.name, false, "name") }) { Text("Name") }
                            FilledTonalButton(onClick = { fill(Formats.digitsOnly(c.mobile), false, "mobile") }) { Text("Mobile") }
                            FilledTonalButton(onClick = { fill(c.email, false, "email") }) { Text("Email") }
                            FilledTonalButton(onClick = { fill(c.address, false, "address") }) { Text("Address") }
                        }
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
                                    // Load only once the view has its real size, so the page
                                    // measures the screen height correctly (vh units).
                                    doOnLayout { it.post { (it as WebView).loadUrl(startUrl) } }
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

    pendingFile?.let { f ->
        SaveFileDialog(
            suggested = f.suggested,
            onDismiss = { pendingFile = null },
            onSave = { typed ->
                pendingFile = null
                val ext = DownloadNames.extension(f.suggested, null)
                val name = DownloadNames.finalName(typed, ext)
                scope.launch {
                    val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        when (f) {
                            is PendingFile.Web -> fetchBytes(f.url, f.userAgent)?.let { (bytes, type) ->
                                saveToDownloads(ctx, bytes, name, f.mime?.takeIf { it.isNotBlank() && it != "application/octet-stream" } ?: type)
                            }
                            is PendingFile.Bytes -> saveToDownloads(ctx, f.bytes, name, f.mime)
                        }
                    }
                    if (result == null) toast("Couldn't save the file. Try ⋮ → Save page as PDF.")
                    else saved = result
                }
            },
        )
    }

    chooseWhatsApp?.let { f ->
        AlertDialog(
            onDismissRequest = { chooseWhatsApp = null },
            title = { Text("Send with") },
            text = { Text("Which WhatsApp?") },
            confirmButton = {
                TextButton(onClick = { chooseWhatsApp = null; sendOnWhatsApp(ctx, f, WHATSAPP, client?.mobile) }) { Text("WhatsApp") }
            },
            dismissButton = {
                TextButton(onClick = { chooseWhatsApp = null; sendOnWhatsApp(ctx, f, WHATSAPP_BUSINESS, client?.mobile) }) { Text("WhatsApp Business") }
            },
        )
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

/** A downloaded file waiting for its name. */
private sealed interface PendingFile {
    val suggested: String

    class Web(val url: String, val userAgent: String, val mime: String?, override val suggested: String) : PendingFile
    class Bytes(val bytes: ByteArray, override val suggested: String, val mime: String) : PendingFile
}

/** Called by the page (JavaScript): a file it made, or its Print command. */
private class PageBridge(
    private val onFile: (ByteArray, String) -> Unit,
    private val onPrint: () -> Unit,
    private val onError: (String) -> Unit,
) {
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    @android.webkit.JavascriptInterface
    fun save(base64: String, mime: String) {
        val bytes = runCatching { android.util.Base64.decode(base64, android.util.Base64.DEFAULT) }.getOrNull()
        main.post { if (bytes != null && bytes.isNotEmpty()) onFile(bytes, mime.ifBlank { "application/pdf" }) else onError("empty file") }
    }

    @android.webkit.JavascriptInterface
    fun failed(message: String) {
        main.post { onError(message) }
    }

    @android.webkit.JavascriptInterface
    fun print() {
        main.post { onPrint() }
    }
}

/** "data:application/pdf;base64,…" → bytes and type. */
private fun dataUrlBytes(url: String): Pair<ByteArray, String>? = runCatching {
    val head = url.substringBefore(',')
    val body = url.substringAfter(',')
    val mime = head.removePrefix("data:").substringBefore(';').ifBlank { "application/octet-stream" }
    val bytes = if (head.contains(";base64")) android.util.Base64.decode(body, android.util.Base64.DEFAULT)
    else java.net.URLDecoder.decode(body, "UTF-8").toByteArray()
    bytes to mime
}.getOrNull()

/** A file saved in Downloads: its address for sharing / opening, and its name. */
private class SavedFile(val uri: Uri, val name: String, val mime: String)

/** Saves bytes into the phone's Downloads folder (Android adds (1) etc. if the name exists). */
private fun saveToDownloads(ctx: Context, bytes: ByteArray, name: String, mime: String): SavedFile? = runCatching {
    if (android.os.Build.VERSION.SDK_INT >= 29) {
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = ctx.contentResolver
        val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@runCatching null
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return@runCatching null
        resolver.update(uri, android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        val shown = resolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: name
        SavedFile(uri, shown, mime)
    } else {
        // Android 8–9: the app's own Downloads folder (no storage permission needed).
        val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return@runCatching null
        var f = java.io.File(dir, name)
        var n = 1
        while (f.exists()) f = java.io.File(dir, name.substringBeforeLast('.') + " (${n++})." + name.substringAfterLast('.'))
        f.writeBytes(bytes)
        SavedFile(androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f), f.name, mime)
    }
}.getOrNull()

/** Gets a file from the site with the browser's cookies (the login), for saving under the chosen name. */
private fun fetchBytes(url: String, userAgent: String): Pair<ByteArray, String>? = runCatching {
    var u = java.net.URL(url)
    repeat(5) {
        val c = u.openConnection() as java.net.HttpURLConnection
        c.instanceFollowRedirects = false
        c.connectTimeout = 20_000
        c.readTimeout = 60_000
        c.setRequestProperty("User-Agent", userAgent)
        CookieManager.getInstance().getCookie(u.toString())?.let { c.setRequestProperty("Cookie", it) }
        val code = c.responseCode
        if (code in 300..399) {
            val next = c.getHeaderField("Location") ?: return@runCatching null
            c.disconnect()
            u = java.net.URL(u, next)
            if (u.protocol != "https") return@runCatching null
            return@repeat
        }
        if (code !in 200..299) return@runCatching null
        val type = c.contentType?.substringBefore(';')?.trim() ?: "application/pdf"
        val bytes = c.inputStream.use { it.readBytes() }
        c.disconnect()
        return@runCatching bytes to type
    }
    null
}.getOrNull()

private const val WHATSAPP = "com.whatsapp"
private const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"

private fun whatsAppsInstalled(ctx: Context): List<String> = listOf(WHATSAPP, WHATSAPP_BUSINESS).filter { p ->
    runCatching { ctx.packageManager.getPackageInfo(p, 0) }.isSuccess
}

/** Opens WhatsApp with the file attached: straight to the client's chat when the mobile is saved, else the contact list. */
private fun sendOnWhatsApp(ctx: Context, f: SavedFile, app: String, mobile: String?) {
    val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = f.mime
        putExtra(android.content.Intent.EXTRA_STREAM, f.uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        setPackage(app)
        mobile?.let(Formats::whatsAppNumber)?.let { putExtra("jid", "$it@s.whatsapp.net") }
    }
    try {
        ctx.startActivity(i)
    } catch (_: android.content.ActivityNotFoundException) {
        Toast.makeText(ctx, "WhatsApp is not installed", Toast.LENGTH_SHORT).show()
    }
}

private fun shareFile(ctx: Context, f: SavedFile) {
    val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = f.mime
        putExtra(android.content.Intent.EXTRA_STREAM, f.uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(android.content.Intent.createChooser(i, f.name))
}

private fun openFile(ctx: Context, f: SavedFile) {
    val i = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
        setDataAndType(f.uri, f.mime)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        ctx.startActivity(i)
    } catch (_: android.content.ActivityNotFoundException) {
        Toast.makeText(ctx, "No app to open this file", Toast.LENGTH_SHORT).show()
    }
}

/** Asks for the file name before saving (owner, 2026-10-21). */
@Composable
private fun SaveFileDialog(suggested: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember {
        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(suggested, androidx.compose.ui.text.TextRange(0, suggested.substringBeforeLast('.').length)))
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save file") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("It will be saved in Downloads.")
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("File name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onSave(text.text) }, enabled = text.text.isNotBlank()) { Text("Save") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Android's print screen, where "Save as PDF" is one of the printers. [title] becomes the PDF's name. */
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

/** The registration form fill for [c] (only the details that are saved). */
private fun registrationJs(c: Client) = BrowserRules.registrationFill(
    cnic = if (c.cnic.length == 15) c.cnic else "",
    name = if (c.name != c.cnic) c.name else "",
    mobile = Formats.digitsOnly(c.mobile),
    email = c.email,
    address = c.address,
)
