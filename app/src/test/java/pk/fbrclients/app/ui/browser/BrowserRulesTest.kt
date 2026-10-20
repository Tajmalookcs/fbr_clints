package pk.fbrclients.app.ui.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserRulesTest {
    private val links = listOf("https://bill.pitc.com.pk/fescobill", "https://www.ke.com.pk/bill", "")

    @Test fun allowedSites() {
        assertTrue(BrowserRules.isAllowed("https://iris.fbr.gov.pk/login", links))
        assertTrue(BrowserRules.isAllowed("https://e.fbr.gov.pk/x", links))
        assertTrue(BrowserRules.isAllowed("https://bill.pitc.com.pk/pescobill", links))
        assertTrue(BrowserRules.isAllowed("https://mis.fesco.com.pk/fescoweb/old.fesco.com.pk/NewTaxStatement/TaxStatement.aspx", links))
        assertTrue(BrowserRules.isAllowed("https://ke.com.pk/anything", links))
    }

    @Test fun otherSitesOpenOutside() {
        assertFalse(BrowserRules.isAllowed("https://evil.com/iris.fbr.gov.pk", links))
        assertFalse(BrowserRules.isAllowed("https://fbr.gov.pk.evil.com/", links))
        assertFalse(BrowserRules.isAllowed("http://iris.fbr.gov.pk/", links)) // not https
        assertFalse(BrowserRules.isAllowed("javascript:alert(1)", links))
        assertFalse(BrowserRules.isAllowed("not a url", links))
    }

    @Test fun desktopUserAgentKeepsChromeVersion() {
        val mobile = "Mozilla/5.0 (Linux; Android 14; SM-A546E Build/UP1A; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/129.0.6668.100 Mobile Safari/537.36"
        val d = BrowserRules.desktopUserAgent(mobile)
        assertTrue(d.contains("Windows NT 10.0"))
        assertTrue(d.contains("Chrome/129.0.6668.100"))
        assertFalse(d.contains("Android"))
        assertFalse(d.contains("Mobile"))
        assertFalse(d.contains("wv"))
    }

    @Test fun zoomIsUnlockedInBothModes() {
        val d = BrowserRules.viewportJs(desktop = true)
        assertTrue(d.contains("var DESKTOP=true"))
        assertTrue(d.contains("width=1280"))
        assertTrue(d.contains("user-scalable=yes"))
        assertTrue(BrowserRules.viewportJs(desktop = false).contains("var DESKTOP=false"))
    }

    @Test fun popupScripts() {
        assertTrue(BrowserRules.FIT_POPUPS_JS.contains(".cdk-overlay-pane"))
        assertTrue(BrowserRules.FIT_POPUPS_JS.contains("max-width:96vw"))
        assertFalse(BrowserRules.FIT_POPUPS_JS.contains("max-height"))
        assertFalse(BrowserRules.FIT_POPUPS_JS.contains("overflow"))
        assertTrue(BrowserRules.PAGE_CHECK_JS.contains("popups: "))
        assertFalse(BrowserRules.PAGE_CHECK_JS.contains("\$"))
    }

    @Test fun valuesAreQuotedSafely() {
        val js = BrowserRules.fillFocused("a'b\"c</script>\\", isPassword = true)
        assertTrue(js.contains("\"a'b\\\"c<\\/script>\\\\\""))
    }

    @Test fun autoFillOnlyWhereUseful() {
        assertNull(BrowserRules.autoFill("https://iris.fbr.gov.pk/", null, null, "123"))
        assertNull(BrowserRules.autoFill("https://bill.pitc.com.pk/fescobill", "u", "p", null))
        assertTrue(BrowserRules.autoFill("https://iris.fbr.gov.pk/", "12345-1234567-1", "pw", null)!!.contains("12345-1234567-1"))
        assertTrue(BrowserRules.autoFill("https://bill.pitc.com.pk/fescobill", null, null, "12345678901234")!!.contains("12345678901234"))
    }
}
