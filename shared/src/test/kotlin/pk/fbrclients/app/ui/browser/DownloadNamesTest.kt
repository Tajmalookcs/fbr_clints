package pk.fbrclients.app.ui.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadNamesTest {
    @Test fun names() {
        assertEquals("Test Person Return.pdf", DownloadNames.suggest("Test Person", "https://iris.fbr.gov.pk/#/return", "blob-123", "application/pdf"))
        assertEquals("Test Person FESCO bill.pdf", DownloadNames.suggest("Test Person", "https://bill.pitc.com.pk/fescobill/general?refno=1", "bill.pdf", null))
        assertEquals("Test Person FESCO tax certificate.pdf",
            DownloadNames.suggest("Test Person", "https://mis.fesco.com.pk/fescoweb/old.fesco.com.pk/NewTaxStatement/TaxStatement.aspx", null, null))
        assertEquals("A_B Traders Return.pdf", DownloadNames.suggest("A/B Traders", "https://iris.fbr.gov.pk", null, null))
        assertEquals("Return.xlsx", DownloadNames.suggest(null, "https://iris.fbr.gov.pk", "data.xlsx", null))
    }

    @Test fun typedNames() {
        assertEquals("My return.pdf", DownloadNames.finalName("My return", "pdf"))
        assertEquals("My return.PDF", DownloadNames.finalName("My return.PDF", "pdf"))
        assertEquals("a_b.pdf", DownloadNames.finalName("a:b", "pdf"))
        assertEquals("Download.pdf", DownloadNames.finalName("  ", "pdf"))
    }
}
