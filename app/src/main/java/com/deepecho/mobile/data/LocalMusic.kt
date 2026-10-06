package com.deepecho.mobile.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Lightweight MediaStore index. Scans only when explicitly asked / first Local Music open. */
data class LocalTrack(
    val song: Song,
    val album: String,
    val albumId: Long,
    val folder: String,
    val year: Int,
    val trackNumber: Int,
    val genre: String?
)

object LocalMusic {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var appContext: Context
    private val _tracks = MutableStateFlow<List<LocalTrack>>(emptyList())
    private val _allTracks = MutableStateFlow<List<LocalTrack>>(emptyList())
    private val _excludedFolders = MutableStateFlow<Set<String>>(emptySet())
    private val _scanning = MutableStateFlow(false)
    private val _lastScanAt = MutableStateFlow(0L)
    val tracks: StateFlow<List<LocalTrack>> = _tracks
    val allTracks: StateFlow<List<LocalTrack>> = _allTracks
    val excludedFolders: StateFlow<Set<String>> = _excludedFolders
    val scanning: StateFlow<Boolean> = _scanning
    val lastScanAt: StateFlow<Long> = _lastScanAt

    fun init(ctx: Context) {
        appContext = ctx.applicationContext
        _excludedFolders.value = Settings.getString("local_excluded_folders", "")
            .split("\n").map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }

    fun toggleExcludedFolder(folder: String) {
        val clean = folder.trim()
        if (clean.isBlank()) return
        val set = _excludedFolders.value.toMutableSet()
        if (!set.add(clean)) set.remove(clean)
        _excludedFolders.value = set
        Settings.putString("local_excluded_folders", set.sorted().joinToString("\n"))
        applyFolderFilter()
    }

    private fun applyFolderFilter() {
        val excluded = _excludedFolders.value
        _tracks.value = _allTracks.value.filterNot { it.folder in excluded }
    }

    fun scan() {
        if (!::appContext.isInitialized || _scanning.value) return
        _scanning.value = true
        scope.launch {
            val out = mutableListOf<LocalTrack>()
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val folderColumn = if (android.os.Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.RELATIVE_PATH else MediaStore.Audio.Media.DATA
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                folderColumn,
                MediaStore.Audio.Media.YEAR,
                MediaStore.Audio.Media.TRACK
            )
            val selection = "${MediaStore.Audio.Media.IS_MUSIC}!=0 AND ${MediaStore.Audio.Media.DURATION}>10000"
            runCatching {
                appContext.contentResolver.query(uri, projection, selection, null, "${MediaStore.Audio.Media.DATE_ADDED} DESC")?.use { c ->
                    val idI = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val albumIdI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val durI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val dataI = c.getColumnIndexOrThrow(folderColumn)
                    val yearI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                    val trackI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                    while (c.moveToNext()) {
                        val id = c.getLong(idI)
                        val albumId = c.getLong(albumIdI)
                        val content = ContentUris.withAppendedId(uri, id).toString()
                        val path = c.getString(dataI).orEmpty().trim('/')
                        val folder = if (android.os.Build.VERSION.SDK_INT >= 29) {
                            path.substringAfterLast('/').ifBlank { "Device" }
                        } else {
                            path.substringBeforeLast('/', "Device").substringAfterLast('/').ifBlank { "Device" }
                        }
                        val art = if (albumId > 0) "content://media/external/audio/albumart/$albumId" else null
                        val song = Song(
                            url = content,
                            title = c.getString(titleI).orEmpty().ifBlank { "Unknown title" },
                            artist = c.getString(artistI).orEmpty().takeUnless { it == "<unknown>" }.orEmpty().ifBlank { "Unknown artist" },
                            durationSec = (c.getLong(durI) / 1000L).coerceAtLeast(0),
                            thumb = art
                        )
                        out += LocalTrack(
                            song = song,
                            album = c.getString(albumI).orEmpty().takeUnless { it == "<unknown>" }.orEmpty().ifBlank { "Unknown album" },
                            albumId = albumId,
                            folder = folder,
                            year = c.getInt(yearI),
                            trackNumber = c.getInt(trackI),
                            genre = null
                        )
                    }
                }
            }
            _allTracks.value = out.distinctBy { it.song.url }
            applyFolderFilter()
            _lastScanAt.value = System.currentTimeMillis()
            _scanning.value = false
        }
    }
}
