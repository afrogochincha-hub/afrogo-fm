package com.afrogo.fm

import android.content.Intent
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.ImmutableList
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AfrogoMediaService : MediaLibraryService() {
    private var librarySession: MediaLibrarySession? = null
    private lateinit var player: ExoPlayer
    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val rootId = "afrogo_root"
    private val radiosId = "afrogo_radios"
    private val libraryId = "afrogo_music"
    private val albumPrefix = "afrogo_album:"
    private val liveId = "afrogo_live_stream"
    @Volatile private var cachedToken: String = ""
    @Volatile private var cachedAt: Long = 0L
    @Volatile private var cachedTracks: List<AfrogoTrack> = emptyList()

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

    private fun albumKey(artist: String, album: String): String = albumPrefix + Uri.encode("$artist|$album")

    private fun childrenFor(parentId: String, params: LibraryParams?): List<MediaItem> {
        return when {
            parentId == rootId -> listOf(
                folder(radiosId, "Radio en vivo", "Emisora oficial de Afrogo FM"),
                folder(libraryId, "Biblioteca musical", "Catálogo central de AzuraCast")
            )
            parentId == radiosId -> listOf(liveMediaItem())
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
