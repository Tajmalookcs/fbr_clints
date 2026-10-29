package pk.fbrclients.desktop.browser

import me.friwi.jcefmaven.CefAppBuilder
import me.friwi.jcefmaven.EnumProgress
import org.cef.CefApp
import org.cef.CefClient
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefBeforeDownloadCallback
import org.cef.callback.CefDownloadItem
import org.cef.callback.CefDownloadItemCallback
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefDownloadHandler
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.misc.CefPdfPrintSettings
import org.cef.network.CefCookieManager
import org.cef.network.CefRequest
import pk.fbrclients.app.data.Client
import pk.fbrclients.app.ui.browser.BrowserRules
import pk.fbrclients.app.ui.browser.DownloadNames
import pk.fbrclients.app.util.Formats
import pk.fbrclients.desktop.platform.AppPaths
import pk.fbrclients.desktop.platform.Open
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Toolkit
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Timer
import java.util.TimerTask
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JToolBar
import javax.swing.SwingUtilities
import javax.swing.UIManager

/**
 * The in-app browser of the PC app (owner, 2026-10-20): the same Chromium engine as Chrome.
 * Like the phone: shows only IRIS, the bill / certificate sites and the links in Settings
 * (other links open in the normal browser); fills the client's username / password or the
 * bill reference into empty boxes (never presses a button); Fill buttons fill the box you
 * clicked; cookies are wiped when the last browser window closes.
 */
object AppBrowser {
    @Volatile private var app: CefApp? = null
    private val lock = Any()
    private var openWindows = 0
    private val timer = Timer("browser-fill", true)

    /** The engine is unpacked here on first use (once per version). */
    private val installDir: File get() = File(AppPaths.dir, "browser-engine")

    /** Starts the engine (first time: unpacks it, which takes a while). [progress] gets short messages. */
    private fun engine(progress: (String) -> Unit): CefApp = synchronized(lock) {
        app?.let { return it }
        val b = CefAppBuilder()
        b.setInstallDir(installDir)
        b.setProgressHandler { state, percent ->
            when (state) {
                EnumProgress.DOWNLOADING -> progress("Downloading the browser (first time only)… " + (if (percent >= 0) "${percent.toInt()}%" else ""))
                EnumProgress.EXTRACTING -> progress("Preparing the browser (first time only)…")
                EnumProgress.INITIALIZING -> progress("Starting the browser…")
                else -> Unit
            }
        }
        b.cefSettings.windowless_rendering_enabled = false
        b.cefSettings.persist_session_cookies = false
        b.cefSettings.log_severity = CefSettings.LogSeverity.LOGSEVERITY_DISABLE
        // No cache folder: pages, cookies and logins stay in memory only.
        b.cefSettings.cache_path = ""
        b.build().also { app = it }
    }

    /** Stops the engine when the app closes. */
    fun shutdown() {
        runCatching { app?.dispose() }
    }

    data class Request(
        val url: String,
        val client: Client?,
        /** Electricity bill reference number, for bill / certificate pages. */
        val reference: String?,
        /** Bill / certificate links from Settings → Electricity companies. */
        val extraLinks: List<String>,
        val title: String,
    )

    /** Opens a browser window. Runs in the background; [onStatus] shows progress or a problem. */
    fun open(r: Request, onStatus: (String) -> Unit) {
        Thread {
            try {
                val cef = engine(onStatus)
                SwingUtilities.invokeLater { window(cef, r, onStatus) }
            } catch (e: Throwable) {
                onStatus("The browser couldn't start (${e.message ?: e.javaClass.simpleName}). Opening it in your normal browser instead.")
                Open.url(r.url)
            }
        }.apply { isDaemon = true; name = "browser-start" }.start()
    }

