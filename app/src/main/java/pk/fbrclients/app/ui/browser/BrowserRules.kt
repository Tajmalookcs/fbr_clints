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


    /**
     * Unlocks pinch-zoom and moving on every page (many sites set "user-scalable=no").
     * In desktop mode the page is also laid out 1280 px wide, like a computer screen.
     * Re-applied if the page (e.g. IRIS, a single-page app) changes the setting again.
     */
    fun viewportJs(desktop: Boolean): String = """
        (function(){
          var DESKTOP=${desktop};
          function fix(){
            var m=document.querySelector('meta[name="viewport"]');
            if(!m){ m=document.createElement('meta'); m.name='viewport'; (document.head||document.documentElement).appendChild(m); }
            var c=m.getAttribute('content')||'';
            var want;
            if(DESKTOP){ want='width=1280, minimum-scale=0.1, maximum-scale=10, user-scalable=yes'; }
            else {
              var parts=c.split(',').map(function(x){return x.trim();}).filter(function(x){
                var k=x.split('=')[0].trim().toLowerCase(); return x && k!=='user-scalable' && k!=='maximum-scale' && k!=='minimum-scale'; });
              parts.push('minimum-scale=0.1','maximum-scale=10','user-scalable=yes'); want=parts.join(', ');
            }
            if(c!==want){ m.setAttribute('content',want); }
            document.documentElement.style.touchAction='auto';
            if(document.body){ document.body.style.touchAction='auto'; }
          }
          fix();
          if(!window.__fbrViewport){
            window.__fbrViewport=true;
            try{ new MutationObserver(function(){ fix(); }).observe(document.head||document.documentElement,{childList:true,subtree:true,attributes:true,attributeFilter:['content']}); }catch(e){}
          }
        })();
    """.trimIndent()

    /** CSS selectors of popup boxes used by common web frameworks (Angular Material, Bootstrap, PrimeNG, …). */
    private const val POPUPS =
        "[role=\"dialog\"], [role=\"alertdialog\"], dialog[open], .modal-dialog, .cdk-overlay-pane, " +
            ".mat-dialog-container, .mat-mdc-dialog-container, .p-dialog, .ui-dialog, .k-window, .e-dialog, " +
            ".swal2-popup, .dx-overlay-content, .ant-modal"

    /**
     * Keeps popups no wider than the screen. Only the width is limited: limiting the
     * height / adding scrolling squashed IRIS's popups to zero height (2026-10-20).
     */
    val FIT_POPUPS_JS: String = """
        (function(){
          if(document.getElementById('fbr-fit-popups')) return;
          var s=document.createElement('style'); s.id='fbr-fit-popups';
          s.textContent='.cdk-overlay-pane,.modal-dialog,.p-dialog,.ui-dialog,.k-window,.e-dialog,.swal2-popup,.ant-modal{max-width:96vw !important;}'
            + '.mat-mdc-dialog-container,.mat-dialog-container{max-width:100% !important;}';
          (document.head||document.documentElement).appendChild(s);
        })();
    """.trimIndent()

    /**
     * "Page check": describes every popup and backdrop on the page (size, position,
     * whether hidden), plus the screen size, so a missing popup can be diagnosed.
     */
    val PAGE_CHECK_JS: String = """
        (function(){
          function d(e){ var r=e.getBoundingClientRect(), c=getComputedStyle(e);
            var cls=(typeof e.className==='string'?e.className:'').trim().split(/\s+/).slice(0,3).join('.');
            return e.tagName.toLowerCase()+(cls?'.'+cls:'')+' x='+Math.round(r.left)+' y='+Math.round(r.top)+' w='+Math.round(r.width)+' h='+Math.round(r.height)
              +' display='+c.display+' vis='+c.visibility+' opacity='+c.opacity+' pos='+c.position+' z='+c.zIndex+(c.transform!=='none'?' transform='+c.transform:''); }
          var out=['screen '+innerWidth+'x'+innerHeight+' zoom='+(window.visualViewport?visualViewport.scale.toFixed(2):'?')+' '+(navigator.userAgent.match(/Chrome\/[\d.]+/)||[''])[0]];
          var pops=document.querySelectorAll('$POPUPS');
          out.push('popups: '+pops.length);
          for(var i=0;i<pops.length&&i<8;i++) out.push('  '+d(pops[i]));
          var bd=document.querySelectorAll('.cdk-overlay-backdrop, .modal-backdrop, .p-component-overlay, .ui-widget-overlay');
          out.push('backdrops: '+bd.length);
          for(var j=0;j<bd.length&&j<4;j++) out.push('  '+d(bd[j]));
          // What is inside the first popups: parts, sizes and how much text they hold.
          for(var p=0;p<pops.length&&p<2;p++){
            out.push('inside popup '+(p+1)+':');
            var n=0;
            (function walk(e,depth){
              for(var c=e.firstElementChild;c&&n<30;c=c.nextElementSibling){
                n++; var cs=getComputedStyle(c);
                out.push('  '+'  '.repeat(depth)+d(c)+' minH='+cs.minHeight+' maxH='+cs.maxHeight+' hgt='+cs.height+' text='+((c.innerText||'').trim().length));
                if(depth<5) walk(c,depth+1);
              }
            })(pops[p],0);
          }
          var ifr=document.querySelectorAll('iframe'); out.push('frames: '+ifr.length);
          for(var k=0;k<ifr.length&&k<4;k++) out.push('  '+d(ifr[k])+' src='+(ifr[k].src||'').slice(0,80));
          return out.join('\n');
        })();
    """.trimIndent()

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
