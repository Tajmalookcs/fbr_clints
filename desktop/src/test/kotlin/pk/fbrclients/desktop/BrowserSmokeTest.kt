package pk.fbrclients.desktop

import org.junit.Assume.assumeTrue
import org.junit.Test
import pk.fbrclients.app.data.Client
import pk.fbrclients.desktop.browser.AppBrowser
import java.awt.Frame
import java.awt.Rectangle
import java.awt.Robot
import java.io.File
import javax.imageio.ImageIO

/**
 * Opens the real in-app browser on IRIS and on a bill page (made-up login and reference),
 * and saves pictures of the windows. Runs only on the Windows review build (BROWSER_SMOKE_DIR set).
 */
class BrowserSmokeTest {
    private val out = System.getenv("BROWSER_SMOKE_DIR")?.let(::File)

    private fun waitForWindow(titlePart: String, seconds: Int): Frame? {
        repeat(seconds * 2) {
            Frame.getFrames().firstOrNull { it.isVisible && it.title.contains(titlePart) }?.let { return it }
            Thread.sleep(500)
        }
        return null
    }

    private fun shot(f: Frame, name: String) {
        val b = f.bounds
        val img = Robot().createScreenCapture(Rectangle(b.x, b.y, b.width, b.height))
        ImageIO.write(img, "png", File(out, "$name.png"))
    }

    @Test fun irisAndBillPage() {
        assumeTrue(out != null)
        out!!.mkdirs()
        val log = File(out, "log.txt")
        fun note(s: String) = log.appendText(s + "\n")
        val client = Client(cnic = "12345-1234567-1", name = "Test Person", irisPassword = "Test@123", ntn = "1234567")

        AppBrowser.open(AppBrowser.Request("https://iris.fbr.gov.pk", client, null, emptyList(), "IRIS test"), ::note)
        val iris = waitForWindow("IRIS test", 240)
        note("IRIS window: " + (iris?.title ?: "none"))
        if (iris != null) {
            Thread.sleep(25_000)
            iris.toFront()
            Thread.sleep(1_000)
            shot(iris, "browser-1-iris")
            note("IRIS title after load: " + iris.title)
        }

        AppBrowser.open(AppBrowser.Request(pk.fbrclients.app.ui.browser.BrowserRules.IRIS_REGISTRATION_URL, client, null, emptyList(), "Registration test"), ::note)
        val bill = waitForWindow("Registration test", 60)
        if (bill != null) {
            Thread.sleep(20_000)
            bill.toFront()
            Thread.sleep(1_000)
            shot(bill, "browser-2-registration")
        }
        iris?.dispose()
        bill?.dispose()
        Thread.sleep(2_000)
        AppBrowser.shutdown()
    }
}
