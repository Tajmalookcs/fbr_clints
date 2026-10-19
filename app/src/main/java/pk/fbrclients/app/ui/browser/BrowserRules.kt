package pk.fbrclients.app.ui.browser

import org.json.JSONObject
import java.net.URI

/** Which sites the in-app browser may show, and the JavaScript used to fill fields. Plain JVM code (tested). */
object BrowserRules {
    /** Always allowed (and their sub-domains). */
    val BUILT_IN = listOf("fbr.gov.pk", "pitc.com.pk", "fesco.com.pk")

    fun host(url: String): String? =
        runCatching { URI(url.trim()).host?.lowercase()?.removePrefix("www.") }.getOrNull()

    /** [extraLinks]: the bill / certificate links set in Settings. Only https pages are shown in the app. */
    fun isAllowed(url: String, extraLinks: Collection<String>): Boolean {
        val u = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        if (u.scheme?.lowercase() != "https") return false
        val h = u.host?.lowercase()?.removePrefix("www.") ?: return false
        val domains = BUILT_IN + extraLinks.mapNotNull(::host)
        return domains.any { d -> h == d || h.endsWith(".$d") }
    }

    fun isIris(url: String) = host(url)?.let { it == "iris.fbr.gov.pk" } == true

    enum class Field { USERNAME, PASSWORD, REFERENCE }

    /**
     * Desktop Chrome on Windows, keeping the phone's real Chrome version
     * (taken from the WebView's own user agent, e.g. "Chrome/129.0.6668.100").
     */
    fun desktopUserAgent(mobileUserAgent: String): String {
        val chrome = Regex("Chrome/[\\d.]+").find(mobileUserAgent)?.value ?: "Chrome/124.0.0.0"
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) $chrome Safari/537.36"
    }

    /** Makes pages that ask for a phone-width layout use a desktop width, like Chrome's "Desktop site". */
    const val DESKTOP_VIEWPORT_JS = """
        (function(){ var m=document.querySelector('meta[name="viewport"]');
          if(!m){ m=document.createElement('meta'); m.name='viewport'; (document.head||document.documentElement).appendChild(m); }
          if(m.getAttribute('content')!=='width=1280'){ m.setAttribute('content','width=1280'); }
        })();
    """

    /** Small helpers shared by the scripts below: visible inputs, and setting a value the page will notice. */
    private const val HELPERS = """
        function vis(e){ var r=e.getBoundingClientRect(); return !e.disabled && !e.readOnly && r.width>0 && r.height>0; }
        function inputs(){ return Array.prototype.slice.call(document.querySelectorAll('input')).filter(function(e){
            var t=(e.type||'text').toLowerCase(); return ['text','email','tel','number','password','search',''].indexOf(t)>=0 && vis(e); }); }
        function put(e,v){
            var proto=Object.getPrototypeOf(e); var d=Object.getOwnPropertyDescriptor(proto,'value');
            if(d&&d.set){ d.set.call(e,v); } else { e.value=v; }
            e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true}));
        }
        function hint(e){ return ((e.name||'')+' '+(e.id||'')+' '+(e.placeholder||'')+' '+(e.getAttribute('aria-label')||'')).toLowerCase(); }
    """

    /** Fills the box the user tapped. Returns "ok", "none" (nothing selected) or "password-only". */
    fun fillFocused(value: String, isPassword: Boolean): String = """
        (function(){ $HELPERS
          var e=document.activeElement;
          if(!e || e.tagName!=='INPUT' && e.tagName!=='TEXTAREA') return 'none';
          if(!${isPassword} && (e.type||'').toLowerCase()==='password') return 'password-only';
          put(e, ${JSONObject.quote(value)}); return 'ok';
        })();
    """.trimIndent()

    /**
     * Automatic fill when a page loads. Only fills empty boxes, never presses any button.
     * IRIS: the password box, and the text box just before it for the username.
     * Bill / certificate pages: a box that looks like a reference number, or the only text box.
     */
    fun autoFill(url: String, username: String?, password: String?, reference: String?): String? {
        if (isIris(url)) {
            if (username.isNullOrEmpty() && password.isNullOrEmpty()) return null
            return """
                (function(){ $HELPERS
                  var all=inputs(); var pw=all.filter(function(e){return (e.type||'').toLowerCase()==='password';})[0];
                  if(!pw) return 'no-login';
                  var i=all.indexOf(pw); var user=null;
                  for(var j=i-1;j>=0;j--){ if((all[j].type||'').toLowerCase()!=='password'){ user=all[j]; break; } }
                  var n=0;
                  ${if (!username.isNullOrEmpty()) "if(user && !user.value){ put(user, ${JSONObject.quote(username)}); n++; }" else ""}
                  ${if (!password.isNullOrEmpty()) "if(!pw.value){ put(pw, ${JSONObject.quote(password)}); n++; }" else ""}
                  return 'filled:'+n;
                })();
            """.trimIndent()
        }
        if (reference.isNullOrEmpty()) return null
        return """
            (function(){ $HELPERS
              var all=inputs().filter(function(e){return (e.type||'').toLowerCase()!=='password';});
              var box=all.filter(function(e){ return /ref|consumer|account|cust|bill/.test(hint(e)); })[0] || (all.length===1?all[0]:null);
              if(!box) return 'no-box';
              if(!box.value){ put(box, ${JSONObject.quote(reference)}); return 'filled:1'; }
              return 'filled:0';
            })();
        """.trimIndent()
    }
}
