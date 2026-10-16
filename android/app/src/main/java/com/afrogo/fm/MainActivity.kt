package com.afrogo.fm

import android.app.Activity
import android.content.ComponentName
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
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
    private lateinit var radioButton: Button
    private lateinit var signInButton: Button
    private lateinit var registerButton: Button

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
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(page) }

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
        page.addView(signInButton, fullWidth().apply { bottomMargin = dp(8) })
        page.addView(registerButton, fullWidth().apply { bottomMargin = dp(8) })
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
        catalogContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(catalogContainer, fullWidth())
        setContentView(scroll)

        connectMediaController()
        val saved = AfrogoApi.savedToken(this)
        if (saved.isNotBlank()) {
            authStatus.text = "Sesión guardada. Verificando acceso…"
            Thread {
                try {
                    val me = AfrogoApi.me(saved).optJSONObject("user")
                    runOnUiThread {
                        authStatus.text = "Sesión activa: ${me?.optString("email", "cuenta Afrogo") ?: "cuenta Afrogo"}"
                        loadCatalog()
                    }
                } catch (e: Exception) {
                    AfrogoApi.clearToken(this)
                    runOnUiThread { authStatus.text = "La sesión venció o no pudo validarse. Inicia sesión de nuevo." }
                }
            }.start()
        } else {
            renderCatalogMessage("Inicia sesión con una cuenta aprobada para acceder a las canciones del servidor.")
        }
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
                    AfrogoApi.saveToken(this, token)
                    runOnUiThread { authStatus.text = "Sesión iniciada. Descargando biblioteca central…"; loadCatalog() }
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
            authStatus.text = "Inicia sesión con una cuenta aprobada para ver la biblioteca."
            renderCatalogMessage("La biblioteca compartida requiere iniciar sesión.")
            return
        }
        renderCatalogMessage("Consultando AzuraCast…")
        Thread {
            try {
                val tracks = AfrogoApi.catalog(token)
                runOnUiThread {
                    catalogContainer.removeAllViews()
                    if (tracks.isEmpty()) {
                        renderCatalogMessage("El servidor no devolvió canciones. Comprueba el ID de la estación y los permisos de AzuraCast.")
                    } else {
                        authStatus.text = "Biblioteca sincronizada: ${tracks.size} canción(es) desde AzuraCast."
                        tracks.take(200).forEach { track ->
                            val row = LinearLayout(this).apply {
                                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                                setPadding(dp(10), dp(9), dp(7), dp(9))
                                background = GradientDrawable().apply { setColor(Color.rgb(27, 20, 34)); cornerRadius = dp(10).toFloat() }
                            }
                            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                            info.addView(TextView(this).apply {
                                text = track.title; textSize = 14f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD); maxLines = 2
                            })
                            info.addView(TextView(this).apply {
                                text = "${track.artist} · ${track.album.ifBlank { track.genre }}"; textSize = 12f
                                setTextColor(Color.rgb(180, 166, 192)); maxLines = 2
                            })
                            row.addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                            row.addView(button("▶", true) { playTrack(track) })
                            catalogContainer.addView(row, fullWidth().apply { bottomMargin = dp(7) })
                        }
                        if (tracks.size > 200) {
                            catalogContainer.addView(TextView(this).apply {
                                text = "Se muestran las primeras 200 canciones. Android Auto tendrá la navegación por álbumes."; textSize = 12f
                                setTextColor(Color.rgb(180, 166, 192))
                            }, fullWidth())
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    authStatus.text = e.message ?: "No se pudo cargar el catálogo."
                    renderCatalogMessage("No se pudo cargar el catálogo de AzuraCast.")
                }
            }
        }.start()
    }

    private fun renderCatalogMessage(message: String) {
        catalogContainer.removeAllViews()
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
        val current = controller?.currentMediaMetadata
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
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null; controllerFuture = null
        super.onDestroy()
    }
}
