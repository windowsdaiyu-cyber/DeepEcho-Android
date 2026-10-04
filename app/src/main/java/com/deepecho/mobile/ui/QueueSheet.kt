package com.deepecho.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deepecho.mobile.data.Settings
import com.deepecho.mobile.player.PlayerClient

@Composable
fun UpNextDialog(onDismiss: () -> Unit) {
    val revision = PlayerClient.queueRevision
    val entries = remember(revision) { PlayerClient.queueSnapshot() }
    val upcomingCount = entries.count { !it.current }
    val smartAutoplay = Settings.smartAutoplay.value

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.QueueMusic, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Queue", fontWeight = FontWeight.Bold)
                }
                Text(
                    "$upcomingCount upcoming • Smart Autoplay ${if (smartAutoplay) "ON" else "OFF"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            if (entries.isEmpty()) {
                Text(
                    "Queue abhi empty hai. Kisi song ke menu se Add to queue / Play next use karo.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 500.dp)) {
                    items(entries, key = { "${it.song.url}#${it.index}" }) { entry ->
                        var drag by remember(entry.song.url, entry.index) { mutableFloatStateOf(0f) }

                        Row(
                            Modifier.fillMaxWidth()
                                .pointerInput(entry.index, entries.size) {
                                    detectVerticalDragGestures(
                                        onVerticalDrag = { _, amount -> drag += amount },
                                        onDragEnd = {
                                            when {
                                                drag > 42f && entry.index < entries.lastIndex ->
                                                    PlayerClient.moveQueueItem(entry.index, entry.index + 1)
                                                drag < -42f && entry.index > 0 ->
                                                    PlayerClient.moveQueueItem(entry.index, entry.index - 1)
                                            }
                                            drag = 0f
                                        },
                                        onDragCancel = { drag = 0f }
                                    )
                                }
                                .clickable(enabled = !entry.current) {
                                    PlayerClient.playQueueItem(entry.index)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Art(entry.song.thumb, Modifier.size(46.dp))
                            Spacer(Modifier.width(10.dp))

                            Column(Modifier.weight(1f)) {
                                Text(
                                    entry.song.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (entry.current) FontWeight.Bold else FontWeight.Medium,
                                    color = if (entry.current) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    if (entry.current) "Now playing"
                                    else "${entry.reason} • drag ↑/↓",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (!entry.current) {
                                IconButton(onClick = { PlayerClient.playQueueItem(entry.index) }) {
                                    Icon(Icons.Filled.PlayArrow, "Play this")
                                }
                                IconButton(onClick = { PlayerClient.removeQueueItem(entry.index) }) {
                                    Icon(Icons.Filled.Close, "Remove")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        dismissButton = {
            if (upcomingCount > 0) {
                TextButton(onClick = { PlayerClient.clearUpcomingQueue() }) {
                    Text("Clear upcoming")
                }
            }
        }
    )
}