    private fun window(cef: CefApp, r: Request, onStatus: (String) -> Unit) {
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
        val client: CefClient = cef.createClient()
        val username = r.client?.cnic?.let(Formats::irisUsername)
        val password = r.client?.irisPassword
        val status = JLabel(" ")
        fun say(s: String) = SwingUtilities.invokeLater { status.text = s }

        fun allowed(url: String): Boolean {
            val scheme = url.substringBefore(':').lowercase()
            return scheme == "about" || scheme == "blob" || scheme == "devtools" || BrowserRules.isAllowed(url, r.extraLinks)
        }

        val registration = BrowserRules.isRegistration(r.url)
        fun autoFill(b: CefBrowser) {
            val c = r.client
            val js = if (registration || BrowserRules.isRegistration(b.url ?: "")) {
                if (c == null) return
                BrowserRules.registrationFill(
                    cnic = if (c.cnic.length == 15) c.cnic else "",
                    name = if (c.name != c.cnic) c.name else "",
                    mobile = Formats.digitsOnly(c.mobile), email = c.email, address = c.address,
                )
            } else BrowserRules.autoFill(b.url, username, password, r.reference) ?: return
            // Sites like IRIS draw the login form a moment after the page loads: try a few times.
            for (delay in listOf(0L, 1_500L, 4_000L)) {
                timer.schedule(object : TimerTask() {
                    override fun run() { runCatching { b.executeJavaScript(js, b.url, 0) } }
                }, delay)
            }
        }

        client.addRequestHandler(object : CefRequestHandlerAdapter() {
            override fun onBeforeBrowse(browser: CefBrowser, frame: CefFrame, request: CefRequest, userGesture: Boolean, isRedirect: Boolean): Boolean {
                val url = request.url ?: return false
                if (!frame.isMain || allowed(url)) return false
                Open.url(url)
                say("Opened in your normal browser: " + (BrowserRules.host(url) ?: url))
                return true
            }
        })
        client.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
            override fun onBeforePopup(browser: CefBrowser, frame: CefFrame, targetUrl: String?, targetFrameName: String?): Boolean {
                val url = targetUrl.orEmpty()
                if (url.isEmpty() || allowed(url)) return false // the site's own window opens on top
                Open.url(url)
                return true
            }
        })
        client.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                if (frame.isMain) autoFill(browser)
            }
        })
        client.addDownloadHandler(object : CefDownloadHandler {
            override fun onBeforeDownload(browser: CefBrowser, item: CefDownloadItem, suggestedName: String, callback: CefBeforeDownloadCallback): Boolean {
                // Ask for the name first (owner, 2026-10-21): "<Client> Return.pdf" from IRIS, etc.
                val suggested = DownloadNames.suggest(r.client?.name, browser.url ?: r.url, suggestedName, item.mimeType)
                val ext = DownloadNames.extension(suggested, null)
                SwingUtilities.invokeLater {
                    val typed = JOptionPane.showInputDialog(
                        SwingUtilities.getWindowAncestor(browser.uiComponent),
                        "File name (saved in Downloads):", "Save file", JOptionPane.PLAIN_MESSAGE, null, null, suggested,
                    ) as String?
                    if (typed.isNullOrBlank()) { say("Download cancelled."); return@invokeLater }
                    val target = uniqueFile(downloadsDir(), DownloadNames.finalName(typed, ext))
                    callback.Continue(target.absolutePath, false)
                    say("Downloading to Downloads: ${target.name}")
                }
                return true
            }

            override fun onDownloadUpdated(browser: CefBrowser, item: CefDownloadItem, callback: CefDownloadItemCallback) {
                if (item.isComplete) say("Saved in Downloads: " + File(item.fullPath ?: "").name)
            }
        })

        val browser = client.createBrowser(r.url, false, false)
        val frame = JFrame("FBR Clients · " + r.title)
        client.addDisplayHandler(object : CefDisplayHandlerAdapter() {
            // IRIS changes page without reloading (e.g. Home → Login): fill again.
            override fun onAddressChange(b: CefBrowser, f: CefFrame?, url: String?) {
                if (f == null || f.isMain) autoFill(b)
            }

            override fun onTitleChange(b: CefBrowser, title: String?) {
                if (b === browser) SwingUtilities.invokeLater { frame.title = "FBR Clients · " + r.title + (title?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") }
            }
        })

        fun button(text: String, tip: String? = null, action: () -> Unit) = JButton(text).apply {
            isFocusable = false // keep the page's box selected when a button is clicked
            toolTipText = tip
            addActionListener { action() }
        }

        fun fill(value: String?, isPassword: Boolean, what: String) {
            if (value.isNullOrEmpty()) { say("No $what saved for this client."); return }
            browser.executeJavaScript(BrowserRules.fillFocused(value, isPassword), browser.url, 0)
            browser.setFocus(true)
            say("$what put in the box you clicked. (Click a box first if nothing happened.)")
        }

        val top = JToolBar().apply {
            isFloatable = false
            add(button("◀", "Back") { browser.goBack() })
            add(button("▶", "Forward") { browser.goForward() })
            add(button("⟳", "Reload") { browser.reload() })
            addSeparator()
            add(button("−", "Zoom out") { browser.zoomLevel = browser.zoomLevel - 0.5 })
            add(button("100%", "Normal size") { browser.zoomLevel = 0.0 })
            add(button("+", "Zoom in") { browser.zoomLevel = browser.zoomLevel + 0.5 })
            addSeparator()
            add(button("Save page as PDF") {
                val suggested = DownloadNames.suggest(r.client?.name, browser.url ?: r.url, null, "application/pdf")
                val typed = JOptionPane.showInputDialog(frame, "File name (saved in Downloads):", "Save page as PDF", JOptionPane.PLAIN_MESSAGE, null, null, suggested) as String?
                if (typed.isNullOrBlank()) return@button
                val f = uniqueFile(downloadsDir(), DownloadNames.finalName(typed, "pdf"))
                browser.printToPDF(f.absolutePath, CefPdfPrintSettings().apply { print_background = true }) { path, ok ->
                    say(if (ok) "Saved in Downloads: " + File(path).name else "Couldn't save the PDF.")
                }
            })
            add(button("Open in normal browser") { Open.url(browser.url) })
        }
        val bottom = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4)).apply {
            if (r.client != null && registration) {
                add(JLabel("Fill:"))
                if (r.client.cnic.length == 15) add(button("CNIC") { fill(r.client.cnic, false, "CNIC") })
                add(button("Name") { fill(r.client.name, false, "Name") })
                add(button("Mobile") { fill(Formats.digitsOnly(r.client.mobile), false, "Mobile") })
                add(button("Email") { fill(r.client.email, false, "Email") })
                add(button("Address") { fill(r.client.address, false, "Address") })
            }
            if (r.client != null) {
                if (!registration) add(JLabel("Fill:"))
                add(button("Username") { fill(username, false, "Username") })
                add(button("Password") { fill(password, true, "Password") })
                if (!r.client.ntn.isBlank()) add(button("NTN") { fill(r.client.ntn, false, "NTN") })
            }
            if (!r.reference.isNullOrEmpty()) add(button("Reference") { fill(r.reference, false, "Reference") })
            add(status)
        }
        bottom.border = BorderFactory.createEmptyBorder(2, 6, 2, 6)

        frame.contentPane.layout = BorderLayout()
        frame.contentPane.add(top, BorderLayout.NORTH)
        frame.contentPane.add(browser.uiComponent, BorderLayout.CENTER)
        frame.contentPane.add(bottom, BorderLayout.SOUTH)
        runCatching { frame.iconImage = ImageIO.read(AppBrowser::class.java.getResource("/app.png")) }
        val screen = Toolkit.getDefaultToolkit().screenSize
        frame.size = Dimension(minOf(1400, screen.width - 80), minOf(920, screen.height - 80))
        frame.setLocationRelativeTo(null)
        frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        frame.addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent) {
                runCatching { browser.close(true) }
                runCatching { client.dispose() }
                synchronized(lock) {
                    openWindows--
                    if (openWindows <= 0) runCatching { CefCookieManager.getGlobalManager()?.deleteCookies("", "") }
                }
            }
        })
        synchronized(lock) { openWindows++ }
        frame.isVisible = true
        onStatus("Browser opened")
    }

    private fun downloadsDir(): File = File(System.getProperty("user.home"), "Downloads").also { it.mkdirs() }

    private fun uniqueFile(dir: File, name: String): File {
        val safe = name.replace(Regex("""[\\/:*?"<>|]"""), "_")
        var f = File(dir, safe)
        var n = 1
        val base = safe.substringBeforeLast('.', safe)
        val ext = safe.substringAfterLast('.', "").let { if (it.isEmpty() || it == safe) "" else ".$it" }
        while (f.exists()) f = File(dir, "$base (${n++})$ext")
        return f
    }
}
