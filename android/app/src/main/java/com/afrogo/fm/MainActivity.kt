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
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.ScrollView
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(22, 13, 29)
        window.navigationBarColor = Color.rgb(22, 13, 29)

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(30))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(42, 12, 63), Color.rgb(18, 14, 21), Color.rgb(12, 10, 15)))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(15, 11, 18))
        }
        searchBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(7), dp(10), dp(7))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(248, 34, 23, 43), Color.argb(222, 23, 16, 30))).apply {
                cornerRadius = dp(26).toFloat()
                setStroke(dp(1), Color.argb(150, 120, 65, 150))
            }
            elevation = dp(12).toFloat()
            visibility = View.GONE
        }
        searchBar.addView(TextView(this).apply {
            text = "⌕"; textSize = 29f; gravity = Gravity.CENTER
            setTextColor(Color.rgb(220, 190, 241))
        }, centeredSize(dp(38), dp(44)))
        catalogSearchInput = EditText(this).apply {
            hint = "Buscar canción, artista o álbum…"
            textSize = 15f; isSingleLine = true
            setHintTextColor(Color.rgb(172, 156, 184)); setTextColor(Color.WHITE)
            setPadding(dp(6), dp(8), dp(4), dp(8))
            background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        }
        searchBar.addView(catalogSearchInput, LinearLayout.LayoutParams(0, dp(46), 1f))
        searchClearButton = button("×", false) {
            catalogSearchInput.setText("")
            catalogSearchInput.clearFocus()
        }.apply { visibility = View.GONE; minWidth = dp(38) }
        searchBar.addView(searchClearButton, centeredSize(dp(42), dp(42)))
        root.addView(searchBar, fullWidth().apply { leftMargin = dp(12); rightMargin = dp(12); topMargin = dp(4); bottomMargin = dp(7) })

        val scroll = ScrollView(this).apply { isFillViewport = true; addView(page) }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        page.addView(TextView(this).apply {
            text = "♫"; textSize = 44f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(120, 2, 210), Color.rgb(48, 11, 69))).apply { cornerRadius = dp(24).toFloat() }
            elevation = dp(8).toFloat()
        }, centeredSize(dp(94), dp(94)).apply { bottomMargin = dp(18) })

        page.addView(TextView(this).apply {
            text = "AFROGO FM"; textSize = 28f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD); letterSpacing = 0.06f
        }, fullWidth().apply { bottomMargin = dp(6) })
        page.addView(TextView(this).apply {
            text = "Tu música, tu ritmo."; textSize = 15f; setTextColor(Color.rgb(215, 194, 229)); gravity = Gravity.CENTER
        }, fullWidth().apply { bottomMargin = dp(20) })

        addHeading(page, "Tu cuenta Afrogo")
        nameInput = input("Nombre para mostrar (solo para solicitar acceso)", false)
        emailInput = input("Correo electrónico", false).apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS }
        passwordInput = input("Contraseña (mínimo 10 caracteres)", false).apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        page.addView(nameInput, fullWidth().apply { bottomMargin = dp(8) })
        page.addView(emailInput, fullWidth().apply { bottomMargin = dp(8) })
        page.addView(passwordInput, fullWidth().apply { bottomMargin = dp(10) })

        signInButton = button("Iniciar sesión", true) { authenticate(register = false) }
        registerButton = button("Solicitar acceso", false) { authenticate(register = true) }
        logoutButton = button("Cerrar sesión", false) { logout() }.apply { visibility = View.GONE }
        page.addView(signInButton, fullWidth().apply { bottomMargin = dp(8) })
        page.addView(registerButton, fullWidth().apply { bottomMargin = dp(8) })
        page.addView(logoutButton, fullWidth().apply { bottomMargin = dp(8) })
        authStatus = TextView(this).apply {
            text = "El acceso a la biblioteca requiere una cuenta aprobada y un gateway HTTPS configurado."
            textSize = 13f; setTextColor(Color.rgb(190, 177, 200)); gravity = Gravity.CENTER
        }
        page.addView(authStatus, fullWidth().apply { bottomMargin = dp(20) })

        radioButton = button("▶  Escuchar Afrogo FM en vivo", true) { toggleRadio() }
        page.addView(radioButton, fullWidth().apply { height = dp(52); bottomMargin = dp(8) })
        playbackStatus = TextView(this).apply {
            text = "La radio en vivo se puede escuchar sin iniciar sesión."; textSize = 13f
            setTextColor(Color.rgb(160, 150, 170)); gravity = Gravity.CENTER
        }
        page.addView(playbackStatus, fullWidth().apply { bottomMargin = dp(22) })

        val catalogHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        catalogHeader.addView(TextView(this).apply {
            text = "Biblioteca AzuraCast"; textSize = 20f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        catalogHeader.addView(button("Actualizar", false) { loadCatalog() })
        page.addView(catalogHeader, fullWidth().apply { bottomMargin = dp(8) })
        val libraryActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        libraryActions.addView(button("Toda la música", false) { catalogMode = "all"; activePlaylistId = ""; renderCatalogTracks() }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { rightMargin = dp(5) })
        libraryActions.addView(button("Favoritos", false) { catalogMode = "favorites"; activePlaylistId = ""; renderCatalogTracks() }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { rightMargin = dp(5) })
        libraryActions.addView(button("Mis playlists", false) { showPlaylistsDialog() }, LinearLayout.LayoutParams(0, dp(42), 1f))
        page.addView(libraryActions, fullWidth().apply { bottomMargin = dp(8) })
        catalogSummary = TextView(this).apply {
            text = "Inicia sesión para explorar las canciones."
            textSize = 12f; setTextColor(Color.rgb(177, 159, 190)); setPadding(dp(4), dp(4), dp(4), dp(8))
        }
        page.addView(catalogSummary, fullWidth())
        catalogContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(catalogContainer, fullWidth())
        setContentView(root)

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
                            // A timeout or server outage must not log the user out. Keep the saved token.
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

    private fun setAuthenticatedUi(signedIn: Boolean) {
        val formVisibility = if (signedIn) View.GONE else View.VISIBLE
        nameInput.visibility = formVisibility
        emailInput.visibility = formVisibility
        passwordInput.visibility = formVisibility
        signInButton.visibility = formVisibility
        registerButton.visibility = formVisibility
        logoutButton.visibility = if (signedIn) View.VISIBLE else View.GONE
    }

    private fun logout() {
        AfrogoApi.clearToken(this)
        controller?.stop()
        controller?.clearMediaItems()
        catalogTracks = emptyList()
        favoriteTrackIds.clear()
        userPlaylists.clear()
        catalogMode = "all"
        activePlaylistId = ""
        authStatus.text = "Has cerrado sesión. Tus datos en la nube se conservan en tu cuenta."
        playbackStatus.text = "Sesión cerrada."
        setAuthenticatedUi(false)
        catalogSummary.text = "Inicia sesión para explorar las canciones."
        renderCatalogMessage("Inicia sesión con una cuenta aprobada para acceder a la biblioteca.")
        radioButton.text = "▶  Escuchar Afrogo FM en vivo"
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
                    renderCatalogTracks()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    authStatus.text = e.message ?: "No se pudo cargar el catálogo."
                    catalogSummary.text = "No se pudo actualizar la biblioteca."
                    renderCatalogMessage("No se pudo cargar el catálogo de AzuraCast.")
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
            val cloudId = "azura:${track.id}"
            val modeMatches = when (catalogMode) {
                "favorites" -> favoriteTrackIds.contains(cloudId)
                "playlist" -> playlistIds.contains(cloudId)
                else -> true
            }
            modeMatches && (normalizedQuery.isBlank() || normalizeSearch("${track.title} ${track.artist} ${track.album} ${track.genre}").contains(normalizedQuery))
        }
        val modeLabel = when (catalogMode) {
            "favorites" -> "Favoritos sincronizados"
            "playlist" -> "Playlist: ${userPlaylists.firstOrNull { it.optString("id") == activePlaylistId }?.optString("name", "") ?: "Mis canciones"}"
            else -> "Biblioteca central de AzuraCast"
        }
        catalogSummary.text = if (catalogQuery.isBlank()) "${filtered.size} canciones · $modeLabel" else "${filtered.size} resultado(s) para «${catalogQuery}» · $modeLabel"
        if (filtered.isEmpty()) {
            val emptyText = when {
                catalogMode == "favorites" -> "Aún no guardaste favoritos en tu cuenta. Pulsa ♡ en una canción para guardarla en la nube."
                catalogMode == "playlist" -> "Esta playlist está vacía. Usa ＋ en una canción para añadirla."
                catalogQuery.isNotBlank() -> "No encontramos canciones. Prueba con otro título, artista o álbum."
                else -> "El servidor no devolvió canciones. Comprueba la estación y los permisos de AzuraCast."
            }
            renderCatalogMessage(emptyText)
            return
        }
        filtered.take(150).forEach { track ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(10), dp(9), dp(10))
                background = GradientDrawable().apply {
                    setColor(Color.rgb(27, 20, 34)); cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), Color.rgb(48, 36, 57))
                }
                isClickable = true
                isFocusable = true
                setOnClickListener { playTrack(track) }
            }
            val cover = ImageView(this).apply {
                contentDescription = "Portada de ${track.title}"
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.rgb(120, 2, 210), Color.rgb(39, 20, 49))).apply { cornerRadius = dp(8).toFloat() }
                tag = track.coverUrl
            }
            row.addView(cover, LinearLayout.LayoutParams(dp(66), dp(66)).apply { rightMargin = dp(12) })
            if (track.coverUrl.isNotBlank()) loadCoverInto(track.coverUrl, cover)
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
            info.addView(TextView(this).apply {
                text = track.title; textSize = 15f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            })
            info.addView(TextView(this).apply {
                text = "${track.artist} · ${track.album.ifBlank { "Sencillo" }}"; textSize = 12f
                setTextColor(Color.rgb(185, 171, 197)); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dp(4), 0, dp(6))
            })
            val badges = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            badges.addView(TextView(this).apply {
                text = if (track.album.isBlank()) "CANCIÓN" else "ÁLBUM"
                textSize = 9f; setTextColor(Color.rgb(238, 222, 248)); setPadding(dp(6), dp(3), dp(6), dp(3))
                background = GradientDrawable().apply { setColor(Color.rgb(64, 48, 76)); cornerRadius = dp(5).toFloat() }
            })
            badges.addView(TextView(this).apply {
                text = track.genre.ifBlank { "Sin categoría" }; textSize = 9f; setTextColor(Color.rgb(226, 190, 250))
                setPadding(dp(6), dp(3), dp(6), dp(3))
                background = GradientDrawable().apply { setColor(Color.rgb(58, 32, 73)); cornerRadius = dp(5).toFloat() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(6) })
            info.addView(badges)
            row.addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(button("▶", true) { playTrack(track) }, centeredSize(dp(42), dp(44)).apply { leftMargin = dp(5) })
            val cloudId = "azura:${track.id}"
            row.addView(button(if (favoriteTrackIds.contains(cloudId)) "♥" else "♡", false) { toggleFavorite(track) }, centeredSize(dp(38), dp(44)).apply { leftMargin = dp(2) })
            row.addView(button("＋", false) { addTrackToPlaylist(track) }, centeredSize(dp(38), dp(44)).apply { leftMargin = dp(2) })
            catalogContainer.addView(row, fullWidth().apply { bottomMargin = dp(9) })
        }
        if (filtered.size > 150) {
            catalogContainer.addView(TextView(this).apply {
                text = "Mostrando 150 de ${filtered.size} resultados. Escribe en el buscador para filtrar la biblioteca."; textSize = 12f
                setTextColor(Color.rgb(180, 166, 192)); setPadding(dp(4), dp(5), dp(4), dp(10))
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

    private fun showPlaylistsDialog() {
        if (userPlaylists.isEmpty()) { createCloudPlaylist(null); return }
        val names = userPlaylists.map { "${it.optString("name", "Playlist")} · ${(it.optJSONArray("songIds")?.length() ?: 0)} canciones" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Tus playlists en la nube")
            .setItems(names) { _, which ->
                val playlist = userPlaylists.getOrNull(which) ?: return@setItems
                catalogMode = "playlist"; activePlaylistId = playlist.optString("id"); catalogSearchInput.setText(""); renderCatalogTracks()
            }
            .setNeutralButton("＋ Crear playlist") { _, _ -> createCloudPlaylist(null) }
            .setNegativeButton("Cancelar", null).show()
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
                runOnUiThread { if (image.tag == url) image.setImageBitmap(loaded) }
            }
        }
    }

    private fun renderCatalogMessage(message: String) {
        catalogContainer.removeAllViews()
        if (::catalogSummary.isInitialized) catalogSummary.text = message
        catalogContainer.addView(TextView(this).apply {
            text = message; textSize = 13f; setTextColor(Color.rgb(190, 177, 200)); setPadding(dp(5), dp(15), dp(5), dp(15))
        }, fullWidth())
    }

    private fun playTrack(track: AfrogoTrack) {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title).setArtist(track.artist).setAlbumTitle(track.album.ifBlank { "Sencillo" })
            .setSubtitle(track.genre).setIsPlayable(true)
        if (track.coverUrl.isNotBlank()) metadata.setArtworkUri(android.net.Uri.parse(track.coverUrl))
        val item = MediaItem.Builder().setMediaId("azuracast:${track.id}")
            .setUri(track.playUrl).setMediaMetadata(metadata.build()).build()
        val player = controller
        if (player == null) { pendingMediaItem = item; playbackStatus.text = "Conectando con el reproductor…"; connectMediaController(); return }
        player.setMediaItem(item); player.prepare(); player.play()
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
                    override fun onIsPlayingChanged(isPlaying: Boolean) = updatePlaybackUi()
                    override fun onPlaybackStateChanged(playbackState: Int) = updatePlaybackUi()
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
        if (player.isPlaying || player.playWhenReady) player.pause() else {
            val metadata = MediaMetadata.Builder().setTitle(AzuraConfig.STATION_NAME).setArtist("Radio en vivo")
                .setAlbumTitle("Afrogo FM").setIsPlayable(true).build()
            player.setMediaItem(MediaItem.Builder().setMediaId("afrogo_live_stream")
                .setUri(AzuraConfig.STREAM_URL).setMediaMetadata(metadata).build())
            player.prepare(); player.play()
        }
        updatePlaybackUi()
    }

    private fun updatePlaybackUi() {
        val current = controller?.currentMediaItem?.mediaMetadata
        val playing = controller?.isPlaying == true
        radioButton.text = if (playing && controller?.currentMediaItem?.mediaId == "afrogo_live_stream") "Ⅱ  Pausar Afrogo FM" else "▶  Escuchar Afrogo FM en vivo"
        if (playing && current != null && controller?.currentMediaItem?.mediaId != "afrogo_live_stream") {
            playbackStatus.text = "Reproduciendo: ${current.title ?: "Canción"} · ${current.artist ?: "Artista"}"
        } else if (playing) playbackStatus.text = "Reproduciendo · Afrogo FM en vivo"
        else playbackStatus.text = "Pausado · Pulsa para continuar la música"
    }

    private fun addHeading(root: LinearLayout, title: String) {
        root.addView(TextView(this).apply { text = title; textSize = 18f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD) }, fullWidth().apply { bottomMargin = dp(8) })
    }

    private fun input(hintText: String, multiline: Boolean) = EditText(this).apply {
        hint = hintText; textSize = 14f; setHintTextColor(Color.rgb(155, 143, 166)); setTextColor(Color.WHITE)
        setPadding(dp(14), dp(10), dp(14), dp(10))
        background = GradientDrawable().apply { setColor(Color.rgb(25, 19, 31)); cornerRadius = dp(10).toFloat(); setStroke(dp(1), Color.rgb(67, 47, 81)) }
        if (multiline) minLines = 2
    }

    private fun button(label: String, primary: Boolean, click: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 14f; setTextColor(Color.WHITE)
        background = GradientDrawable().apply { cornerRadius = dp(22).toFloat(); setColor(if (primary) Color.rgb(120, 2, 210) else Color.rgb(45, 34, 52)) }
        setOnClickListener { click() }
    }

    private fun fullWidth() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    private fun centeredSize(w: Int, h: Int) = LinearLayout.LayoutParams(w, h).apply { gravity = Gravity.CENTER }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        scheduledCatalogFilter?.let { searchHandler.removeCallbacks(it) }
        coverExecutor.shutdownNow()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null; controllerFuture = null
        super.onDestroy()
    }
}
