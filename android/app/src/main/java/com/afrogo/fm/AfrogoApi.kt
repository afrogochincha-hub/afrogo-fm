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

object AfrogoApi {
    private const val PREFS = "afrogo_auth"
    private const val TOKEN_KEY = "token"

    fun savedToken(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(TOKEN_KEY, "") ?: ""
    fun saveToken(context: Context, token: String) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(TOKEN_KEY, token).apply() }
    fun clearToken(context: Context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(TOKEN_KEY).apply() }

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
            if (code !in 200..299) throw IllegalStateException(result.optString("error", "Error de servidor ($code)."))
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
