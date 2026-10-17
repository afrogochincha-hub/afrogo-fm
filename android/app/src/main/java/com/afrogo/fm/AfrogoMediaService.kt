package com.afrogo.fm

import android.content.Intent
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.Locale
import org.json.JSONObject
import org.json.JSONArray

class AfrogoMediaService : MediaLibraryService() {
    private var librarySession: MediaLibrarySession? = null
    private lateinit var player: ExoPlayer
    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val rootId = "afrogo_root"
    private val radiosId = "afrogo_radios"
    private val libraryId = "afrogo_music"
    private val favoritesId = "afrogo_favorites"
    private val playlistsId = "afrogo_playlists"
    private val playlistPrefix = "afrogo_playlist:"
    private val albumPrefix = "afrogo_album:"
    private val liveId = "afrogo_live_stream"
    @Volatile private var cachedToken: String = ""
    @Volatile private var cachedAt: Long = 0L
    @Volatile private var cachedTracks: List<AfrogoTrack> = emptyList()
    @Volatile private var cachedLibrary: JSONObject? = null
    @Volatile private var cachedLibraryAt: Long = 0L

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build()
        player.setMediaItem(liveMediaItem())
        librarySession = MediaLibrarySession.Builder(this, player, LibraryCallback()).build()
    }

    private fun liveMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(liveId)
        .setUri(Uri.parse(AzuraConfig.STREAM_URL))
        .setMediaMetadata(MediaMetadata.Builder()
            .setTitle(AzuraConfig.STATION_NAME)
            .setArtist("Radio en vivo")
            .setAlbumTitle("Afrogo FM")
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build())
        .build()

    private fun folder(id: String, title: String, subtitle: String, art: String = ""): MediaItem {
        val metadata = MediaMetadata.Builder().setTitle(title).setSubtitle(subtitle)
            .setIsBrowsable(true).setIsPlayable(false)
        if (art.isNotBlank()) metadata.setArtworkUri(Uri.parse(art))
        return MediaItem.Builder().setMediaId(id).setMediaMetadata(metadata.build()).build()
    }

    private fun infoItem(id: String, title: String): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setIsBrowsable(false).setIsPlayable(false).build())
        .build()

    private fun trackItem(track: AfrogoTrack): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album.ifBlank { "Sencillo" })
            .setSubtitle(track.genre)
            .setIsBrowsable(false)
            .setIsPlayable(true)
        if (track.coverUrl.isNotBlank()) metadata.setArtworkUri(Uri.parse(track.coverUrl))
        return MediaItem.Builder()
            .setMediaId("azuracast:${track.id}")
            .setUri(Uri.parse(track.playUrl))
            .setMediaMetadata(metadata.build())
            .build()
    }

    private fun getTracks(force: Boolean = false): List<AfrogoTrack> {
        val token = AfrogoApi.savedToken(this)
        if (token.isBlank()) return emptyList()
        val now = System.currentTimeMillis()
        if (!force && token == cachedToken && now - cachedAt < 60_000L) return cachedTracks
        val tracks = AfrogoApi.catalog(token)
        cachedToken = token
        cachedAt = now
        cachedTracks = tracks
        return tracks
    }

    private fun getCloudLibrary(force: Boolean = false): JSONObject {
        val token = AfrogoApi.savedToken(this)
        if (token.isBlank()) return JSONObject()
        if (!force && token == cachedToken && System.currentTimeMillis() - cachedLibraryAt < 15_000L) return cachedLibrary ?: JSONObject()
        val data = AfrogoApi.library(token)
        cachedLibrary = data
        cachedLibraryAt = System.currentTimeMillis()
        return data
    }

    private fun idsFrom(array: JSONArray?): Set<String> = if (array == null) emptySet() else
        (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }.toSet()

    private fun favoriteItems(): List<MediaItem> {
        val favorites = idsFrom(getCloudLibrary().optJSONArray("favorites"))
        return getTracks().filter { favorites.contains("azura:${it.id}") }.map { trackItem(it) }
    }

    private fun playlistFolders(): List<MediaItem> {
        val playlists = getCloudLibrary().optJSONArray("playlists") ?: return emptyList()
        return (0 until playlists.length()).mapNotNull { index ->
            val item = playlists.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id")
            if (id.isBlank()) return@mapNotNull null
            val songCount = item.optJSONArray("songIds")?.length() ?: 0
            folder(playlistPrefix + Uri.encode(id), item.optString("name", "Playlist"), "$songCount canciones · Tu cuenta Afrogo", item.optString("cover", ""))
        }
    }

    private fun albumKey(artist: String, album: String): String = albumPrefix + Uri.encode("$artist|$album")

    private fun searchMediaItems(query: String): List<MediaItem> {
        val normalized = query.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank()) return emptyList()
        return getTracks().filter { track ->
            listOf(track.title, track.artist, track.album, track.genre).any { value ->
                value.lowercase(Locale.ROOT).contains(normalized)
            }
        }.map { trackItem(it) }
    }

    private fun childrenFor(parentId: String, params: LibraryParams?): List<MediaItem> {
        return when {
            parentId == rootId -> listOf(
                folder(radiosId, "Radio en vivo", "Emisora oficial de Afrogo FM"),
                folder(libraryId, "Biblioteca musical", "Catálogo central de AzuraCast"),
                folder(favoritesId, "Mis favoritos", "Canciones guardadas en tu cuenta"),
                folder(playlistsId, "Mis playlists", "Colecciones sincronizadas de tu cuenta")
            )
            parentId == radiosId -> listOf(liveMediaItem())
            parentId == favoritesId -> favoriteItems().ifEmpty { listOf(infoItem("afrogo_no_favorites", "Aún no tienes canciones favoritas")) }
            parentId == playlistsId -> playlistFolders().ifEmpty { listOf(infoItem("afrogo_no_playlists", "Crea playlists desde la aplicación Afrogo FM")) }
            parentId.startsWith(playlistPrefix) -> {
                val playlistId = Uri.decode(parentId.removePrefix(playlistPrefix))
                val playlist = (getCloudLibrary().optJSONArray("playlists") ?: JSONArray()).let { rows ->
                    (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }.firstOrNull { it.optString("id") == playlistId }
                }
                val ids = idsFrom(playlist?.optJSONArray("songIds"))
                getTracks().filter { ids.contains("azura:${it.id}") }.map { trackItem(it) }
            }
            parentId == libraryId -> {
                val tracks = getTracks()
                if (tracks.isEmpty()) {
                    val token = AfrogoApi.savedToken(this)
                    if (token.isBlank()) listOf(infoItem("afrogo_login_required", "Inicia sesión en Afrogo FM para ver la biblioteca"))
                    else listOf(infoItem("afrogo_catalog_unavailable", "No se pudo cargar el catálogo; vuelve a intentarlo"))
                } else {
                    val albumGroups = tracks.filter { it.album.isNotBlank() }
                        .groupBy { it.artist.trim().lowercase() + "\u0000" + it.album.trim().lowercase() }
                    val albums = albumGroups.values.map { group ->
                        val first = group.first()
                        folder(albumKey(first.artist, first.album), first.album, "${first.artist} · ${group.size} canciones", first.coverUrl)
                    }
                    val singles = tracks.filter { it.album.isBlank() }.map { trackItem(it) }
                    (albums + singles).sortedBy { it.mediaMetadata.title?.toString()?.lowercase() ?: "" }
                }
            }
            parentId.startsWith(albumPrefix) -> {
                val pair = Uri.decode(parentId.removePrefix(albumPrefix)).split("|", limit = 2)
                if (pair.size != 2) emptyList()
                else getTracks().filter { it.artist.equals(pair[0], ignoreCase = true) && it.album.equals(pair[1], ignoreCase = true) }
                    .sortedWith(compareBy<AfrogoTrack> { it.trackNumber.takeIf { n -> n > 0 } ?: Int.MAX_VALUE }.thenBy { it.title.lowercase() })
                    .map { trackItem(it) }
            }
            else -> emptyList()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = librarySession

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root = folder(rootId, "Afrogo FM", "Radio y biblioteca musical")
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            val future = SettableFuture.create<LibraryResult<Void>>()
            ioExecutor.execute {
                try {
                    val count = searchMediaItems(query).size
                    if (query.isNotBlank()) session.notifySearchResultChanged(browser, query, count, params)
                    future.set(LibraryResult.ofVoid())
                } catch (_: Exception) {
                    if (query.isNotBlank()) session.notifySearchResultChanged(browser, query, 0, params)
                    future.set(LibraryResult.ofVoid())
                }
            }
            return future
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            ioExecutor.execute {
                try {
                    val all = searchMediaItems(query)
                    val start = (page.coerceAtLeast(0) * pageSize.coerceAtLeast(1)).coerceAtMost(all.size)
                    val end = (start + pageSize.coerceAtLeast(1)).coerceAtMost(all.size)
                    future.set(LibraryResult.ofItemList(ImmutableList.copyOf(all.subList(start, end)), params))
                } catch (_: Exception) {
                    future.set(LibraryResult.ofItemList(ImmutableList.of(), params))
                }
            }
            return future
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            ioExecutor.execute {
                try {
                    val all = childrenFor(parentId, params)
                    val start = (page.coerceAtLeast(0) * pageSize.coerceAtLeast(1)).coerceAtMost(all.size)
                    val end = (start + pageSize.coerceAtLeast(1)).coerceAtMost(all.size)
                    future.set(LibraryResult.ofItemList(ImmutableList.copyOf(all.subList(start, end)), params))
                } catch (e: Exception) {
                    val fallback = listOf(infoItem("afrogo_api_error", "Error conectando con el catálogo de Afrogo"))
                    future.set(LibraryResult.ofItemList(ImmutableList.copyOf(fallback), params))
                }
            }
            return future
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val playable = mediaItems.filter { it.localConfiguration != null }
            val safe = if (playable.isNotEmpty()) playable else listOf(liveMediaItem())
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(safe, startIndex.coerceIn(0, safe.lastIndex), startPositionMs))
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        librarySession?.run { player.release(); release() }
        librarySession = null
        ioExecutor.shutdownNow()
        super.onDestroy()
    }
}
