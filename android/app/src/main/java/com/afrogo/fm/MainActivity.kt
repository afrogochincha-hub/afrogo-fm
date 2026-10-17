package com.afrogo.fm

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.ProgressBar
import android.widget.TextView
import android.util.LruCache
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

class MainActivity : Activity() {
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var pendingMediaItem: MediaItem? = null
    private lateinit var authStatus: TextView
    private lateinit var playbackStatus: TextView
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var nameInput: EditText
    private lateinit var catalogContainer: LinearLayout
    private lateinit var searchBar: LinearLayout
    private lateinit var catalogSearchInput: EditText
    private lateinit var searchClearButton: Button
    private lateinit var catalogSummary: TextView
    private var catalogTracks: List<AfrogoTrack> = emptyList()
    private var favoriteTrackIds: MutableSet<String> = linkedSetOf()
    private var userPlaylists: MutableList<JSONObject> = mutableListOf()
    private var catalogMode: String = "all"
    private var selectedArtist: String = ""
    private var activePlaylistId: String = ""
    private var catalogQuery: String = ""
    private val searchHandler = Handler(Looper.getMainLooper())
    private var scheduledCatalogFilter: Runnable? = null
    private val coverExecutor = java.util.concurrent.Executors.newFixedThreadPool(4)
    private val coverCache = object : LruCache<String, Bitmap>(12 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }
    private lateinit var radioButton: Button
    private lateinit var signInButton: Button
    private lateinit var registerButton: Button
    private lateinit var logoutButton: Button
    private lateinit var appRoot: LinearLayout
    private lateinit var loginRoot: ScrollView
    private lateinit var appShell: LinearLayout
    private lateinit var screenHost: FrameLayout
    private lateinit var miniPlayer: LinearLayout
    private lateinit var miniPlayerCover: ImageView
    private lateinit var miniPlayerTitle: TextView
    private lateinit var miniPlayerArtist: TextView
    private lateinit var miniPlayerPlayButton: Button
    private var landscapePlayerCover: ImageView? = null
    private var landscapePlayerTitle: TextView? = null
    private var landscapePlayerArtist: TextView? = null
    private var landscapePlayerToggle: Button? = null
    // References for the full-screen landscape player; these must be refreshed on Media3 events.
    private var playerScreenCover: ImageView? = null
    private var playerScreenTitle: TextView? = null
    private var playerScreenArtist: TextView? = null
    private var playerScreenToggle: Button? = null
    private var playerScreenProgress: SeekBar? = null
    private var playerScreenElapsed: TextView? = null
    private var playerScreenDuration: TextView? = null
    private var isUserSeekingPlayerProgress: Boolean = false
    private val playbackProgressHandler = Handler(Looper.getMainLooper())
    private val playbackProgressTicker = object : Runnable {
        override fun run() {
            updatePlayerProgressUi()
            playbackProgressHandler.postDelayed(this, 500L)
        }
    }
    private val navLabels = linkedMapOf<String, TextView>()
    private var activeTab: String = "home"
    private var isRegistrationMode: Boolean = false

    private val bgColor = Color.rgb(18, 18, 18)
    private val surfaceColor = Color.rgb(36, 32, 40)
    private val accentColor = Color.rgb(120, 2, 210)
    private val mutedColor = Color.rgb(170, 166, 178)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playbackProgressHandler.post(playbackProgressTicker)
        window.statusBarColor = bgColor
        window.navigationBarColor = bgColor
        window.decorView.systemUiVisibility = 0

        appRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
        }
        loginRoot = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(bgColor)
        }
        buildLoginScreen()

        appShell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
            visibility = View.GONE
        }
        screenHost = FrameLayout(this).apply { setBackgroundColor(bgColor) }
        appShell.addView(screenHost, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        buildMiniPlayer()
        appShell.addView(miniPlayer, fullWidth())
        buildBottomNavigation()

        appRoot.addView(loginRoot, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        appRoot.addView(appShell, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        // Persistent search control. It is reattached to the Search screen when needed.
        searchBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(4), dp(10), dp(4))
            background = roundedSurface(surfaceColor, dp(18), Color.rgb(67, 61, 72))
            visibility = View.GONE
        }
        searchBar.addView(TextView(this).apply {
            text = "⌕"; textSize = 28f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }, centeredSize(dp(34), dp(42)))
        catalogSearchInput = EditText(this).apply {
            hint = "Canción, artista o álbum"
            textSize = 14f; isSingleLine = true
            setHintTextColor(Color.rgb(160, 156, 168)); setTextColor(Color.WHITE)
            setPadding(dp(7), dp(7), dp(4), dp(7))
            background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        }
        searchBar.addView(catalogSearchInput, LinearLayout.LayoutParams(0, dp(42), 1f))
        searchClearButton = button("×", false) {
            catalogSearchInput.setText("")
            catalogSearchInput.clearFocus()
        }.apply { visibility = View.GONE; minWidth = dp(34) }
        searchBar.addView(searchClearButton, centeredSize(dp(38), dp(38)))

        catalogContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        catalogSummary = TextView(this).apply {
            text = "Inicia sesión para explorar tu biblioteca."
            textSize = 12f; setTextColor(mutedColor); setPadding(dp(2), dp(7), dp(2), dp(9))
        }

        catalogSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                catalogQuery = s?.toString()?.trim().orEmpty()
                searchClearButton.visibility = if (catalogQuery.isBlank()) View.GONE else View.VISIBLE
                scheduledCatalogFilter?.let { searchHandler.removeCallbacks(it) }
                val filterTask = Runnable { renderCatalogTracks() }
                scheduledCatalogFilter = filterTask
                searchHandler.postDelayed(filterTask, 140L)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        setContentView(appRoot)
        connectMediaController()
        val saved = AfrogoApi.savedToken(this)
        if (saved.isNotBlank()) {
            setAuthenticatedUi(true)
            authStatus.text = "Sesión guardada. Verificando acceso…"
            Thread {
                try {
                    val refresh = AfrogoApi.savedRefreshToken(this)
                    val refreshed = if (refresh.isNotBlank()) AfrogoApi.refresh(refresh) else AfrogoApi.upgradeLegacySession(saved)
                    val newAccess = refreshed.optString("token", "")
                    val newRefresh = refreshed.optString("refreshToken", "")
                    if (newAccess.isBlank() || newRefresh.isBlank()) throw IllegalStateException("El servidor no devolvió una sesión renovada válida.")
                    AfrogoApi.saveTokens(this, newAccess, newRefresh)
                    val me = refreshed.optJSONObject("user")
                    runOnUiThread {
                        setAuthenticatedUi(true)
                        authStatus.text = "Sesión activa: ${me?.optString("email", "cuenta Afrogo") ?: "cuenta Afrogo"}"
                        loadCatalog()
                    }
                } catch (e: Exception) {
                    val authRejected = e is AfrogoApiException && (e.statusCode == 401 || e.statusCode == 403)
                    if (authRejected) AfrogoApi.clearToken(this)
                    runOnUiThread {
                        if (authRejected) {
                            setAuthenticatedUi(false)
                            authStatus.text = "La sesión venció o la cuenta ya no tiene acceso. Inicia sesión de nuevo."
                            renderCatalogMessage("Inicia sesión con una cuenta aprobada para acceder a las canciones del servidor.")
                        } else {
                            setAuthenticatedUi(true)
                            authStatus.text = "No se pudo verificar el servidor. Tu sesión sigue guardada; comprueba la conexión y pulsa Actualizar."
                            catalogSummary.text = "Sesión conservada en este dispositivo."
                            renderCatalogMessage("No hay conexión con el catálogo ahora. Tu sesión sigue guardada.")
                        }
                    }
                }
            }.start()
        } else {
            setAuthenticatedUi(false)
            renderCatalogMessage("Inicia sesión con una cuenta aprobada para acceder a las canciones del servidor.")
        }
    }

    private fun buildLoginScreen() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(34), dp(24), dp(30))
            setBackgroundColor(bgColor)
        }
        loginRoot.addView(page, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ))
        val brandIcon = ImageView(this).apply {
            setImageResource(com.afrogo.fm.R.drawable.afrogo_login_logo)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundedSurface(accentColor, dp(24), accentColor)
            clipToOutline = true
            contentDescription = "Logo AfroGo"
        }
        page.addView(brandIcon, centeredSize(dp(90), dp(90)).apply { bottomMargin = dp(20) })
        page.addView(TextView(this).apply {
            text = "AfroGo FM"; textSize = 30f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
        }, fullWidth().apply { bottomMargin = dp(5) })
        page.addView(TextView(this).apply {
            text = "Tu música, tu ritmo."; textSize = 15f; gravity = Gravity.CENTER
            setTextColor(Color.rgb(195, 185, 205))
        }, fullWidth().apply { bottomMargin = dp(28) })

        nameInput = input("Nombre para mostrar", false).apply { visibility = View.GONE }
        emailInput = input("Correo electrónico", false).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        passwordInput = input("Contraseña (mínimo 10 caracteres)", false).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        page.addView(nameInput, fullWidth().apply { bottomMargin = dp(9) })
        page.addView(emailInput, fullWidth().apply { bottomMargin = dp(9) })
        page.addView(passwordInput, fullWidth().apply { bottomMargin = dp(12) })

        signInButton = button("Iniciar sesión", true) {
            if (isRegistrationMode) {
                isRegistrationMode = false
                nameInput.visibility = View.GONE
                signInButton.text = "Iniciar sesión"
                registerButton.text = "Solicitar acceso"
                authStatus.text = "Inicia sesión con tu cuenta AfroGo."
            } else authenticate(register = false)
        }
        registerButton = button("Solicitar acceso", false) {
            if (!isRegistrationMode) {
                isRegistrationMode = true
                nameInput.visibility = View.VISIBLE
                signInButton.text = "Ya tengo cuenta"
                registerButton.text = "Enviar solicitud"
                authStatus.text = "Solicita acceso. El administrador debe aprobar tu cuenta."
            } else authenticate(register = true)
        }
        logoutButton = button("Cerrar sesión", false) { logout() }.apply { visibility = View.GONE }
        page.addView(signInButton, fullWidth().apply { bottomMargin = dp(9); topMargin = dp(2) })
        page.addView(registerButton, fullWidth().apply { bottomMargin = dp(10) })
        page.addView(logoutButton, fullWidth())
        authStatus = TextView(this).apply {
            text = "Inicia sesión para sincronizar tu biblioteca en PC y Android."
            textSize = 12f; setTextColor(mutedColor); gravity = Gravity.CENTER
            setPadding(dp(8), dp(7), dp(8), dp(10))
        }
        page.addView(authStatus, fullWidth().apply { bottomMargin = dp(8) })
        val divider = View(this).apply { setBackgroundColor(Color.rgb(45, 43, 48)) }
        page.addView(divider, fullWidth().apply { height = dp(1); topMargin = dp(8); bottomMargin = dp(16) })
        page.addView(TextView(this).apply {
            text = "¿Solo quieres escuchar la radio?"; textSize = 13f; gravity = Gravity.CENTER
            setTextColor(mutedColor)
        }, fullWidth().apply { bottomMargin = dp(9) })
        radioButton = button("▶   Escuchar AfroGo FM en vivo", false) { toggleRadio() }
        page.addView(radioButton, fullWidth().apply { height = dp(50); bottomMargin = dp(7) })
        playbackStatus = TextView(this).apply {
            text = "La radio en vivo se puede escuchar sin iniciar sesión."
            textSize = 11f; setTextColor(Color.rgb(140, 136, 150)); gravity = Gravity.CENTER
        }
        page.addView(playbackStatus, fullWidth())
    }

    private fun buildMiniPlayer() {
        miniPlayer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(5), dp(8), dp(5))
            background = android.graphics.drawable.ColorDrawable(Color.rgb(48, 29, 62))
            visibility = View.GONE
            isClickable = true
            setOnClickListener { showPlayerScreen() }
        }
        miniPlayerCover = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundedSurface(accentColor, dp(5), accentColor)
            contentDescription = "Portada de la canción actual"
        }
        miniPlayer.addView(miniPlayerCover, LinearLayout.LayoutParams(dp(42), dp(42)).apply { rightMargin = dp(9) })
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        miniPlayerTitle = TextView(this).apply {
            text = "AfroGo FM"; textSize = 13f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        miniPlayerArtist = TextView(this).apply {
            text = "Tu música, tu ritmo"; textSize = 11f; setTextColor(Color.rgb(214, 202, 225))
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        labels.addView(miniPlayerTitle); labels.addView(miniPlayerArtist)
        miniPlayer.addView(labels, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        miniPlayerPlayButton = button("▶", false) {
            val player = controller
            if (player == null) connectMediaController()
            else if (player.isPlaying) player.pause() else player.play()
        }
        miniPlayer.addView(miniPlayerPlayButton, centeredSize(dp(38), dp(42)))
    }

    private fun buildBottomNavigation() {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(3), dp(4), dp(3), dp(4))
            background = android.graphics.drawable.ColorDrawable(Color.rgb(13, 13, 13))
        }
        val items = listOf(
            Triple("home", "⌂", "Inicio"),
            Triple("search", "⌕", "Buscar"),
            Triple("library", "▤", "Biblioteca")
        )
        items.forEach { (key, icon, label) ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(1), dp(3), dp(1), dp(2))
                isClickable = true; isFocusable = true
                setOnClickListener { navigateTo(key) }
            }
            val iconView = TextView(this).apply {
                text = icon; textSize = if (key == "create") 27f else 23f
                gravity = Gravity.CENTER; setTextColor(Color.rgb(145, 142, 151))
            }
            val labelView = TextView(this).apply {
                text = label; textSize = 9f; gravity = Gravity.CENTER
                setTextColor(Color.rgb(145, 142, 151)); maxLines = 1
            }
            item.addView(iconView); item.addView(labelView)
            nav.addView(item, LinearLayout.LayoutParams(0, dp(49), 1f))
            navLabels[key] = labelView
        }
        appShell.addView(nav, fullWidth().apply { height = dp(54) })
        updateNavSelection()
    }

    private fun navigateTo(tab: String) {
        if (tab == "create") { showCreateDialog(); return }
        when (tab) {
            "home" -> showHome()
            "search" -> showSearch()
            "library" -> showLibrary()
            "premium" -> showPremium()
            "player" -> showPlayerScreen()
        }
    }

    private fun updateNavSelection() {
        navLabels.forEach { (key, label) ->
            label.setTextColor(if (key == activeTab || (activeTab == "player" && key == "home")) accentColor else Color.rgb(145, 142, 151))
            label.setTypeface(null, if (key == activeTab) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun prepareScreen(title: String): LinearLayout {
        screenHost.removeAllViews()
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(bgColor) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(13), dp(16), dp(24))
            setBackgroundColor(bgColor)
        }
        scroll.addView(content, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ))
        screenHost.addView(scroll, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        activeTab = title
        updateNavSelection()
        return content
    }

    private fun showHome() {
        playerScreenCover = null
        playerScreenTitle = null
        playerScreenArtist = null
        playerScreenToggle = null
        playerScreenProgress = null
        playerScreenElapsed = null
        playerScreenDuration = null
        if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            showHomeLandscape()
            return
        }
        // Restore the standard mobile layout when the device returns to portrait.
        miniPlayer.visibility = View.VISIBLE
        if (appShell.childCount > 0) appShell.getChildAt(appShell.childCount - 1).visibility = View.VISIBLE
        val content = prepareScreen("home")
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(profileAvatar(42), LinearLayout.LayoutParams(dp(42), dp(42)).apply { rightMargin = dp(10) })
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        titles.addView(TextView(this).apply {
            text = "AfroGo FM"; textSize = 21f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
        })
        titles.addView(TextView(this).apply {
            text = "Tu música, tu ritmo"; textSize = 11f; setTextColor(mutedColor)
        })
        header.addView(titles, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(button("⋮", false) { showAccountDialog() }, centeredSize(dp(42), dp(42)))
        content.addView(header, fullWidth().apply { bottomMargin = dp(16) })

        val chips = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        chipRow.addView(chip("Todo", true) { showHome() }, wrapParams().apply { rightMargin = dp(7) })
        chipRow.addView(chip("Música", false) { showSearch() }, wrapParams().apply { rightMargin = dp(7) })
        chipRow.addView(chip("Favoritos", false) { catalogMode = "favorites"; showLibrary() }, wrapParams().apply { rightMargin = dp(7) })
        chipRow.addView(chip("Emisoras", false) { toggleRadio() }, wrapParams())
        chips.addView(chipRow)
        content.addView(chips, fullWidth().apply { bottomMargin = dp(23) })

        addSectionHeading(content, "Para arrancar", "Tu música disponible")
        val recent = catalogTracks.take(12)
        if (recent.isNotEmpty()) {
            addTrackCarousel(content, recent)
        } else {
            val emptyCard = coloredFeatureCard("Tu biblioteca está lista", "Inicia sesión para cargar canciones y portadas", "Explorar") { showSearch() }
            content.addView(emptyCard, fullWidth().apply { bottomMargin = dp(22) })
        }

        addSectionHeading(content, "Emisoras recomendadas", "Escucha en vivo")
        content.addView(coloredFeatureCard("AFROGO FM", "Radio oficial · Música en vivo", "▶  Escuchar") { toggleRadio() }, fullWidth().apply { bottomMargin = dp(22) })

        addSectionHeading(content, "Tu biblioteca", "Canciones y favoritos")
        if (recent.isNotEmpty()) {
            recent.take(5).forEach { track -> content.addView(compactTrackRow(track), fullWidth().apply { bottomMargin = dp(8) }) }
        } else {
            content.addView(TextView(this).apply {
                text = "Cuando inicies sesión, aquí verás canciones de la biblioteca central de AfroGo."
                textSize = 13f; setTextColor(mutedColor); setPadding(dp(4), dp(3), dp(4), dp(14))
            }, fullWidth())
        }
        content.addView(button("Ver toda la biblioteca  →", false) { showLibrary() }, fullWidth().apply { topMargin = dp(2); bottomMargin = dp(15) })
    }



    /**
     * Wide-screen home layout inspired by the desktop reference.
     * Android Auto itself uses the MediaLibraryService UI supplied by the car host.
     */
    private fun showHomeLandscape() {
        playerScreenCover = null
        playerScreenTitle = null
        playerScreenArtist = null
        playerScreenToggle = null
        playerScreenProgress = null
        playerScreenElapsed = null
        playerScreenDuration = null
        screenHost.removeAllViews()
        activeTab = "home"
        updateNavSelection()

        // Hide phone-only chrome in wide mode. The sidebar contains its own navigation.
        miniPlayer.visibility = View.GONE
        if (appShell.childCount > 0) appShell.getChildAt(appShell.childCount - 1).visibility = View.GONE

        val wideRoot = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(bgColor)
        }
        val sidebar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(18), dp(12), dp(12))
            setBackgroundColor(Color.rgb(24, 24, 24))
        }
        val brandRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        brandRow.addView(brandAvatar(40), LinearLayout.LayoutParams(dp(40), dp(40)).apply { rightMargin = dp(10) })
        val brandText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brandText.addView(TextView(this).apply { text = "AfroGo FM"; textSize = 20f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD) })
        brandText.addView(TextView(this).apply { text = "Tu música, tu ritmo"; textSize = 11f; setTextColor(mutedColor) })
        brandRow.addView(brandText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        sidebar.addView(brandRow, fullWidth().apply { bottomMargin = dp(22) })

        fun sideItem(label: String, icon: String, action: () -> Unit) {
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(10), dp(8), dp(10)); isClickable = true; isFocusable = true
                setOnClickListener { action() }
            }
            item.addView(TextView(this).apply { text = icon; textSize = 22f; setTextColor(Color.WHITE) },
                centeredSize(dp(34), dp(34)))
            item.addView(TextView(this).apply { text = label; textSize = 14f; setTextColor(Color.rgb(225, 225, 225)) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            sidebar.addView(item, fullWidth())
        }
        sideItem("Inicio", "⌂") { showHome() }
        sideItem("Buscar", "⌕") { showSearch() }
        sideItem("Tu biblioteca", "▤") { showLibrary() }
        sideItem("Radio en vivo", "♫") { toggleRadio() }
        val spacer = View(this)
        sidebar.addView(spacer, LinearLayout.LayoutParams(1, 0, 1f))

        // Desktop-like player dock on the left, matching the landscape reference.
        // Use dedicated views so portrait mini-player layout remains unchanged.
        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(2), dp(8), dp(2), dp(6))
            background = roundedSurface(Color.rgb(28, 25, 30), dp(8), Color.rgb(42, 38, 45))
        }
        val dockCover = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Portada de la canción actual"
            setImageResource(com.afrogo.fm.R.drawable.afrogo_icon_foreground)
        }
        landscapePlayerCover = dockCover
        dock.addView(dockCover, LinearLayout.LayoutParams(dp(104), dp(104)).apply { bottomMargin = dp(12) })
        val dockTitle = TextView(this).apply {
            text = miniPlayerTitle.text
            textSize = 14f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        landscapePlayerTitle = dockTitle
        dock.addView(dockTitle, fullWidth())
        val dockArtist = TextView(this).apply {
            text = miniPlayerArtist.text
            textSize = 12f; setTextColor(mutedColor)
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        landscapePlayerArtist = dockArtist
        dock.addView(dockArtist, fullWidth().apply { bottomMargin = dp(12) })
        val dockProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(75, 70, 78))
        }
        dock.addView(dockProgress, fullWidth().apply { bottomMargin = dp(8) })
        val dockControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
        }
        fun dockButton(label: String, action: () -> Unit) {
            dockControls.addView(button(label, label == "▶") { action() },
                LinearLayout.LayoutParams(0, dp(40), 1f).apply { leftMargin = dp(2); rightMargin = dp(2) })
        }
        dockButton("|◀") { controller?.seekToPreviousMediaItem() }
        val dockToggle = button(if (controller?.isPlaying == true) "Ⅱ" else "▶", true) {
            val active = controller
            if (active == null) connectMediaController()
            else if (active.isPlaying || active.playWhenReady) active.pause() else active.play()
        }
        landscapePlayerToggle = dockToggle
        dockControls.addView(dockToggle, LinearLayout.LayoutParams(0, dp(40), 1.2f))
        dockButton("▶|") { controller?.seekToNextMediaItem() }
        dock.addView(dockControls, fullWidth())
        dock.setOnClickListener { showPlayerScreen() }
        dockToggle.setOnClickListener {
            val active = controller
            if (active == null) connectMediaController()
            else if (active.isPlaying || active.playWhenReady) active.pause() else active.play()
        }
        sidebar.addView(dock, fullWidth())

        wideRoot.addView(sidebar, LinearLayout.LayoutParams(dp(245), LinearLayout.LayoutParams.MATCH_PARENT))
        val mainScroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(bgColor) }
        val mainContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(24))
            setBackgroundColor(bgColor)
        }
        val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        topRow.addView(profileAvatar(38), LinearLayout.LayoutParams(dp(38), dp(38)).apply { rightMargin = dp(12) })
        topRow.addView(TextView(this).apply {
            text = "Para arrancar"; textSize = 24f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        topRow.addView(button("⋮", false) { showAccountDialog() }, centeredSize(dp(42), dp(42)))
        mainContent.addView(topRow, fullWidth().apply { bottomMargin = dp(12) })

        val filters = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val filterRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        filterRow.addView(chip("Todo", true) { showHome() }, wrapParams().apply { rightMargin = dp(8) })
        filterRow.addView(chip("Música", false) { showSearch() }, wrapParams().apply { rightMargin = dp(8) })
        filterRow.addView(chip("Favoritos", false) { catalogMode = "favorites"; showLibrary() }, wrapParams().apply { rightMargin = dp(8) })
        filterRow.addView(chip("Emisoras", false) { toggleRadio() }, wrapParams())
        filters.addView(filterRow)
        mainContent.addView(filters, fullWidth().apply { bottomMargin = dp(20) })

        addSectionHeading(mainContent, "Para arrancar", "Tu música disponible")
        val tracks = catalogTracks.take(12)
        if (tracks.isNotEmpty()) addTrackCarousel(mainContent, tracks)
        else mainContent.addView(coloredFeatureCard("Tu biblioteca está lista", "Inicia sesión para cargar canciones y portadas", "Explorar") { showSearch() },
            fullWidth().apply { bottomMargin = dp(18) })
        addSectionHeading(mainContent, "Emisoras recomendadas", "Escucha en vivo")
        mainContent.addView(coloredFeatureCard("AFROGO FM", "Radio oficial · Música en vivo", "▶ Escuchar") { toggleRadio() },
            fullWidth().apply { bottomMargin = dp(18) })

        mainScroll.addView(mainContent, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        wideRoot.addView(mainScroll, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        screenHost.addView(wideRoot, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
    }

    private fun showSearch() {
        val content = prepareScreen("search")
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(brandAvatar(38), LinearLayout.LayoutParams(dp(38), dp(38)).apply { rightMargin = dp(10) })
        header.addView(TextView(this).apply {
            text = "Buscar"; textSize = 25f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(button("↻", false) { loadCatalog() }, centeredSize(dp(42), dp(42)))
        content.addView(header, fullWidth().apply { bottomMargin = dp(14) })

        detachFromParent(searchBar)
        searchBar.visibility = View.VISIBLE
        content.addView(searchBar, fullWidth().apply { bottomMargin = dp(16) })
        if (catalogTracks.isEmpty() && AfrogoApi.savedToken(this).isBlank()) {
            content.addView(TextView(this).apply {
                text = "Inicia sesión para cargar el catálogo musical completo."
                textSize = 12f; setTextColor(mutedColor); setPadding(dp(2), 0, dp(2), dp(12))
            }, fullWidth())
        }

        addSectionHeading(content, "Explora", "Accesos rápidos")
        val categories = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val firstRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        firstRow.addView(categoryTile("Música", "Canciones y artistas", Color.rgb(116, 13, 154)) { catalogMode = "all"; showLibrary() }, LinearLayout.LayoutParams(0, dp(92), 1f).apply { rightMargin = dp(8) })
        firstRow.addView(categoryTile("Favoritos", "Lo que te gusta", Color.rgb(45, 98, 63)) { catalogMode = "favorites"; showLibrary() }, LinearLayout.LayoutParams(0, dp(92), 1f))
        categories.addView(firstRow, fullWidth().apply { bottomMargin = dp(8) })
        val secondRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        secondRow.addView(categoryTile("Playlists", "Tus listas", Color.rgb(38, 70, 145)) { showPlaylistsScreen() }, LinearLayout.LayoutParams(0, dp(92), 1f).apply { rightMargin = dp(8) })
        secondRow.addView(categoryTile("En directo", "AfroGo FM", Color.rgb(112, 18, 190)) { toggleRadio() }, LinearLayout.LayoutParams(0, dp(92), 1f))
        categories.addView(secondRow, fullWidth().apply { bottomMargin = dp(23) })
        content.addView(categories, fullWidth())

        addSectionHeading(content, "Canciones de AfroGo", "Resultados del catálogo")
        catalogSummary = TextView(this).apply {
            text = "${catalogTracks.size} canciones disponibles"; textSize = 12f; setTextColor(mutedColor)
            setPadding(dp(1), dp(1), dp(1), dp(9))
        }
        content.addView(catalogSummary, fullWidth())
        catalogContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(catalogContainer, fullWidth())
        renderCatalogTracks()
    }

    private fun showLibrary() {
        val content = prepareScreen("library")
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(brandAvatar(38), LinearLayout.LayoutParams(dp(38), dp(38)).apply { rightMargin = dp(10) })
        header.addView(TextView(this).apply {
            text = "Tu biblioteca"; textSize = 24f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(button("⌕", false) { showSearch() }, centeredSize(dp(42), dp(42)))
        header.addView(button("＋", false) { showCreateDialog() }, centeredSize(dp(42), dp(42)))
        content.addView(header, fullWidth().apply { bottomMargin = dp(16) })

        val filters = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val filterRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        filterRow.addView(chip("Artistas", catalogMode == "artists" || catalogMode == "artist") { catalogMode = "artists"; selectedArtist = ""; activePlaylistId = ""; renderCatalogTracks() }, wrapParams().apply { rightMargin = dp(7) })
        filterRow.addView(chip("Canciones", catalogMode == "all") { catalogMode = "all"; selectedArtist = ""; activePlaylistId = ""; renderCatalogTracks() }, wrapParams().apply { rightMargin = dp(7) })
        filterRow.addView(chip("Favoritos", catalogMode == "favorites") { catalogMode = "favorites"; renderCatalogTracks() }, wrapParams().apply { rightMargin = dp(7) })
        filterRow.addView(chip("Playlists", catalogMode == "playlist") { showPlaylistsScreen() }, wrapParams())
        filters.addView(filterRow)
        content.addView(filters, fullWidth().apply { bottomMargin = dp(22) })

        val libraryHeading = when (catalogMode) {
            "artists" -> "Artistas"
            "artist" -> "Canciones de $selectedArtist"
            "favorites" -> "Tus favoritos"
            "playlist" -> "Tu playlist"
            else -> "Recientes"
        }
        addSectionHeading(content, libraryHeading, "Sincronizado con tu cuenta")
        catalogSummary = TextView(this).apply {
            text = "Inicia sesión para sincronizar tu biblioteca."; textSize = 12f; setTextColor(mutedColor)
            setPadding(dp(1), dp(2), dp(1), dp(8))
        }
        content.addView(catalogSummary, fullWidth())
        catalogContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(catalogContainer, fullWidth())
        content.addView(button("＋  Crear playlist", false) { createCloudPlaylist(null) }, fullWidth().apply { topMargin = dp(10); bottomMargin = dp(12) })
        renderCatalogTracks()
    }

    private fun showPremium() {
        val content = prepareScreen("premium")
        content.addView(brandAvatar(54), centeredSize(dp(54), dp(54)).apply { bottomMargin = dp(16) })
        content.addView(TextView(this).apply {
            text = "AfroGo+"; textSize = 30f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
        }, fullWidth().apply { bottomMargin = dp(7) })
        content.addView(TextView(this).apply {
            text = "Una experiencia musical conectada a tu cuenta AfroGo."; textSize = 14f; gravity = Gravity.CENTER
            setTextColor(mutedColor)
        }, fullWidth().apply { bottomMargin = dp(20) })
        content.addView(coloredFeatureCard("Tu música, en un solo lugar", "Accede a tu biblioteca central, favoritos y playlists sincronizadas entre PC y móvil.", "Ver biblioteca") { showLibrary() }, fullWidth().apply { bottomMargin = dp(13) })
        content.addView(coloredFeatureCard("Radio en vivo", "Escucha la señal oficial de AfroGo FM.", "▶  Escuchar radio") { toggleRadio() }, fullWidth().apply { bottomMargin = dp(13) })
        content.addView(TextView(this).apply {
            text = "Esta sección se irá ampliando con nuevas funciones de AfroGo."
            textSize = 12f; gravity = Gravity.CENTER; setTextColor(mutedColor); setPadding(dp(12), dp(15), dp(12), dp(10))
        }, fullWidth())
    }

    private fun showPlayerScreen() {
        if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            showPlayerScreenLandscape()
            return
        }
        val content = prepareScreen("player")
        content.setPadding(dp(4), dp(8), dp(4), dp(20))
        val current = controller?.currentMediaItem
        val metadata = current?.mediaMetadata
        val artworkUrl = metadata?.artworkUri?.toString().orEmpty()
        val titleText = metadata?.title?.toString()?.takeIf { it.isNotBlank() } ?: "AfroGo FM"
        val artistText = metadata?.artist?.toString()?.takeIf { it.isNotBlank() } ?: "Radio oficial · Música en vivo"
        content.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.rgb(130, 75, 43), Color.rgb(83, 52, 37), Color.rgb(29, 24, 23), bgColor))

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
        }
        header.addView(button("⌄", false) { showHome() }, centeredSize(dp(42), dp(42)))
        val headerLabels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        headerLabels.addView(TextView(this).apply {
            text = "Reproduciendo desde lista"; textSize = 10f; gravity = Gravity.CENTER
            setTextColor(Color.rgb(245, 224, 209))
        }, fullWidth())
        headerLabels.addView(TextView(this).apply {
            text = metadata?.albumTitle?.toString()?.takeIf { it.isNotBlank() } ?: "AfroGo FM"
            textSize = 11f; gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE)
            maxLines = 1
        }, fullWidth())
        header.addView(headerLabels, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(button("⋮", false) { showCreateDialog() }, centeredSize(dp(42), dp(42)))
        content.addView(header, fullWidth().apply { bottomMargin = dp(16) })

        val cover = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundedSurface(accentColor, dp(8), accentColor)
            contentDescription = "Portada de la reproducción actual"
            tag = artworkUrl
        }
        val screenWidth = resources.displayMetrics.widthPixels
        val coverSize = (screenWidth - dp(76)).coerceAtMost(dp(440))
        content.addView(cover, centeredSize(coverSize, coverSize).apply { bottomMargin = dp(30) })
        if (artworkUrl.isNotBlank()) loadCoverInto(artworkUrl, cover)
        else cover.setImageResource(com.afrogo.fm.R.drawable.afrogo_icon_foreground)

        val trackLine = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), 0, dp(24), 0)
        }
        val trackLabels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        trackLabels.addView(TextView(this).apply {
            text = titleText; textSize = 16f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD); maxLines = 2
        }, fullWidth())
        trackLabels.addView(TextView(this).apply {
            text = artistText; textSize = 12f; setTextColor(Color.rgb(226, 211, 199)); maxLines = 1
        }, fullWidth().apply { topMargin = dp(3) })
        trackLine.addView(trackLabels, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        trackLine.addView(button("×", false) { /* La canción permanece en la cola. */ }, centeredSize(dp(42), dp(42)))
        trackLine.addView(button("＋", false) { showCreateDialog() }, centeredSize(dp(42), dp(42)).apply { leftMargin = dp(3) })
        content.addView(trackLine, fullWidth().apply { bottomMargin = dp(15) })

        val progress = SeekBar(this).apply {
            max = 1000
            progressTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.argb(100, 255, 255, 255))
            contentDescription = "Progreso de reproducción"
        }
        content.addView(progress, fullWidth().apply { leftMargin = dp(22); rightMargin = dp(22) })
        val times = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(24), 0, dp(24), 0)
        }
        val elapsed = TextView(this).apply { text = "0:00"; textSize = 10f; setTextColor(Color.rgb(220, 204, 191)) }
        val duration = TextView(this).apply { text = "--:--"; textSize = 10f; setTextColor(Color.rgb(220, 204, 191)); gravity = Gravity.END }
        times.addView(elapsed, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        times.addView(duration, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(times, fullWidth().apply { bottomMargin = dp(20) })

        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        controls.addView(button("|◀", false) { controller?.seekToPreviousMediaItem() }, centeredSize(dp(58), dp(48)).apply { rightMargin = dp(22) })
        controls.addView(button(if (controller?.isPlaying == true) "Ⅱ" else "▶", true) {
            val player = controller
            if (player == null) connectMediaController() else if (player.isPlaying) player.pause() else player.play()
            updatePlaybackUi()
        }, centeredSize(dp(62), dp(58)).apply { rightMargin = dp(22) })
        controls.addView(button("▶|", false) { controller?.seekToNextMediaItem() }, centeredSize(dp(58), dp(48)))
        content.addView(controls, fullWidth().apply { bottomMargin = dp(24) })

        val player = controller
        if (player != null) {
            val total = player.duration
            if (total > 0 && total != androidx.media3.common.C.TIME_UNSET) duration.text = formatPlayerTime(total)
            val position = player.currentPosition.coerceAtLeast(0L)
            if (total > 0 && total != androidx.media3.common.C.TIME_UNSET) progress.progress = ((position.toDouble() / total) * 1000).toInt()
            elapsed.text = formatPlayerTime(position)
            progress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) {
                    if (fromUser) {
                        isUserSeekingPlayerProgress = true
                        val currentDuration = player.duration
                        if (currentDuration > 0 && currentDuration != androidx.media3.common.C.TIME_UNSET) {
                            elapsed.text = formatPlayerTime(currentDuration * value / 1000)
                        }
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) { isUserSeekingPlayerProgress = true }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val currentDuration = player.duration
                    if (currentDuration > 0 && currentDuration != androidx.media3.common.C.TIME_UNSET) {
                        player.seekTo(currentDuration * (seekBar?.progress ?: 0) / 1000)
                    }
                    isUserSeekingPlayerProgress = false
                    updatePlayerProgressUi()
                }
            })
        }
        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(24), dp(8), dp(24), 0) }
        footer.addView(TextView(this).apply { text = "▣  Dispositivos disponibles"; textSize = 11f; setTextColor(Color.WHITE) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        footer.addView(TextView(this).apply { text = "↗"; textSize = 20f; setTextColor(Color.WHITE) })
        content.addView(footer, fullWidth())
    }


    /**
     * Wide player layout: large artwork on the left and playback information/controls
     * on the right, so landscape mode uses the available width instead of showing
     * only the mini-player strip.
     */
    private fun showPlayerScreenLandscape() {
        screenHost.removeAllViews()
        activeTab = "player"
        updateNavSelection()
        miniPlayer.visibility = View.GONE
        if (appShell.childCount > 0) appShell.getChildAt(appShell.childCount - 1).visibility = View.GONE

        val current = controller?.currentMediaItem
        val metadata = current?.mediaMetadata
        val artworkUrl = metadata?.artworkUri?.toString().orEmpty()
        val titleText = metadata?.title?.toString()?.takeIf { it.isNotBlank() } ?: "AfroGo FM"
        val artistText = metadata?.artist?.toString()?.takeIf { it.isNotBlank() } ?: "Radio oficial · Música en vivo"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), dp(14), dp(22), dp(14))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(47, 28, 58), Color.rgb(18, 18, 18))
            )
        }
        val coverColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val cover = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundedSurface(accentColor, dp(8), accentColor)
            contentDescription = "Portada de la reproducción actual"
            tag = artworkUrl
        }
        playerScreenCover = cover
        val availableHeight = (resources.displayMetrics.heightPixels - dp(70)).coerceAtLeast(dp(150))
        val coverSize = (availableHeight - dp(16)).coerceAtMost(dp(300))
        coverColumn.addView(cover, LinearLayout.LayoutParams(coverSize, coverSize))
        if (artworkUrl.isNotBlank()) loadCoverInto(artworkUrl, cover)
        else cover.setImageResource(com.afrogo.fm.R.drawable.afrogo_icon_foreground)
        root.addView(coverColumn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.9f))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), dp(8), dp(12), dp(8))
        }
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topRow.addView(button("⌄", false) { showHome() }, centeredSize(dp(42), dp(42)))
        topRow.addView(TextView(this).apply {
            text = "REPRODUCIENDO"; textSize = 10f; gravity = Gravity.CENTER
            setTextColor(Color.rgb(196, 178, 208))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        topRow.addView(button("⋮", false) { showCreateDialog() }, centeredSize(dp(42), dp(42)))
        panel.addView(topRow, fullWidth().apply { bottomMargin = dp(18) })

        val playerTitleView = TextView(this).apply {
            text = titleText; textSize = 22f; maxLines = 2
            setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
        }
        playerScreenTitle = playerTitleView
        panel.addView(playerTitleView, fullWidth())
        val playerArtistView = TextView(this).apply {
            text = artistText; textSize = 14f
            setTextColor(Color.rgb(205, 195, 214))
        }
        playerScreenArtist = playerArtistView
        panel.addView(playerArtistView, fullWidth().apply { topMargin = dp(5); bottomMargin = dp(16) })

        val progress = SeekBar(this).apply {
            max = 1000
            progressTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.argb(100, 255, 255, 255))
            contentDescription = "Progreso de reproducción"
        }
        val player = controller
        val elapsed = TextView(this).apply { text = "0:00"; textSize = 10f; setTextColor(mutedColor) }
        val duration = TextView(this).apply { text = "--:--"; textSize = 10f; setTextColor(mutedColor); gravity = Gravity.END }
        playerScreenProgress = progress
        playerScreenElapsed = elapsed
        playerScreenDuration = duration
        val times = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        times.addView(elapsed, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        times.addView(duration, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        panel.addView(progress, fullWidth())
        panel.addView(times, fullWidth().apply { bottomMargin = dp(18) })
        if (player != null) {
            val total = player.duration
            if (total > 0 && total != androidx.media3.common.C.TIME_UNSET) {
                duration.text = formatPlayerTime(total)
                progress.progress = ((player.currentPosition.coerceAtLeast(0L).toDouble() / total) * 1000).toInt()
            }
            elapsed.text = formatPlayerTime(player.currentPosition)
            progress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) {
                    if (fromUser && total > 0 && total != androidx.media3.common.C.TIME_UNSET) elapsed.text = formatPlayerTime(total * value / 1000)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    if (total > 0 && total != androidx.media3.common.C.TIME_UNSET) player.seekTo(total * (seekBar?.progress ?: 0) / 1000)
                }
            })
        }

        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        controls.addView(button("|◀", false) { controller?.seekToPreviousMediaItem() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        val playerToggle = button(
            if (controller?.isPlaying == true || controller?.playWhenReady == true) "Ⅱ" else "▶",
            true
        ) {
            val active = controller
            if (active == null) connectMediaController()
            else if (active.isPlaying || active.playWhenReady) active.pause() else active.play()
            // Media3 listener updates the icon after the actual player state changes.
            updatePlaybackUi()
        }
        playerScreenToggle = playerToggle
        controls.addView(playerToggle, centeredSize(dp(62), dp(58)))
        controls.addView(button("▶|", false) { controller?.seekToNextMediaItem() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        panel.addView(controls, fullWidth().apply { topMargin = dp(6); bottomMargin = dp(12) })
        panel.addView(TextView(this).apply {
            text = "La reproducción también funciona con los controles multimedia y Android Auto."
            textSize = 11f; gravity = Gravity.CENTER
            setTextColor(Color.rgb(155, 148, 164))
        }, fullWidth())
        root.addView(panel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.1f))
        screenHost.addView(root, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
    }

    /** Refreshes the elapsed timer and seek bar while the player is open. */
    private fun updatePlayerProgressUi() {
        val activePlayer = controller ?: return
        val seekBar = playerScreenProgress ?: return
        val elapsedLabel = playerScreenElapsed ?: return
        val durationLabel = playerScreenDuration ?: return
        val durationMs = activePlayer.duration
        val positionMs = activePlayer.currentPosition.coerceAtLeast(0L)
        elapsedLabel.text = formatPlayerTime(positionMs)
        if (durationMs > 0L && durationMs != androidx.media3.common.C.TIME_UNSET) {
            durationLabel.text = formatPlayerTime(durationMs)
            if (!isUserSeekingPlayerProgress) {
                seekBar.progress = ((positionMs.toDouble() / durationMs.toDouble()) * seekBar.max)
                    .toInt().coerceIn(0, seekBar.max)
            }
        } else {
            durationLabel.text = "LIVE"
            if (!isUserSeekingPlayerProgress) seekBar.progress = 0
        }
    }

    private fun formatPlayerTime(milliseconds: Long): String {
        val seconds = (milliseconds.coerceAtLeast(0L) / 1000L).toInt()
        return String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
    }

    private fun showCreateDialog() {
        val choices = arrayOf("Crear playlist", "Ver mis playlists", "Abrir favoritos", "Escuchar AfroGo FM en vivo")
        AlertDialog.Builder(this)
            .setTitle("Crear y organizar")
            .setItems(choices) { _, which ->
                when (which) {
                    0 -> createCloudPlaylist(null)
                    1 -> showPlaylistsDialog()
                    2 -> { catalogMode = "favorites"; showLibrary() }
                    3 -> toggleRadio()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showAccountDialog() {
        val email = try { authStatus.text.toString().substringAfter("Sesión activa:", "Cuenta AfroGo").trim() } catch (_: Exception) { "Cuenta AfroGo" }
        AlertDialog.Builder(this)
            .setTitle("Tu cuenta AfroGo")
            .setMessage(if (email.isBlank()) "Sesión conectada" else email)
            .setPositiveButton("Actualizar biblioteca") { _, _ -> loadCatalog() }
            .setNegativeButton("Cerrar sesión") { _, _ -> logout() }
            .setNeutralButton("Cancelar", null)
            .show()
    }

    /** Menú de perfil con accesos rápidos de cuenta. */
    private fun showProfileMenu() {
        val signedIn = authStatus.text.toString().contains("Sesión activa:", ignoreCase = true)
        val accountName = if (signedIn) authStatus.text.toString()
            .substringAfter("Sesión activa:", "Cuenta AfroGo").trim().ifBlank { "Cuenta AfroGo" }
            else "Cuenta AfroGo"
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(8))
            setBackgroundColor(Color.rgb(31, 30, 32))
        }
        val identity = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(14)); isClickable = true
            setOnClickListener { showAccountDialog() }
        }
        identity.addView(brandAvatar(48), LinearLayout.LayoutParams(dp(48), dp(48)).apply { rightMargin = dp(12) })
        val identityText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        identityText.addView(TextView(this).apply {
            text = accountName; textSize = 16f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        })
        identityText.addView(TextView(this).apply { text = "Ver perfil"; textSize = 12f; setTextColor(Color.LTGRAY); setPadding(0, dp(3), 0, 0) })
        identity.addView(identityText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        panel.addView(identity, fullWidth())
        addProfileMenuItem(panel, "＋", "Añadir cuenta", "", Color.LTGRAY) { showLoginHint() }
        addProfileMenuItem(panel, "⌁", "Estadísticas de escucha", "Novedad", Color.WHITE) {
            AlertDialog.Builder(this).setTitle("Estadísticas de escucha")
                .setMessage("Las estadísticas de escucha estarán disponibles próximamente.")
                .setPositiveButton("Entendido", null).show()
        }
        addProfileMenuItem(panel, "◷", "Recientes", "", Color.WHITE) { catalogMode = "recent"; showLibrary() }
        addProfileMenuItem(panel, "♢", "Tus avisos", "", Color.WHITE) {
            AlertDialog.Builder(this).setTitle("Tus avisos").setMessage("No tienes avisos nuevos por ahora.")
                .setPositiveButton("Aceptar", null).show()
        }
        addProfileMenuItem(panel, "⚙", "Configuración y privacidad", "", Color.WHITE) {
            AlertDialog.Builder(this).setTitle("Configuración y privacidad")
                .setItems(arrayOf("Actualizar biblioteca", "Cuenta", "Cerrar sesión")) { _, which ->
                    when (which) {
                        0 -> loadCatalog()
                        1 -> showAccountDialog()
                        2 -> AlertDialog.Builder(this).setTitle("Cerrar sesión")
                            .setMessage("¿Quieres cerrar tu sesión de AfroGo FM?")
                            .setNegativeButton("Cancelar", null)
                            .setPositiveButton("Cerrar sesión") { _, _ -> logout() }.show()
                    }
                }.show()
        }
        AlertDialog.Builder(this).setView(panel).setNegativeButton("Cerrar", null).show()
    }

    private fun addProfileMenuItem(panel: LinearLayout, symbol: String, title: String, subtitle: String, iconColor: Int, action: () -> Unit) {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(11), dp(4), dp(11)); isClickable = true; isFocusable = true
            setOnClickListener { action() }
        }
        item.addView(TextView(this).apply {
            text = symbol; textSize = 22f; gravity = Gravity.CENTER; setTextColor(iconColor)
        }, LinearLayout.LayoutParams(dp(38), dp(38)))
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        texts.addView(TextView(this).apply { text = title; textSize = 14f; setTextColor(Color.WHITE) })
        if (subtitle.isNotBlank()) texts.addView(TextView(this).apply {
            text = subtitle; textSize = 10f; setTextColor(Color.rgb(150, 190, 255)); setPadding(0, dp(2), 0, 0)
        })
        item.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        panel.addView(item, fullWidth())
    }

    private fun profileAvatar(size: Int): View = brandAvatar(size).apply {
        isClickable = true; isFocusable = true; setOnClickListener { showProfileMenu() }
        contentDescription = "Abrir menú de perfil"
    }

    private fun showLoginHint() {
        AlertDialog.Builder(this)
            .setTitle("Inicia sesión")
            .setMessage("Usa tu cuenta AfroGo para buscar canciones del catálogo compartido y sincronizar tus favoritos y playlists.")
            .setPositiveButton("Entendido", null)
            .show()
    }

    private fun buildTrackCard(track: AfrogoTrack): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(10))
            background = roundedSurface(Color.rgb(30, 29, 33), dp(12), Color.rgb(43, 41, 47))
            isClickable = true; isFocusable = true
            setOnClickListener { playTrack(track) }
        }
        val cover = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundedSurface(accentColor, dp(8), accentColor)
            contentDescription = "Portada de ${track.title}"; tag = track.coverUrl
        }
        card.addView(cover, LinearLayout.LayoutParams(dp(132), dp(132)).apply { bottomMargin = dp(9) })
        if (track.coverUrl.isNotBlank()) loadCoverInto(track.coverUrl, cover)
        card.addView(TextView(this).apply {
            text = track.title; textSize = 13f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(dp(132), LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(3) })
        card.addView(TextView(this).apply {
            text = track.artist; textSize = 11f; setTextColor(mutedColor)
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(dp(132), LinearLayout.LayoutParams.WRAP_CONTENT))
        return card
    }

    private fun addTrackCarousel(parent: LinearLayout, tracks: List<AfrogoTrack>) {
        val horizontal = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tracks.take(10).forEach { track ->
            row.addView(buildTrackCard(track), LinearLayout.LayoutParams(dp(148), LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(10) })
        }
        horizontal.addView(row)
        parent.addView(horizontal, fullWidth().apply { bottomMargin = dp(23) })
    }

    private fun compactTrackRow(track: AfrogoTrack): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(7), dp(8), dp(7))
            background = roundedSurface(Color.rgb(27, 27, 29), dp(10), Color.rgb(38, 38, 41))
            isClickable = true; setOnClickListener { playTrack(track) }
        }
        val cover = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundedSurface(accentColor, dp(6), accentColor); contentDescription = "Portada ${track.title}"
        }
        row.addView(cover, LinearLayout.LayoutParams(dp(54), dp(54)).apply { rightMargin = dp(11) })
        if (track.coverUrl.isNotBlank()) loadCoverInto(track.coverUrl, cover)
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        info.addView(TextView(this).apply {
            text = track.title; textSize = 14f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        })
        info.addView(TextView(this).apply {
            text = track.artist; textSize = 12f; setTextColor(mutedColor); maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, dp(3), 0, 0)
        })
        row.addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(button("▶", true) { playTrack(track) }, centeredSize(dp(40), dp(42)))
        return row
    }

    private fun addSectionHeading(parent: LinearLayout, title: String, subtitle: String) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        row.addView(TextView(this).apply {
            text = title; textSize = 19f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
        })
        row.addView(TextView(this).apply {
            text = subtitle; textSize = 11f; setTextColor(mutedColor); setPadding(0, dp(3), 0, 0)
        })
        parent.addView(row, fullWidth().apply { bottomMargin = dp(12) })
    }

    private fun coloredFeatureCard(title: String, subtitle: String, action: String, click: () -> Unit): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(89, 15, 129), Color.rgb(40, 21, 50))).apply { cornerRadius = dp(14).toFloat() }
        }
        card.addView(TextView(this).apply {
            text = title; textSize = 18f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
        }, fullWidth().apply { bottomMargin = dp(5) })
        card.addView(TextView(this).apply {
            text = subtitle; textSize = 12f; setTextColor(Color.rgb(227, 217, 236))
        }, fullWidth().apply { bottomMargin = dp(13) })
        card.addView(button(action, true, click), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(42)))
        card.isClickable = true
        return card
    }

    private fun categoryTile(title: String, subtitle: String, color: Int, click: () -> Unit): View {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(9), dp(10), dp(9))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(color, darken(color))).apply { cornerRadius = dp(11).toFloat() }
            isClickable = true; isFocusable = true; setOnClickListener { click() }
        }
        tile.addView(TextView(this).apply {
            text = title; textSize = 16f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
        })
        tile.addView(TextView(this).apply {
            text = subtitle; textSize = 10f; setTextColor(Color.rgb(239, 232, 245))
        })
        return tile
    }

    private fun chip(label: String, selected: Boolean, click: () -> Unit): View = TextView(this).apply {
        text = label; textSize = 12f; gravity = Gravity.CENTER
        setPadding(dp(14), dp(9), dp(14), dp(9))
        setTextColor(if (selected) Color.BLACK else Color.WHITE)
        background = roundedSurface(if (selected) Color.rgb(218, 188, 240) else Color.rgb(40, 39, 43), dp(22), Color.TRANSPARENT)
        isClickable = true; isFocusable = true; setOnClickListener { click() }
    }

    private fun brandAvatar(size: Int): View = TextView(this).apply {
        text = "A"; textSize = (size * 0.42f).coerceAtLeast(16f); gravity = Gravity.CENTER
        setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(accentColor, Color.rgb(69, 28, 91))).apply { shape = GradientDrawable.OVAL }
        contentDescription = "AfroGo"
    }

    private fun wrapParams() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    private fun detachFromParent(view: View) {
        (view.parent as? ViewGroup)?.removeView(view)
    }

    private fun roundedSurface(color: Int, radius: Int, stroke: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = radius.toFloat()
        if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
    }

    private fun darken(color: Int): Int {
        val r = (Color.red(color) * 0.68f).toInt()
        val g = (Color.green(color) * 0.68f).toInt()
        val b = (Color.blue(color) * 0.68f).toInt()
        return Color.rgb(r, g, b)
    }

    private fun setAuthenticatedUi(signedIn: Boolean) {
        loginRoot.visibility = if (signedIn) View.GONE else View.VISIBLE
        appShell.visibility = if (signedIn) View.VISIBLE else View.GONE
        if (signedIn) {
            isRegistrationMode = false
            nameInput.visibility = View.GONE
            signInButton.text = "Iniciar sesión"
            registerButton.text = "Solicitar acceso"
            showHome()
        }
    }

    private fun logout() {
        AfrogoApi.clearToken(this)
        controller?.stop()
        controller?.clearMediaItems()
        catalogTracks = emptyList()
        favoriteTrackIds.clear()
        userPlaylists.clear()
        catalogMode = "all"
        selectedArtist = ""
        activePlaylistId = ""
        authStatus.text = "Has cerrado sesión. Tus datos en la nube se conservan en tu cuenta."
        playbackStatus.text = "Sesión cerrada."
        setAuthenticatedUi(false)
        miniPlayer.visibility = View.GONE
        catalogSummary.text = "Inicia sesión para explorar las canciones."
        renderCatalogMessage("Inicia sesión con una cuenta aprobada para acceder a la biblioteca.")
        radioButton.text = "▶   Escuchar AfroGo FM en vivo"
    }

    private fun authenticate(register: Boolean) {
        val email = emailInput.text.toString().trim().lowercase()
        val password = passwordInput.text.toString()
        val name = nameInput.text.toString().trim().ifBlank { email.substringBefore('@') }
        if (email.isBlank() || password.length < 10) {
            authStatus.text = "Introduce un correo y una contraseña de al menos 10 caracteres."
            return
        }
        signInButton.isEnabled = false; registerButton.isEnabled = false
        authStatus.text = if (register) "Enviando solicitud…" else "Iniciando sesión…"
        Thread {
            try {
                if (register) {
                    val result = AfrogoApi.register(name, email, password)
                    runOnUiThread { authStatus.text = result.optString("message", "Solicitud enviada; espera la aprobación del administrador.") }
                } else {
                    val result = AfrogoApi.login(email, password)
                    val token = result.optString("token", "")
                    if (token.isBlank()) throw IllegalStateException("El servidor no devolvió una sesión válida.")
                    val refreshToken = result.optString("refreshToken", "")
                    AfrogoApi.saveTokens(this, token, refreshToken)
                    runOnUiThread { setAuthenticatedUi(true); authStatus.text = "Sesión iniciada. Descargando biblioteca central…"; loadCatalog() }
                }
            } catch (e: Exception) {
                runOnUiThread { authStatus.text = e.message ?: "No se pudo conectar con el servidor de Afrogo." }
            } finally {
                runOnUiThread { signInButton.isEnabled = true; registerButton.isEnabled = true }
            }
        }.start()
    }

    private fun loadCatalog() {
        val token = AfrogoApi.savedToken(this)
        if (token.isBlank()) {
            searchBar.visibility = View.GONE
            catalogTracks = emptyList()
            authStatus.text = "Inicia sesión con una cuenta aprobada para ver la biblioteca."
            catalogSummary.text = "Inicia sesión para explorar las canciones."
            renderCatalogMessage("La biblioteca compartida requiere iniciar sesión.")
            return
        }
        searchBar.visibility = View.VISIBLE
        renderCatalogMessage("Consultando AzuraCast…")
        Thread {
            try {
                val tracks = try {
                    AfrogoApi.catalog(token)
                } catch (e: AfrogoApiException) {
                    if (e.statusCode != 401 || AfrogoApi.savedRefreshToken(this).isBlank()) throw e
                    AfrogoApi.catalog(AfrogoApi.refreshSavedSession(this))
                }
                val currentToken = AfrogoApi.savedToken(this)
                val cloudLibrary = try { AfrogoApi.library(currentToken) } catch (_: Exception) { JSONObject() }
                val favoritesJson = cloudLibrary.optJSONArray("favorites") ?: JSONArray()
                val playlistJson = cloudLibrary.optJSONArray("playlists") ?: JSONArray()
                val favorites = linkedSetOf<String>()
                for (i in 0 until favoritesJson.length()) favorites.add(favoritesJson.optString(i))
                val playlists = mutableListOf<JSONObject>()
                for (i in 0 until playlistJson.length()) playlistJson.optJSONObject(i)?.let { playlists.add(it) }
                runOnUiThread {
                    catalogTracks = tracks
                    favoriteTrackIds = favorites
                    userPlaylists = playlists
                    authStatus.text = "Biblioteca sincronizada: ${tracks.size} canciones · ${userPlaylists.size} playlist(s) en la nube."
                    if (activeTab == "home") showHome() else renderCatalogTracks()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    authStatus.text = e.message ?: "No se pudo cargar el catálogo."
                    catalogSummary.text = "No se pudo actualizar la biblioteca."
                    renderCatalogMessage("No se pudo cargar el catálogo de AzuraCast.")
                    if (activeTab == "home") showHome()
                }
            }
        }.start()
    }

    private fun renderCatalogTracks() {
        if (!::catalogContainer.isInitialized) return
        catalogContainer.removeAllViews()
        val normalizedQuery = normalizeSearch(catalogQuery)
        val playlistIds = userPlaylists.firstOrNull { it.optString("id") == activePlaylistId }?.optJSONArray("songIds")?.let { arr ->
            (0 until arr.length()).map { arr.optString(it) }.toSet()
        } ?: emptySet()
        val filtered = catalogTracks.filter { track ->
            val cloudId = cloudTrackId(track)
            val modeMatches = when (catalogMode) {
                "favorites" -> favoriteTrackIds.contains(cloudId)
                "playlist" -> playlistIds.contains(cloudId)
                "artist" -> track.artist.equals(selectedArtist, ignoreCase = true)
                else -> true
            }
            modeMatches && (normalizedQuery.isBlank() || normalizeSearch("${track.title} ${track.artist} ${track.album} ${track.genre}").contains(normalizedQuery))
        }
        if (catalogMode == "artists") {
            val artistGroups = catalogTracks.groupBy { normalizeSearch(it.artist).ifBlank { "artista desconocido" } }
                .toList().sortedBy { it.second.firstOrNull()?.artist?.lowercase() ?: "" }
            catalogSummary.text = "${artistGroups.size} artistas en la biblioteca central"
            if (artistGroups.isEmpty()) {
                renderCatalogMessage("Cuando la biblioteca tenga canciones, aquí aparecerán sus artistas.")
                return
            }
            artistGroups.forEach { (_, songs) ->
                val first = songs.first()
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(8), dp(7), dp(10), dp(7))
                    background = roundedSurface(Color.rgb(28, 27, 30), dp(10), Color.rgb(43, 41, 46))
                    isClickable = true; isFocusable = true
                    setOnClickListener { selectedArtist = first.artist; catalogMode = "artist"; renderCatalogTracks() }
                }
                val cover = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    background = roundedSurface(accentColor, dp(30), accentColor)
                    contentDescription = "Imagen de ${first.artist}"
                }
                row.addView(cover, LinearLayout.LayoutParams(dp(56), dp(56)).apply { rightMargin = dp(12) })
                if (first.coverUrl.isNotBlank()) loadCoverInto(first.coverUrl, cover)
                val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
                labels.addView(TextView(this).apply {
                    text = first.artist; textSize = 14f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
                    maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                })
                labels.addView(TextView(this).apply {
                    text = "Artista · ${songs.size} canción(es)"; textSize = 11f; setTextColor(mutedColor)
                })
                row.addView(labels, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(TextView(this).apply { text = "›"; textSize = 27f; setTextColor(mutedColor) }, centeredSize(dp(24), dp(42)))
                catalogContainer.addView(row, fullWidth().apply { bottomMargin = dp(7) })
            }
            return
        }
        val modeLabel = when (catalogMode) {
            "favorites" -> "Favoritos sincronizados"
            "playlist" -> "Playlist: ${userPlaylists.firstOrNull { it.optString("id") == activePlaylistId }?.optString("name", "") ?: "Mis canciones"}"
            "artist" -> "Artista: $selectedArtist"
            else -> "Biblioteca central de AzuraCast"
        }
        catalogSummary.text = if (catalogQuery.isBlank()) "${filtered.size} canciones · $modeLabel" else "${filtered.size} resultado(s) para «${catalogQuery}» · $modeLabel"
        if (filtered.isEmpty()) {
            val emptyText = when {
                catalogMode == "favorites" -> "Aún no guardaste favoritos. Pulsa ♡ en una canción para guardarla en tu cuenta."
                catalogMode == "playlist" -> "Esta playlist está vacía. Usa ＋ en una canción para añadirla."
                catalogQuery.isNotBlank() -> "No encontramos canciones. Prueba con otro título, artista o álbum."
                catalogTracks.isEmpty() -> "Inicia sesión o actualiza la biblioteca para cargar las canciones de AfroGo."
                else -> "No hay canciones en esta vista todavía."
            }
            renderCatalogMessage(emptyText)
            return
        }
        filtered.take(150).forEach { track ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(8), dp(7), dp(8))
                background = roundedSurface(Color.rgb(28, 27, 30), dp(10), Color.rgb(43, 41, 46))
                isClickable = true; isFocusable = true
                setOnClickListener { playTrack(track) }
            }
            val cover = ImageView(this).apply {
                contentDescription = "Portada de ${track.title}"
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = roundedSurface(accentColor, dp(7), accentColor)
                tag = track.coverUrl
            }
            row.addView(cover, LinearLayout.LayoutParams(dp(58), dp(58)).apply { rightMargin = dp(10) })
            if (track.coverUrl.isNotBlank()) loadCoverInto(track.coverUrl, cover)
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
            info.addView(TextView(this).apply {
                text = track.title; textSize = 14f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            })
            info.addView(TextView(this).apply {
                text = "${track.artist} · ${track.album.ifBlank { "Sencillo" }}"; textSize = 11f
                setTextColor(mutedColor); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dp(3), 0, dp(3))
            })
            info.addView(TextView(this).apply {
                text = track.genre.ifBlank { "AfroGo FM" }; textSize = 10f
                setTextColor(Color.rgb(202, 174, 223)); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            })
            row.addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(button("▶", true) { playTrack(track) }, centeredSize(dp(38), dp(42)).apply { leftMargin = dp(4) })
            val cloudId = cloudTrackId(track)
            row.addView(button(if (favoriteTrackIds.contains(cloudId)) "♥" else "♡", false) { toggleFavorite(track) }, centeredSize(dp(36), dp(42)).apply { leftMargin = dp(2) })
            row.addView(button("＋", false) { addTrackToPlaylist(track) }, centeredSize(dp(36), dp(42)).apply { leftMargin = dp(2) })
            catalogContainer.addView(row, fullWidth().apply { bottomMargin = dp(7) })
        }
        if (filtered.size > 150) {
            catalogContainer.addView(TextView(this).apply {
                text = "Mostrando 150 de ${filtered.size} resultados. Escribe en el buscador para filtrar la biblioteca."
                textSize = 12f; setTextColor(mutedColor); setPadding(dp(4), dp(5), dp(4), dp(10))
            }, fullWidth())
        }
    }

    private fun cloudTrackId(track: AfrogoTrack): String = "azura:${track.id}"

    private fun toggleFavorite(track: AfrogoTrack) {
        val cloudId = cloudTrackId(track)
        val active = !favoriteTrackIds.contains(cloudId)
        if (active) favoriteTrackIds.add(cloudId) else favoriteTrackIds.remove(cloudId)
        renderCatalogTracks()
        val token = AfrogoApi.savedToken(this)
        Thread {
            try { AfrogoApi.setFavorite(token, cloudId, active) }
            catch (e: Exception) {
                runOnUiThread {
                    if (active) favoriteTrackIds.remove(cloudId) else favoriteTrackIds.add(cloudId)
                    renderCatalogTracks()
                    authStatus.text = "No se pudo sincronizar el favorito: ${e.message ?: "Error de conexión"}"
                }
            }
        }.start()
    }

    private fun addTrackToPlaylist(track: AfrogoTrack) {
        if (userPlaylists.isEmpty()) { createCloudPlaylist(track); return }
        val names = userPlaylists.map { it.optString("name", "Playlist") }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Añadir a playlist")
            .setItems(names) { _, which ->
                val playlist = userPlaylists.getOrNull(which) ?: return@setItems
                val songIds = playlist.optJSONArray("songIds") ?: JSONArray()
                val id = cloudTrackId(track)
                val already = (0 until songIds.length()).any { songIds.optString(it) == id }
                if (!already) songIds.put(id)
                playlist.put("songIds", songIds)
                persistPlaylist(playlist, "Canción añadida a ${playlist.optString("name")}")
            }
            .setNeutralButton("＋ Crear playlist") { _, _ -> createCloudPlaylist(track) }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun showPlaylistsDialog() = showPlaylistsScreen()

    private fun showPlaylistsScreen() {
        val content = prepareScreen("playlists")
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(button("‹", false) { showLibrary() }, centeredSize(dp(42), dp(42)))
        header.addView(TextView(this).apply {
            text = "Playlists"; textSize = 25f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(8) })
        header.addView(button("＋", true) { createCloudPlaylist(null) }, centeredSize(dp(44), dp(44)))
        content.addView(header, fullWidth().apply { bottomMargin = dp(8) })
        content.addView(TextView(this).apply {
            text = "Tus listas con portada y canciones"; textSize = 12f; setTextColor(mutedColor)
        }, fullWidth().apply { bottomMargin = dp(18) })
        if (userPlaylists.isEmpty()) {
            content.addView(coloredFeatureCard("Aún no tienes playlists", "Crea una lista y añade canciones de la biblioteca AfroGo.", "Crear playlist") { createCloudPlaylist(null) }, fullWidth())
            return
        }
        userPlaylists.forEach { playlist ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = roundedSurface(Color.rgb(30, 28, 33), dp(12), Color.rgb(49, 44, 54))
                isClickable = true; isFocusable = true
                setOnClickListener {
                    catalogMode = "playlist"; activePlaylistId = playlist.optString("id")
                    catalogSearchInput.setText(""); showLibrary()
                }
            }
            val cover = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(accentColor, Color.rgb(42, 18, 58))).apply { cornerRadius = dp(8).toFloat() }
                contentDescription = "Portada de ${playlist.optString("name", "Playlist")}"
            }
            row.addView(cover, LinearLayout.LayoutParams(dp(76), dp(76)).apply { rightMargin = dp(12) })
            val coverUrl = playlist.optString("cover", "")
            if (coverUrl.isNotBlank()) loadCoverInto(coverUrl, cover) else cover.setImageResource(com.afrogo.fm.R.drawable.afrogo_icon_foreground)
            val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
            labels.addView(TextView(this).apply {
                text = playlist.optString("name", "Playlist"); textSize = 15f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
            })
            labels.addView(TextView(this).apply {
                text = "${playlist.optJSONArray("songIds")?.length() ?: 0} canciones"; textSize = 12f; setTextColor(mutedColor); setPadding(0, dp(4), 0, 0)
            })
            row.addView(labels, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(this).apply { text = "›"; textSize = 28f; setTextColor(Color.WHITE) }, centeredSize(dp(24), dp(42)))
            content.addView(row, fullWidth().apply { bottomMargin = dp(9) })
        }
    }

    private fun createCloudPlaylist(track: AfrogoTrack?) {
        val nameInput = EditText(this).apply { hint = "Nombre de la playlist"; setSingleLine(true); setPadding(dp(16), dp(10), dp(16), dp(10)) }
        AlertDialog.Builder(this).setTitle("Crear playlist en la nube").setMessage("Se sincronizará con tus otros dispositivos.")
            .setView(nameInput)
            .setPositiveButton("Crear") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isBlank()) { authStatus.text = "Escribe un nombre para la playlist."; return@setPositiveButton }
                val songIds = JSONArray()
                if (track != null) songIds.put(cloudTrackId(track))
                val playlist = JSONObject().put("id", "pl_" + java.util.UUID.randomUUID().toString().replace("-", ""))
                    .put("name", name.take(60)).put("description", "").put("songIds", songIds).put("cover", "")
                persistPlaylist(playlist, "Playlist creada y sincronizada")
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun persistPlaylist(playlist: JSONObject, successMessage: String) {
        val token = AfrogoApi.savedToken(this)
        Thread {
            try {
                val saved = AfrogoApi.upsertPlaylist(token, playlist).optJSONObject("playlist") ?: playlist
                runOnUiThread {
                    val index = userPlaylists.indexOfFirst { it.optString("id") == saved.optString("id") }
                    if (index >= 0) userPlaylists[index] = saved else userPlaylists.add(saved)
                    catalogMode = "all"; activePlaylistId = ""; renderCatalogTracks()
                    authStatus.text = successMessage
                }
            } catch (e: Exception) {
                runOnUiThread { authStatus.text = "No se pudo sincronizar la playlist: ${e.message ?: "Error de conexión"}" }
            }
        }.start()
    }

    private fun normalizeSearch(value: String): String = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").lowercase(java.util.Locale.ROOT).trim()

    private fun loadCoverInto(url: String, image: ImageView) {
        if (url.isBlank()) return
        coverCache.get(url)?.let { image.setImageBitmap(it); return }
        image.tag = url
        coverExecutor.execute {
            var bitmap: Bitmap? = null
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 7000; readTimeout = 9000; instanceFollowRedirects = true }
                conn.inputStream.use { stream ->
                    val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565; inSampleSize = 2 }
                    bitmap = BitmapFactory.decodeStream(stream, null, options)
                }
            } catch (_: Exception) { }
            finally { conn?.disconnect() }
            val loaded = bitmap
            if (loaded != null) {
                coverCache.put(url, loaded)
                runOnUiThread {
                    if (image.tag == url) {
                        image.setImageBitmap(loaded)
                        val sample = Bitmap.createScaledBitmap(loaded, 1, 1, true)
                        val pixel = sample.getPixel(0, 0)
                        val r = (Color.red(pixel) * 0.52f).toInt().coerceIn(10, 100)
                        val g = (Color.green(pixel) * 0.52f).toInt().coerceIn(10, 90)
                        val b = (Color.blue(pixel) * 0.58f).toInt().coerceIn(15, 115)
                        val dynamicColor = Color.rgb(r, g, b)
                        if (::miniPlayerCover.isInitialized && image === miniPlayerCover) {
                            miniPlayer.background = android.graphics.drawable.ColorDrawable(dynamicColor)
                        }
                        if (image.contentDescription?.toString() == "Portada de la reproducción actual") {
                            (image.parent as? View)?.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                                intArrayOf(dynamicColor, darken(dynamicColor), bgColor))
                        }
                        sample.recycle()
                    }
                }
            }
        }
    }

    private fun renderCatalogMessage(message: String) {
        if (!::catalogContainer.isInitialized) return
        catalogContainer.removeAllViews()
        if (::catalogSummary.isInitialized) catalogSummary.text = message
        catalogContainer.addView(TextView(this).apply {
            text = message; textSize = 13f; setTextColor(mutedColor)
            gravity = Gravity.CENTER; setPadding(dp(15), dp(20), dp(15), dp(22))
            background = roundedSurface(Color.rgb(27, 27, 30), dp(12), Color.rgb(40, 39, 43))
        }, fullWidth().apply { bottomMargin = dp(12) })
    }

    private fun playTrack(track: AfrogoTrack) {
        fun toMediaItem(song: AfrogoTrack): MediaItem {
            val metadata = MediaMetadata.Builder()
                .setTitle(song.title).setArtist(song.artist).setAlbumTitle(song.album.ifBlank { "Sencillo" })
                .setSubtitle(song.genre).setIsPlayable(true)
            if (song.coverUrl.isNotBlank()) metadata.setArtworkUri(android.net.Uri.parse(song.coverUrl))
            return MediaItem.Builder().setMediaId("azuracast:${song.id}")
                .setUri(song.playUrl).setMediaMetadata(metadata.build()).build()
        }
        val item = toMediaItem(track)
        val player = controller
        if (player == null) {
            pendingMediaItem = item
            playbackStatus.text = "Conectando con el reproductor…"
            connectMediaController()
            return
        }
        // Keep the catalog as a real playback queue so Next/Previous also work in Android Auto.
        val queue = (catalogTracks.ifEmpty { listOf(track) }).map { toMediaItem(it) }
        val selectedIndex = catalogTracks.indexOfFirst { it.id == track.id }.let { if (it < 0) 0 else it }
        player.setMediaItems(queue, selectedIndex.coerceIn(0, queue.lastIndex), 0L)
        player.prepare(); player.play()
        playbackStatus.text = "Reproduciendo: ${track.title} · ${track.artist}"
        val token = AfrogoApi.savedToken(this)
        val recent = JSONObject().put("id", cloudTrackId(track)).put("title", track.title).put("artist", track.artist)
            .put("album", track.album).put("genre", track.genre).put("cover", track.coverUrl).put("playedAt", System.currentTimeMillis())
        Thread { try { AfrogoApi.addRecent(token, recent) } catch (_: Exception) { } }.start()
    }

    private fun connectMediaController() {
        if (controller != null) {
            pendingMediaItem?.let { item -> controller?.setMediaItem(item); controller?.prepare(); controller?.play(); pendingMediaItem = null }
            return
        }
        val token = SessionToken(this, ComponentName(this, AfrogoMediaService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                controller = future.get()
                controller?.addListener(object : Player.Listener {
                    // Refresh controls for both actual playback changes and play/pause requests.
                    override fun onIsPlayingChanged(isPlaying: Boolean) = updatePlaybackUi()
                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = updatePlaybackUi()
                    override fun onPlaybackStateChanged(playbackState: Int) = updatePlaybackUi()
                    // Track changes in a queue and live-stream metadata updates must refresh title/cover.
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = updatePlaybackUi()
                    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = updatePlaybackUi()
                    override fun onEvents(player: Player, events: Player.Events) {
                        if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) ||
                            events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) ||
                            events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                            events.contains(Player.EVENT_MEDIA_METADATA_CHANGED) ||
                            events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                            updatePlaybackUi()
                        }
                    }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        playbackStatus.text = "No se pudo reproducir. Comprueba la URL de escucha de AzuraCast."
                    }
                })
                pendingMediaItem?.let { item -> controller?.setMediaItem(item); controller?.prepare(); controller?.play(); pendingMediaItem = null }
                updatePlaybackUi()
            } catch (e: Exception) {
                playbackStatus.text = "No se pudo iniciar el reproductor. Vuelve a intentarlo."
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun toggleRadio() {
        val player = controller
        if (player == null) { playbackStatus.text = "Conectando con el reproductor…"; connectMediaController(); return }
        val isLiveStream = player.currentMediaItem?.mediaId == "afrogo_live_stream"
        if (isLiveStream && (player.isPlaying || player.playWhenReady)) {
            player.pause()
        } else {
            val metadata = MediaMetadata.Builder().setTitle(AzuraConfig.STATION_NAME).setArtist("Radio en vivo")
                .setAlbumTitle("AfroGo FM").setIsPlayable(true).build()
            player.setMediaItem(MediaItem.Builder().setMediaId("afrogo_live_stream")
                .setUri(AzuraConfig.STREAM_URL).setMediaMetadata(metadata).build())
            player.prepare(); player.play()
        }
        updatePlaybackUi()
    }

    private fun updatePlaybackUi() {
        updatePlayerProgressUi()
        val currentItem = controller?.currentMediaItem
        val current = currentItem?.mediaMetadata
        val playing = controller?.isPlaying == true
        radioButton.text = if (playing && currentItem?.mediaId == "afrogo_live_stream") "Ⅱ   Pausar AfroGo FM" else "▶   Escuchar AfroGo FM en vivo"
        if (playing && current != null && currentItem?.mediaId != "afrogo_live_stream") {
            playbackStatus.text = "Reproduciendo: ${current.title ?: "Canción"} · ${current.artist ?: "Artista"}"
        } else if (playing) playbackStatus.text = "Reproduciendo · AfroGo FM en vivo"
        else playbackStatus.text = "Pausado · Pulsa para continuar la música"

        if (::miniPlayer.isInitialized) {
            if (currentItem == null) {
                miniPlayer.visibility = View.GONE
            } else {
                // The landscape layout has its own player panel on the left.
                // Never show the bottom mini-player strip in landscape mode.
                miniPlayer.visibility = if (
                    resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                ) View.GONE else View.VISIBLE
                miniPlayerTitle.text = current?.title?.toString() ?: "AfroGo FM"
                miniPlayerArtist.text = current?.artist?.toString() ?: "Radio en vivo"
                miniPlayerPlayButton.text = if (playing) "Ⅱ" else "▶"
                landscapePlayerTitle?.text = current?.title?.toString() ?: "AfroGo FM"
                landscapePlayerArtist?.text = current?.artist?.toString() ?: "Radio en vivo"
                val toggleLabel = if (playing || controller?.playWhenReady == true) "Ⅱ" else "▶"
                landscapePlayerToggle?.text = toggleLabel
                playerScreenToggle?.text = toggleLabel
                playerScreenTitle?.text = current?.title?.toString()?.takeIf { it.isNotBlank() } ?: "AfroGo FM"
                playerScreenArtist?.text = current?.artist?.toString()?.takeIf { it.isNotBlank() } ?: "Radio oficial · Música en vivo"
                val coverUrl = current?.artworkUri?.toString().orEmpty()
                val screenCover = playerScreenCover
                if (screenCover != null) {
                    if (coverUrl.isNotBlank() && screenCover.tag != coverUrl) {
                        screenCover.tag = coverUrl
                        screenCover.setImageDrawable(null)
                        loadCoverInto(coverUrl, screenCover)
                    } else if (coverUrl.isBlank() && screenCover.tag != "afrogo-default") {
                        screenCover.tag = "afrogo-default"
                        screenCover.setImageResource(com.afrogo.fm.R.drawable.afrogo_icon_foreground)
                    }
                }
                val landscapeCover = landscapePlayerCover
                if (landscapeCover != null && coverUrl.isNotBlank() && landscapeCover.tag != coverUrl) {
                    landscapeCover.tag = coverUrl
                    landscapeCover.setImageDrawable(null)
                    loadCoverInto(coverUrl, landscapeCover)
                } else if (landscapeCover != null && coverUrl.isBlank()) {
                    landscapeCover.tag = "afrogo-default"
                    landscapeCover.setImageResource(com.afrogo.fm.R.drawable.afrogo_icon_foreground)
                }
                if (coverUrl.isNotBlank()) {
                    if (miniPlayerCover.tag != coverUrl) {
                        miniPlayerCover.tag = coverUrl
                        miniPlayerCover.setImageDrawable(null)
                        loadCoverInto(coverUrl, miniPlayerCover)
                    }
                } else {
                    miniPlayerCover.tag = "afrogo-default"
                    miniPlayerCover.setImageResource(com.afrogo.fm.R.drawable.afrogo_icon_foreground)
                    miniPlayer.background = android.graphics.drawable.ColorDrawable(Color.rgb(48, 29, 62))
                }
            }
        }
    }

    private fun input(hintText: String, multiline: Boolean) = EditText(this).apply {
        hint = hintText; textSize = 14f; setHintTextColor(Color.rgb(142, 137, 151)); setTextColor(Color.WHITE)
        setPadding(dp(15), dp(12), dp(15), dp(12))
        background = roundedSurface(Color.rgb(30, 29, 33), dp(10), Color.rgb(62, 59, 67))
        if (multiline) minLines = 2
    }

    private fun button(label: String, primary: Boolean, click: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 13f; setTextColor(Color.WHITE)
        minHeight = dp(40); minimumHeight = dp(40); minWidth = dp(0); minimumWidth = dp(0)
        setPadding(dp(11), dp(4), dp(11), dp(4))
        stateListAnimator = null
        elevation = 0f
        background = GradientDrawable().apply {
            cornerRadius = dp(22).toFloat()
            setColor(if (primary) accentColor else Color.rgb(42, 41, 45))
            if (!primary) setStroke(dp(1), Color.rgb(67, 64, 72))
        }
        setOnClickListener { click() }
    }

    private fun fullWidth() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    private fun centeredSize(w: Int, h: Int) = LinearLayout.LayoutParams(w, h).apply { gravity = Gravity.CENTER }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        playbackProgressHandler.removeCallbacks(playbackProgressTicker)
        scheduledCatalogFilter?.let { searchHandler.removeCallbacks(it) }
        coverExecutor.shutdownNow()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null; controllerFuture = null
        super.onDestroy()
    }
}
