package com.deepecho.mobile.net

import com.deepecho.mobile.data.Settings
import com.deepecho.mobile.data.Song
import com.deepecho.mobile.data.TopArtist
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CompletableFuture

data class Resolved(val url: String, val ext: String)

data class RemotePlaylist(
    val url: String,
    val title: String,
    val uploader: String,
    val thumb: String?,
    val songCount: Long
)

/**
 * Search + audio-stream resolve (NewPipeExtractor).
 * Existing playback resolver is intentionally kept unchanged.
 * New playlist helpers are additive only.
 */
object YouTubeApi {
    private val yt get() = ServiceList.YouTube

    private val searchCache = ConcurrentHashMap<String, Pair<Long, List<Song>>>()
    private val videoSearchCache = ConcurrentHashMap<String, Pair<Long, List<Song>>>()
    private val playlistSearchCache = ConcurrentHashMap<String, Pair<Long, List<RemotePlaylist>>>()
    private val albumSearchCache = ConcurrentHashMap<String, Pair<Long, List<RemotePlaylist>>>()
    private val artistSearchCache = ConcurrentHashMap<String, Pair<Long, List<TopArtist>>>()
    private val playlistOpenCache = ConcurrentHashMap<String, Pair<Long, List<Song>>>()
    private val streamCache = ConcurrentHashMap<String, Pair<Long, Resolved>>()
    private val inFlightResolves = ConcurrentHashMap<String, CompletableFuture<Resolved>>()


    private fun bestThumbnail(images: List<Image>): String? {
        if (images.isEmpty()) return null
        return images.maxByOrNull { image ->
            val w = image.width
            val h = image.height
            val known = w > 0 && h > 0
            val square = if (known) {
                val big = maxOf(w, h).toDouble()
                val small = minOf(w, h).toDouble()
                (small / big * 1_000_000.0).toLong()
            } else 350_000L
            val area = if (known) (w.toLong() * h.toLong()).coerceAtMost(4_000_000L) else 0L
            // Square album art dominates; resolution breaks ties.
            square * 10L + area
        }?.url
    }

    /** Default discovery search: music songs first, videos as a safe fallback. */
    fun search(query: String): List<Song> {
        val songs = searchSongs(query)
        return if (songs.isNotEmpty()) songs else searchVideos(query)
    }

    /** Dedicated song result bucket used by the categorized Search screen. */
    fun searchSongs(query: String): List<Song> {
        val key = query.trim().lowercase()
        searchCache[key]?.let { if (System.currentTimeMillis() - it.first < 10 * 60_000) return it.second }
        val result = runCatching { doSearch(query, YoutubeSearchQueryHandlerFactory.MUSIC_SONGS) }
            .getOrDefault(emptyList())
        if (result.isNotEmpty()) searchCache[key] = System.currentTimeMillis() to result
        return result
    }

    /** Dedicated video result bucket so Search no longer labels every hit as a song. */
    fun searchVideos(query: String): List<Song> {
        val key = query.trim().lowercase()
        videoSearchCache[key]?.let { if (System.currentTimeMillis() - it.first < 10 * 60_000) return it.second }
        val result = runCatching { doSearch(query, YoutubeSearchQueryHandlerFactory.MUSIC_VIDEOS) }
            .getOrDefault(emptyList())
            .ifEmpty {
                runCatching { doSearch(query, YoutubeSearchQueryHandlerFactory.VIDEOS) }
                    .getOrDefault(emptyList())
            }
        if (result.isNotEmpty()) videoSearchCache[key] = System.currentTimeMillis() to result
        return result
    }

    /** Dedicated YouTube Music artist bucket, with normal channel search as a fallback. */
    fun searchArtists(query: String, limit: Int = 12): List<TopArtist> {
        val key = query.trim().lowercase()
        artistSearchCache[key]?.let {
            if (System.currentTimeMillis() - it.first < 15 * 60_000) return it.second.take(limit)
        }
        fun fetch(filter: String): List<TopArtist> = runCatching {
            val handler = yt.searchQHFactory.fromQuery(query, listOf(filter), "")
            val info = SearchInfo.getInfo(yt, handler)
            info.relatedItems
                .filterIsInstance<ChannelInfoItem>()
                .map { item ->
                    TopArtist(
                        name = item.name ?: "Artist",
                        thumb = bestThumbnail(item.thumbnails),
                        plays = item.subscriberCount.coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    )
                }
                .filter { it.name.isNotBlank() }
        }.getOrDefault(emptyList())

        val result = fetch(YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS)
            .ifEmpty { fetch(YoutubeSearchQueryHandlerFactory.CHANNELS) }
        if (result.isNotEmpty()) artistSearchCache[key] = System.currentTimeMillis() to result
        return result.take(limit)
    }

