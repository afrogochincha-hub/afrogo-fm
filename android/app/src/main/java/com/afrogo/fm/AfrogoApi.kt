package com.afrogo.fm

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class AfrogoTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val duration: Long,
    val trackNumber: Int,
    val coverUrl: String,
    val playUrl: String
)

class AfrogoApiException(val statusCode: Int, message: String) : IllegalStateException(message)

object AfrogoApi {
    private const val PREFS = "afrogo_auth"
    private const val TOKEN_KEY = "token"
    private const val REFRESH_TOKEN_KEY = "refresh_token"

    fun savedToken(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(TOKEN_KEY, "") ?: ""
    fun savedRefreshToken(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(REFRESH_TOKEN_KEY, "") ?: ""
    fun saveToken(context: Context, token: String) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(TOKEN_KEY, token).apply() }
    fun saveTokens(context: Context, token: String, refreshToken: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(TOKEN_KEY, token).putString(REFRESH_TOKEN_KEY, refreshToken).apply()
    }
    fun clearToken(context: Context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(TOKEN_KEY).remove(REFRESH_TOKEN_KEY).apply() }

    private fun request(path: String, method: String = "GET", body: JSONObject? = null, token: String? = null): JSONObject {
        val base = AzuraConfig.API_BASE_URL
        if (base.isBlank()) throw IllegalStateException("Configura afrogoApiBaseUrl en android/gradle.properties o como parámetro de Gradle.")
        if (!base.startsWith("https://", ignoreCase = true)) throw IllegalStateException("El catálogo y el inicio de sesión requieren un gateway HTTPS. Configura el dominio seguro de la API Afrogo.")
        val conn = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 25_000
            setRequestProperty("Accept", "application/json")
            token?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { reader -> reader.readText() } } ?: "{}"
            val result = try { JSONObject(text) } catch (_: Exception) { JSONObject() }
            if (code !in 200..299) throw AfrogoApiException(code, result.optString("error", "Error de servidor ($code)."))
            return result
        } finally { conn.disconnect() }
    }

    fun login(email: String, password: String): JSONObject = request(
        "/api/auth/login", "POST", JSONObject().put("email", email).put("password", password)
    )

    fun register(name: String, email: String, password: String): JSONObject = request(
        "/api/auth/register", "POST", JSONObject().put("name", name).put("email", email).put("password", password)
    )

    fun me(token: String): JSONObject = request("/api/auth/me", token = token)

    fun refresh(refreshToken: String): JSONObject = request(
        "/api/auth/refresh", "POST", JSONObject().put("refreshToken", refreshToken)
    )

    fun upgradeLegacySession(accessToken: String): JSONObject = request(
        "/api/auth/refresh", "POST", JSONObject().put("token", accessToken)
    )

    fun refreshSavedSession(context: Context): String {
        val oldRefresh = savedRefreshToken(context)
        if (oldRefresh.isBlank()) throw IllegalStateException("No hay una sesión renovable guardada.")
        val result = refresh(oldRefresh)
        val access = result.optString("token", "")
        val refresh = result.optString("refreshToken", "")
        if (access.isBlank() || refresh.isBlank()) throw IllegalStateException("El servidor no devolvió una sesión renovada válida.")
        saveTokens(context, access, refresh)
        return access
    }

    /** Biblioteca personal sincronizada en la nube y aislada por la cuenta autenticada. */
    fun library(token: String): JSONObject = request("/api/me/library", token = token)
    fun saveLibrary(token: String, library: JSONObject): JSONObject = request("/api/me/library", "PUT", library, token)
    fun upsertPlaylist(token: String, playlist: JSONObject): JSONObject = request("/api/me/playlists", "POST", JSONObject().put("playlist", playlist), token)
    fun deletePlaylist(token: String, playlistId: String): JSONObject = request("/api/me/playlists/${java.net.URLEncoder.encode(playlistId, "UTF-8")}", "DELETE", token = token)
    fun setFavorite(token: String, trackId: String, favorite: Boolean): JSONObject = request("/api/me/favorites", "POST", JSONObject().put("trackId", trackId).put("favorite", favorite), token)
    fun setSavedTrack(token: String, trackId: String, saved: Boolean): JSONObject = request("/api/me/saved-tracks", "POST", JSONObject().put("trackId", trackId).put("saved", saved), token)
    fun addRecent(token: String, track: JSONObject): JSONObject = request("/api/me/history", "POST", JSONObject().put("track", track), token)

    fun catalog(token: String): List<AfrogoTrack> {
        val data = request("/api/catalog", token = token)
        val items = data.optJSONArray("items") ?: return emptyList()
        return buildList {
            for (i in 0 until items.length()) {
                val row = items.optJSONObject(i) ?: continue
                val playUrl = row.optString("playUrl", "")
                if (playUrl.isBlank()) continue
                add(AfrogoTrack(
                    id = row.optString("id"),
                    title = row.optString("title", "Canción sin título"),
                    artist = row.optString("artist", "Artista desconocido"),
                    album = row.optString("album", ""),
                    genre = row.optString("genre", "Sin categoría"),
                    duration = row.optLong("duration", 0L),
                    trackNumber = row.optInt("trackNumber", 0),
                    coverUrl = row.optString("coverUrl", ""),
                    playUrl = playUrl
                ))
            }
        }
    }
}
