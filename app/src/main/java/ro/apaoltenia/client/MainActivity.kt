package ro.apaoltenia.client

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.launch
import ro.apaoltenia.client.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var store: CredentialStore
    private lateinit var biometric: BiometricAuthenticator

    /** Setat pe true dupa deblocarea reusita, ca sa injectam auto-login o singura data. */
    private var autoLoginArmed = false

    /**
     * True cand continutul paginii e derulat sus de tot. Raportat din JS,
     * pentru ca aplicatia OpenUI5 a portalului deruleaza intr-un container
     * intern — scroll-ul nativ al WebView-ului ramane mereu 0, iar fara acest
     * semnal pull-to-refresh s-ar declansa de oriunde din pagina.
     */
    @Volatile
    private var pageAtTop = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        store = CredentialStore(this)
        biometric = BiometricAuthenticator(this)

        configureWebView()

        binding.swipeRefresh.setOnRefreshListener { binding.webView.reload() }
        // Pull-to-refresh doar cand pagina e sus de tot: daca WebView-ul sau
        // containerul intern al portalului mai poate derula in sus, gestul
        // ramane un scroll normal.
        binding.swipeRefresh.setOnChildScrollUpCallback { _, _ ->
            binding.webView.canScrollVertically(-1) || !pageAtTop
        }

        onBackPressedDispatcher.addCallback(this) {
            if (binding.webView.canGoBack()) binding.webView.goBack() else finish()
        }

        binding.forgetButton.setOnClickListener { confirmForgetCredentials() }

        if (store.hasCredentials && biometric.canAuthenticate()) {
            showLockedState()
            runBiometric()
        } else {
            openLoginPage()
        }

        checkForUpdate()
    }

    /**
     * Verificare automata de versiune la fiecare pornire. Daca exista o
     * versiune noua, dialogul descarca si instaleaza APK-ul direct din
     * aplicatie. Esecurile (offline, rate-limit) sunt silentioase.
     */
    private fun checkForUpdate() {
        lifecycleScope.launch {
            val update = UpdateChecker.check(BuildConfig.VERSION_NAME) ?: return@launch
            if (!isFinishing && !isDestroyed) {
                UpdateManager.promptAndInstall(this@MainActivity, update)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_reload -> {
                binding.webView.reload(); true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java)); true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun runBiometric() {
        biometric.authenticate(
            onSuccess = {
                autoLoginArmed = true
                openLoginPage()
            },
            onError = { message -> showLockedState(message) },
            onFailed = { showLockedState(getString(R.string.unlock_failed)) }
        )
    }

    private fun openLoginPage() {
        showWebView()
        binding.webView.loadUrl(LOGIN_URL)
    }

    private fun showWebView() {
        binding.lockOverlay.visibility = View.GONE
        binding.swipeRefresh.visibility = View.VISIBLE
    }

    private fun showLockedState(status: String = getString(R.string.unlock_hint)) {
        binding.swipeRefresh.visibility = View.GONE
        binding.lockOverlay.visibility = View.VISIBLE
        binding.unlockStatus.text = status
        binding.actionButton.text = getString(R.string.unlock_button)
        binding.actionButton.setOnClickListener { runBiometric() }
        binding.forgetButton.visibility = View.VISIBLE
    }

    private fun showErrorState() {
        binding.swipeRefresh.visibility = View.GONE
        binding.lockOverlay.visibility = View.VISIBLE
        binding.unlockStatus.text = getString(R.string.load_error)
        binding.actionButton.text = getString(R.string.retry_button)
        binding.actionButton.setOnClickListener {
            showWebView()
            binding.webView.reload()
        }
        binding.forgetButton.visibility = View.GONE
    }

    private fun confirmForgetCredentials() {
        AlertDialog.Builder(this)
            .setTitle(R.string.forget_title)
            .setMessage(R.string.forget_message)
            .setPositiveButton(R.string.forget_yes) { _, _ ->
                store.clear()
                autoLoginArmed = false
                openLoginPage()
            }
            .setNegativeButton(R.string.save_no, null)
            .show()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val web = binding.webView
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            // Blindaj: fara acces la fisierele locale sau la continut din alte origini.
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            // Fara continut mixt HTTP intr-o pagina HTTPS.
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        // Google Safe Browsing pentru pagini periculoase (daca dispozitivul il suporta).
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(web.settings, true)
        }

        applyWebViewTheme()

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            // Portalul e first-party; nu acceptam cookie-uri third-party.
            setAcceptThirdPartyCookies(web, false)
        }

        web.addJavascriptInterface(
            WebAppInterface(
                onCredentialsCaptured = { email, password ->
                    onCredentialsCaptured(email, password)
                },
                onScrollStatusChanged = { atTop -> pageAtTop = atTop }
            ),
            "AndroidBridge"
        )

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView, request: WebResourceRequest
            ): Boolean {
                val url = request.url
                val host = url.host ?: return false
                if (host == PORTAL_DOMAIN || host.endsWith(".$PORTAL_DOMAIN")) {
                    return false
                }
                // Linkurile externe se deschid in browserul telefonului, dar numai
                // pentru scheme sigure (http/https). Refuzam intent:/file:/javascript:
                // etc. ca sa nu poata fi pornite activitati arbitrare dintr-o pagina.
                val scheme = url.scheme?.lowercase()
                if (scheme == "http" || scheme == "https") {
                    runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, url).apply {
                            addCategory(Intent.CATEGORY_BROWSABLE)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                    }
                }
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                binding.progressBar.visibility = View.VISIBLE
                // Document nou -> pornim din varf pana raporteaza JS-ul altceva.
                pageAtTop = true
            }

            override fun onPageCommitVisible(view: WebView, url: String) {
                super.onPageCommitVisible(view, url)
                // Injectam stilul cat mai devreme, ca pagina sa nu "clipeasca"
                // cu aspectul vechi inainte de restilizare.
                injectCustomStyle(url)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false

                // Reinjectam la final: aplicatia OpenUI5 isi incarca CSS-ul
                // asincron si vrem ca foaia noastra sa ramana in documentul final.
                injectCustomStyle(url)
                injectScrollHook(url)
                injectChartFix(url)

                if (!url.contains("login.jsp", ignoreCase = true)) return

                if (autoLoginArmed && store.hasCredentials) {
                    val email = store.email
                    val password = store.password
                    if (email != null && password != null) {
                        injectAutoFill(email, password)
                    }
                    autoLoginArmed = false
                }
                // Ascultam mereu trimiterea formularului: la prima logare
                // propunem salvarea, iar daca parola s-a schimbat intre timp
                // propunem actualizarea datelor salvate.
                injectCaptureHook()
            }

            override fun onReceivedError(
                view: WebView, request: WebResourceRequest, error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                if (request.isForMainFrame) showErrorState()
            }
        }
    }

    /** CSS-ul custom pentru portal, citit o singura data din assets. */
    private val portalCss: String by lazy {
        assets.open("portal_style.css").bufferedReader().use { it.readText() }
    }

    /**
     * Injecteaza foaia de stil custom peste paginile portalului si comuta
     * clasa `apao-dark` dupa tema aplicatiei (temele light/dark ale portalului
     * sunt definite prin CSS variables in portal_style.css). CSS-ul este
     * transportat Base64 ca sa nu depinda de escapari; JS-ul inlocuieste
     * elementul <style> daca exista deja (idempotent la reincarcari).
     */
    private fun injectCustomStyle(url: String) {
        if (!url.contains(PORTAL_DOMAIN, ignoreCase = true)) return
        val b64 = android.util.Base64.encodeToString(
            portalCss.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP
        )
        val dark = isDarkTheme()
        val js = """
            (function() {
              document.documentElement.classList.toggle('apao-dark', $dark);
              var id = 'apao-custom-style';
              var css = atob('$b64');
              var el = document.getElementById(id);
              if (el) { el.textContent = css; return; }
              el = document.createElement('style');
              el.id = id;
              el.textContent = css;
              (document.head || document.documentElement).appendChild(el);
            })();
        """.trimIndent()
        binding.webView.evaluateJavascript(js, null)
    }

    /**
     * Injecteaza un ascultator de scroll (faza de captura, deci prinde si
     * derularea containerelor interne OpenUI5) care raporteaza in Kotlin daca
     * pagina e sus de tot. Idempotent: se instaleaza o singura data pe document.
     */
    private fun injectScrollHook(url: String) {
        if (!url.contains(PORTAL_DOMAIN, ignoreCase = true)) return
        val js = """
            (function() {
              if (window.__apaoScrollHook) return;
              window.__apaoScrollHook = true;
              var atTop = true;
              function report(v) {
                if (v === atTop) return;
                atTop = v;
                if (window.AndroidBridge && AndroidBridge.onScrollStatus) {
                  AndroidBridge.onScrollStatus(v);
                }
              }
              document.addEventListener('scroll', function(e) {
                var winTop = (window.scrollY || document.documentElement.scrollTop || 0) <= 0;
                var elTop = true;
                var t = e.target;
                if (t && t !== document && t !== document.documentElement &&
                    t !== document.body && typeof t.scrollTop === 'number') {
                  elTop = t.scrollTop <= 0;
                }
                report(winTop && elTop);
              }, { capture: true, passive: true });
            })();
        """.trimIndent()
        binding.webView.evaluateJavascript(js, null)
    }

    /**
     * Graficul de consum al portalului (Chart.js) se randeaza cu inaltime foarte
     * mica pe mobil (maintainAspectRatio implicit), asa ca cele 12 luni si 4 ani
     * se ingramadesc ilizibil. Injectam un hook persistent care, la aparitia
     * oricarui grafic (inclusiv dupa navigarea client-side din SPA-ul OpenUI5),
     * ii da o inaltime utila, dezactiveaza raportul de aspect fix si roteste
     * etichetele lunilor. Hook-ul se instaleaza o singura data si urmareste DOM-ul
     * printr-un MutationObserver + un interval de siguranta.
     */
    private fun injectChartFix(url: String) {
        if (!url.contains(PORTAL_DOMAIN, ignoreCase = true)) return
        val js = """
            (function() {
              if (window.__apaoChartHook) return;
              window.__apaoChartHook = true;
              function fix() {
                if (!window.Chart || !window.Chart.instances) return;
                Object.keys(window.Chart.instances).forEach(function(k) {
                  var inst = window.Chart.instances[k];
                  if (!inst) return;
                  var canvas = inst.canvas || (inst.chart && inst.chart.canvas);
                  if (!canvas) return;
                  var container = canvas.parentElement;
                  if (container && container.style.height !== '320px') {
                    container.style.height = '320px';
                    container.style.width = '100%';
                  }
                  if (!inst.__apaoFixed) {
                    inst.__apaoFixed = true;
                    inst.options.maintainAspectRatio = false;
                    try {
                      var xa = inst.options.scales.xAxes[0];
                      xa.ticks = xa.ticks || {};
                      xa.ticks.maxRotation = 60; xa.ticks.minRotation = 45;
                      xa.ticks.autoSkip = false; xa.ticks.fontSize = 10;
                    } catch (e) {}
                    try {
                      if (inst.options.legend) {
                        inst.options.legend.position = 'top';
                        if (inst.options.legend.labels) inst.options.legend.labels.boxWidth = 12;
                      }
                    } catch (e) {}
                  }
                  var r = canvas.getBoundingClientRect();
                  if (r.height < 220) { try { inst.resize(); inst.update(); } catch (e) {} }
                });
              }
              try {
                new MutationObserver(fix).observe(document.body, { childList: true, subtree: true });
              } catch (e) {}
              setInterval(fix, 1200);
              fix();
            })();
        """.trimIndent()
        binding.webView.evaluateJavascript(js, null)
    }

    private fun isDarkTheme(): Boolean {
        val nightMode = resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * Fundalul WebView-ului pe culoarea temei, ca sa nu apara un "blit" alb
     * inainte de injectarea stilului. Tema intunecata a paginii vine din
     * CSS-ul propriu (clasa apao-dark), nu din algorithmic darkening —
     * astfel culorile raman exact cele alese, in ambele teme.
     */
    private fun applyWebViewTheme() {
        val background = if (isDarkTheme()) 0xFF0D1720.toInt() else 0xFFF4F9FD.toInt()
        binding.webView.setBackgroundColor(background)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(binding.webView.settings, false)
        }
    }

    /**
     * Injecteaza JS care completeaza formularul de login si il trimite.
     * Selecteaza formularul de autentificare = cel cu EXACT un camp de tip
     * password (inregistrarea are doua, recuperarea are zero).
     */
    private fun injectAutoFill(email: String, password: String) {
        val safeEmail = jsEscape(email)
        val safePassword = jsEscape(password)
        val js = """
            (function() {
              function loginForm() {
                var pwds = Array.prototype.slice.call(
                    document.querySelectorAll('input[type=password]'));
                for (var i = 0; i < pwds.length; i++) {
                  var f = pwds[i].form;
                  if (!f) continue;
                  var inForm = f.querySelectorAll('input[type=password]').length;
                  if (inForm === 1) return f;
                }
                return pwds.length ? pwds[0].form : null;
              }
              var form = loginForm();
              if (!form) return;
              var pwd = form.querySelector('input[type=password]');
              var user = form.querySelector('input[type=email]') ||
                         form.querySelector('input[type=text]') ||
                         form.querySelector('input:not([type=password]):not([type=hidden]):not([type=checkbox])');
              if (user) { user.value = '$safeEmail';
                          user.dispatchEvent(new Event('input',{bubbles:true})); }
              if (pwd)  { pwd.value = '$safePassword';
                          pwd.dispatchEvent(new Event('input',{bubbles:true})); }
            })();
        """.trimIndent()
        // NOTA: nu apasam automat "Login". Portalul cere completarea unui
        // Cloudflare Turnstile (anti-bot) inainte de trimitere, asa ca lasam
        // utilizatorul sa bifeze verificarea si sa apese Login. Campurile sunt
        // deja completate, deci ramane un singur gest.
        binding.webView.evaluateJavascript(js, null)
    }

    /**
     * Injecteaza JS care, la trimiterea formularului de login, citeste valorile
     * introduse si le trimite in Kotlin prin AndroidBridge.
     *
     * Portalul NU trimite formularul printr-un submit normal: `onsubmit` face
     * `preventDefault()`, iar butonul de Login apeleaza `form.submit()` din JS.
     * Un `form.submit()` programatic NU declanseaza evenimentul 'submit', deci
     * un simplu ascultator de 'submit' nu ar prinde niciodata datele (motivul
     * pentru care salvarea si, implicit, deblocarea biometrica nu functionau).
     * De aceea capturam pe trei cai: evenimentul 'submit' (Enter), click-ul pe
     * buton (faza de captura) si ambalarea metodei `form.submit`. Prima captura
     * reusita opreste restul, ca sa nu apara dialogul de salvare de doua ori.
     */
    private fun injectCaptureHook() {
        val js = """
            (function() {
              function loginForm() {
                var pwds = Array.prototype.slice.call(
                    document.querySelectorAll('input[type=password]'));
                for (var i = 0; i < pwds.length; i++) {
                  var f = pwds[i].form;
                  if (!f) continue;
                  if (f.querySelectorAll('input[type=password]').length === 1) return f;
                }
                return null;
              }
              var form = loginForm();
              if (!form || form.__apaoHooked) return;
              form.__apaoHooked = true;
              var captured = false;
              function grab() {
                if (captured) return;
                var pwd = form.querySelector('input[type=password]');
                var user = form.querySelector('input[type=email]') ||
                           form.querySelector('input[type=text]') ||
                           form.querySelector('input:not([type=password]):not([type=hidden]):not([type=checkbox])');
                if (user && pwd && user.value && pwd.value) {
                  captured = true;
                  AndroidBridge.captureCredentials(user.value, pwd.value);
                }
              }
              form.addEventListener('submit', grab, true);
              var btn = form.querySelector('input[type=submit], button[type=submit]');
              if (btn) btn.addEventListener('click', grab, true);
              var nativeSubmit = form.submit;
              if (typeof nativeSubmit === 'function') {
                form.submit = function() {
                  grab();
                  return nativeSubmit.apply(this, arguments);
                };
              }
            })();
        """.trimIndent()
        binding.webView.evaluateJavascript(js, null)
    }

    private fun onCredentialsCaptured(email: String, password: String) {
        runOnUiThread {
            if (!biometric.canAuthenticate()) return@runOnUiThread

            val isUpdate = store.hasCredentials
            if (isUpdate && store.email == email && store.password == password) {
                return@runOnUiThread
            }

            AlertDialog.Builder(this)
                .setTitle(if (isUpdate) R.string.update_title else R.string.save_title)
                .setMessage(if (isUpdate) R.string.update_message else R.string.save_message)
                .setPositiveButton(R.string.save_yes) { _, _ ->
                    store.save(email, password)
                }
                .setNegativeButton(R.string.save_no, null)
                .show()
        }
    }

    private fun jsEscape(value: String): String =
        value.replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "")
            .replace("\r", "")
            .replace("\u2028", "")
            .replace("\u2029", "")

    companion object {
        private const val PORTAL_DOMAIN = "apaoltenia.ro"
        private const val LOGIN_URL =
            "https://clienti.apaoltenia.ro/self_utilities/login.jsp"
    }
}