    private fun doSearch(query: String, filter: String): List<Song> {
        val handler = yt.searchQHFactory.fromQuery(query, listOf(filter), "")
        val info = SearchInfo.getInfo(yt, handler)
        return info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .filter { it.duration > 0 }
            .map {
                Song(
                    url = it.url,
                    title = it.name ?: "Unknown",
                    artist = (it.uploaderName ?: "").removeSuffix(" - Topic").trim(),
                    durationSec = it.duration,
                    thumb = bestThumbnail(it.thumbnails)
                )
            }
    }

    /** YouTube Music album bucket used by the Albums search chip. */
    fun searchAlbums(query: String, limit: Int = 10): List<RemotePlaylist> {
        val key = query.trim().lowercase()
        albumSearchCache[key]?.let {
            if (System.currentTimeMillis() - it.first < 15 * 60_000) return it.second.take(limit)
        }

        val result = searchPlaylistLike(query, YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS)
        if (result.isNotEmpty()) albumSearchCache[key] = System.currentTimeMillis() to result
        return result.take(limit)
    }

    fun searchPlaylists(query: String, limit: Int = 10): List<RemotePlaylist> {
        val key = query.trim().lowercase()
        playlistSearchCache[key]?.let {
            if (System.currentTimeMillis() - it.first < 15 * 60_000) return it.second.take(limit)
        }

        val result = searchPlaylistLike(query, YoutubeSearchQueryHandlerFactory.MUSIC_PLAYLISTS)
        if (result.isNotEmpty()) playlistSearchCache[key] = System.currentTimeMillis() to result
        return result.take(limit)
    }

    private fun searchPlaylistLike(query: String, filter: String): List<RemotePlaylist> = runCatching {
        val handler = yt.searchQHFactory.fromQuery(query, listOf(filter), "")
        val info = SearchInfo.getInfo(yt, handler)
        info.relatedItems
            .filterIsInstance<PlaylistInfoItem>()
            .map {
                RemotePlaylist(
                    url = it.url,
                    title = it.name ?: "YouTube Music",
                    uploader = it.uploaderName ?: "YouTube Music",
                    thumb = bestThumbnail(it.thumbnails),
                    songCount = it.streamCount
                )
            }
    }.getOrDefault(emptyList())

    fun openPlaylist(url: String): List<Song> {
        playlistOpenCache[url]?.let {
            if (System.currentTimeMillis() - it.first < 30 * 60_000) return it.second
        }

        val info = PlaylistInfo.getInfo(yt, url)
        val songs = info.relatedItems
            .filter { it.duration > 0 }
            .map {
                Song(
                    url = it.url,
                    title = it.name ?: "Unknown",
                    artist = (it.uploaderName ?: "").removeSuffix(" - Topic").trim(),
                    durationSec = it.duration,
                    thumb = bestThumbnail(it.thumbnails)
                )
            }
        if (songs.isNotEmpty()) {
            playlistOpenCache[url] = System.currentTimeMillis() to songs
        }
        return songs
    }

    fun resolve(videoUrl: String, forceFresh: Boolean = false): Resolved {
        if (!forceFresh) {
            streamCache[videoUrl]?.let {
                if (System.currentTimeMillis() - it.first < 3 * 3600_000L) return it.second
            }
        } else {
            streamCache.remove(videoUrl)
        }

        // Pre-warm + playback/download often request the same song at nearly the same time.
        // Share one NewPipe extraction instead of doing duplicate network work.
        val mine = CompletableFuture<Resolved>()
        val existing = inFlightResolves.putIfAbsent(videoUrl, mine)
        if (existing != null) {
            return try {
                existing.get()
            } catch (e: Exception) {
                val cause = e.cause
                if (cause is Exception) throw cause
                throw IOException("Stream resolve fail", e)
            }
        }

        try {
            val info = StreamInfo.getInfo(yt, videoUrl)
            val all = info.audioStreams.filter {
                it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && !it.content.isNullOrEmpty()
            }
            if (all.isEmpty()) throw IOException("Is gaane ka audio stream nahi mila")

            val pool = all.filter { it.format == MediaFormat.M4A }.ifEmpty { all }
            val sorted = pool.sortedBy { it.averageBitrate }
            val pick = when (Settings.quality.value) {
                0 -> sorted.first()
                1 -> sorted[sorted.size / 2]
                else -> sorted.last()
            }
            val ext = pick.format?.suffix ?: "m4a"
            val r = Resolved(pick.content, ext)
            streamCache[videoUrl] = System.currentTimeMillis() to r
            mine.complete(r)
            return r
        } catch (t: Throwable) {
            mine.completeExceptionally(t)
            if (t is Exception) throw t
            throw IOException("Stream resolve fail", t)
        } finally {
            inFlightResolves.remove(videoUrl, mine)
        }
    }

    fun invalidate(videoUrl: String) {
        streamCache.remove(videoUrl)
    }
}
