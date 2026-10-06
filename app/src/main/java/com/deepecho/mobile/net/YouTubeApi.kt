package com.deepecho.mobile.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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

data class ResolvedVideo(
    val url: String,
    val width: Int,
    val height: Int,
    val resolution: String
)

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
    private var appContext: Context? = null
    fun init(ctx: Context) { appContext = ctx.applicationContext }

    private fun effectiveStreamQuality(): Int {
        if (Settings.dataSaverMode.value) return 0
        val ctx = appContext ?: return Settings.quality.value
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return Settings.quality.value
        val caps = cm.activeNetwork?.let(cm::getNetworkCapabilities)
        return if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        ) Settings.wifiQuality.value else Settings.mobileQuality.value
    }

    private val searchCache = ConcurrentHashMap<String, Pair<Long, List<Song>>>()
    private val videoSearchCache = ConcurrentHashMap<String, Pair<Long, List<Song>>>()
    private val playlistSearchCache = ConcurrentHashMap<String, Pair<Long, List<RemotePlaylist>>>()
    private val albumSearchCache = ConcurrentHashMap<String, Pair<Long, List<RemotePlaylist>>>()
    private val artistSearchCache = ConcurrentHashMap<String, Pair<Long, List<TopArtist>>>()
    private val playlistOpenCache = ConcurrentHashMap<String, Pair<Long, List<Song>>>()
    private val streamCache = ConcurrentHashMap<String, Pair<Long, Resolved>>()
    private val videoStreamCache = ConcurrentHashMap<String, Pair<Long, ResolvedVideo>>()
    private val suggestionCache = ConcurrentHashMap<String, Pair<Long, List<String>>>()
    private val inFlightResolves = ConcurrentHashMap<String, CompletableFuture<Resolved>>()


    private fun bestThumbnail(images: List<Image>): String? {
        if (images.isEmpty()) return null
        return images.maxByOrNull { image ->
            val w = image.width
            val h = image.height
            val known = w > 0 && h > 0
            if (!known) return@maxByOrNull 0L

            val big = maxOf(w, h).toDouble().coerceAtLeast(1.0)
            val small = minOf(w, h).toDouble()
            val squareScore = (small / big * 1_000_000.0).toLong()
            val area = (w.toLong() * h.toLong()).coerceAtMost(8_000_000L)

            // v1.10.8: resolution is the primary signal. Older builds over-weighted squareness,
            // which could choose a tiny 120x120 image over a much sharper 720p/1080p image.
            // Keep a moderate square-art bonus, but never at the expense of a large quality jump.
            area * 8L + squareScore * 2L
        }?.url
    }


    /**
     * Real YouTube typeahead from NewPipe's YouTube SuggestionExtractor. This uses the
     * same extractor stack as DeepEcho search and avoids fake locally generated suffixes.
     */
    fun searchSuggestions(query: String, limit: Int = 10): List<String> {
        val clean = query.trim().replace(Regex("\\s+"), " ")
        if (clean.length < 2) return emptyList()
        val key = clean.lowercase()
        suggestionCache[key]?.let { cached ->
            if (System.currentTimeMillis() - cached.first < 5 * 60_000L) {
                return cached.second.take(limit)
            }
        }

        val results = runCatching {
            yt.suggestionExtractor.suggestionList(clean)
        }.getOrDefault(emptyList())
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotBlank() && !it.equals(clean, true) }
            .distinctBy { it.lowercase() }
            .take(limit)

        if (results.isNotEmpty()) suggestionCache[key] = System.currentTimeMillis() to results
        return results
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
            val streamQuality = effectiveStreamQuality()
            val pick = when (streamQuality) {
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


    /** Separate download resolver so download quality can differ from streaming quality. */
    fun resolveForDownload(videoUrl: String): Resolved {
        val info = StreamInfo.getInfo(yt, videoUrl)
        val all = info.audioStreams.filter {
            it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && !it.content.isNullOrEmpty()
        }
        if (all.isEmpty()) throw IOException("Is gaane ka audio stream nahi mila")

        val pool = all.filter { it.format == MediaFormat.M4A }.ifEmpty { all }
        val sorted = pool.sortedBy { it.averageBitrate }
        val pick = when (Settings.downloadQuality.value) {
            0 -> sorted.first()
            1 -> sorted[sorted.size / 2]
            else -> sorted.last()
        }
        return Resolved(pick.content, pick.format?.suffix ?: "m4a")
    }


    /**
     * Resolve a direct progressive video stream for the Ambient player. This bypasses
     * YouTube iframe/WebView embedding entirely (which can fail with player error 153
     * when Android does not provide an accepted HTTP referrer). Audio is muted in the
     * Ambient player; DeepEcho's normal PlaybackService remains the audio source.
     */
    fun resolveVideo(videoUrl: String, forceFresh: Boolean = false): ResolvedVideo {
        if (!forceFresh) {
            videoStreamCache[videoUrl]?.let { cached ->
                if (System.currentTimeMillis() - cached.first < 45 * 60_000L) return cached.second
            }
        } else {
            videoStreamCache.remove(videoUrl)
        }

        val info = StreamInfo.getInfo(yt, videoUrl)
        // Ambient video is muted, so video-only streams are perfectly valid and often
        // more widely available than legacy muxed YouTube streams.
        val available = (info.videoStreams + info.videoOnlyStreams)
            .filter { it.content.isNotBlank() && it.content.startsWith("http") }
            .distinctBy { it.content }
        if (available.isEmpty()) throw IOException("Is video ka direct video stream nahi mila")

        // YouTube increasingly exposes the useful muted video track as a DASH-labelled direct
        // googlevideo URL rather than a legacy progressive mux. Media3 can play the direct MP4
        // URL, so do not reject it merely because NewPipe labels the delivery method DASH.
        val mp4 = available.filter { it.format == MediaFormat.MPEG_4 }.ifEmpty { available }
        val known = mp4.filter { it.height > 0 }
        val pick = if (known.isNotEmpty()) {
            val under720 = known.filter { it.height <= 720 }
            val qualityPool = if (under720.isNotEmpty()) under720 else known
            qualityPool.maxByOrNull { stream ->
                val progressiveBonus = if (stream.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP) 10_000 else 0
                progressiveBonus + stream.height
            }!!
        } else {
            mp4.first()
        }

        val resolved = ResolvedVideo(
            url = pick.content,
            width = pick.width.coerceAtLeast(0),
            height = pick.height.coerceAtLeast(0),
            resolution = pick.resolution.ifBlank {
                if (pick.height > 0) "${pick.height}p" else "video"
            }
        )
        videoStreamCache[videoUrl] = System.currentTimeMillis() to resolved
        return resolved
    }

    fun invalidate(videoUrl: String) {
        streamCache.remove(videoUrl)
        videoStreamCache.remove(videoUrl)
    }
}
