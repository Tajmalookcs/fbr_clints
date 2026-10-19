package pk.fbrclients.app.ui.browser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.View
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
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
    val webView = remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(clientId) {
        client = if (clientId > 0) clients.get(clientId) else null
        extraLinks = electricity.discos().first().flatMap { listOf(it.billUrl, it.certUrl) }.filter { it.isNotBlank() }
        ready = true
    }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    fun fill(value: String?, isPassword: Boolean, what: String) {
        val wv = webView.value ?: return
        if (value.isNullOrEmpty()) return toast("No $what saved for this client")
        wv.evaluateJavascript(BrowserRules.fillFocused(value, isPassword)) { r ->
            when (r?.trim('"')) {
                "ok" -> Unit
                "password-only" -> toast("That is a password box")
                else -> toast("Tap a box on the page first")
            }
        }
    }

    BackHandler {
        val wv = webView.value
        if (wv != null && wv.canGoBack()) wv.goBack() else onClose()
    }

    DisposableEffect(Unit) {
        onDispose {
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
                IconButton(onClick = { webView.value?.reload() }) { Icon(Icons.Filled.Refresh, contentDescription = "Reload") }
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Save page as PDF / Print") }, onClick = {
                        menu = false
                        webView.value?.let { printPage(ctx, it, title) }
                    })
                    DropdownMenuItem(text = { Text("Open in Chrome") }, onClick = {
                        menu = false
                        Actions.openUrl(ctx, currentUrl)
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
                            FilledTonalButton(onClick = { fill(c.cnic, false, "username") }) { Text("Username") }
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
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            if (!ready) return@Column
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        // Android's own autofill must not offer to save client passwords.
                        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.setSupportMultipleWindows(false)
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        CookieManager.getInstance().setAcceptCookie(true)

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val url = request.url.toString()
                                if (BrowserRules.isAllowed(url, extraLinks)) return false
                                // Other sites (and tel:, mailto: …) open outside the app.
                                Actions.openUrl(context, url)
                                return true
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                currentUrl = url
                                title = view.title.orEmpty()
                                val c = client
                                BrowserRules.autoFill(url, c?.cnic, c?.irisPassword, reference)?.let { js ->
                                    view.evaluateJavascript(js, null)
                                }
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                progress = newProgress
                            }
                        }
                        setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                            download(context, url, userAgent, contentDisposition, mimeType)
                        }
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
