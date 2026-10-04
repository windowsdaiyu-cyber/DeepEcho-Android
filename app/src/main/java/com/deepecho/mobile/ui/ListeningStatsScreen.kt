package com.deepecho.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deepecho.mobile.data.ListeningStat
import com.deepecho.mobile.data.Store
import com.deepecho.mobile.player.PlayerClient
import java.time.LocalDate

private fun statDuration(ms: Long): String {
    val minutes = (ms / 60_000L).coerceAtLeast(0L)
    return when {
        minutes < 60 -> "${minutes}m"
        minutes < 24 * 60 -> "${minutes / 60}h ${minutes % 60}m"
        else -> "${minutes / (24 * 60)}d ${(minutes % (24 * 60)) / 60}h"
    }
}

@Composable
fun ListeningStatsScreen() {
    val stats by Store.listeningStats.collectAsState()
    val daily by Store.dailyListeningMs.collectAsState()
    val today = remember { LocalDate.now() }

    val totalMs = remember(stats) { stats.sumOf { it.listenedMs } }
    val totalPlays = remember(stats) { stats.sumOf { it.playCount } }
    val todayMs = daily[today.toString()] ?: 0L
    val last7 = remember(daily, today) {
        (0L..6L).sumOf { days -> daily[today.minusDays(days).toString()] ?: 0L }
    }
    val topSongs = remember(stats) {
        stats.sortedWith(compareByDescending<ListeningStat> { it.listenedMs }.thenByDescending { it.playCount }).take(10)
    }
    val topArtists = remember(stats) {
        stats.filter { it.song.artist.isNotBlank() }
            .groupBy { it.song.artist.trim() }
            .map { (artist, items) ->
                Triple(artist, items.sumOf { it.listenedMs }, items.sumOf { it.playCount })
            }
            .sortedByDescending { it.second }
            .take(8)
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard("Today", statDuration(todayMs), Modifier.weight(1f))
                StatCard("7 days", statDuration(last7), Modifier.weight(1f))
                StatCard("All time", statDuration(totalMs), Modifier.weight(1f))
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard("Plays", totalPlays.toString(), Modifier.weight(1f))
                StatCard("Tracked songs", stats.size.toString(), Modifier.weight(1f))
            }
        }

        item {
            Text(
                "Last 7 days",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 8.dp)
            )
            SevenDayBars(daily, today)
        }

        if (topSongs.isNotEmpty()) {
            item {
                Text(
                    "Top songs",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 22.dp, end = 16.dp, bottom = 6.dp)
                )
            }
            items(topSongs, key = { it.song.url }) { stat ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { PlayerClient.playDiscovery(stat.song) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Art(stat.song.thumb, Modifier.size(52.dp), RoundedCornerShape(10.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stat.song.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            stat.song.artist,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(statDuration(stat.listenedMs), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text("${stat.playCount} plays", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (topArtists.isNotEmpty()) {
            item {
                Text(
                    "Top artists",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 22.dp, end = 16.dp, bottom = 6.dp)
                )
            }
            items(topArtists, key = { it.first }) { artist ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(artist.first, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(statDuration(artist.second), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Text("${artist.third} plays", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (stats.isEmpty()) {
            item {
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 220.dp).padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Abhi stats ban rahe hain. Gaane suno — DeepEcho listening time, top songs aur top artists locally track karega.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            item {
                TextButton(
                    onClick = Store::clearListeningStats,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
                ) { Text("Reset listening stats") }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SevenDayBars(daily: Map<String, Long>, today: LocalDate) {
    val days = remember(daily, today) {
        (6L downTo 0L).map { offset ->
            val date = today.minusDays(offset)
            date to (daily[date.toString()] ?: 0L)
        }
    }
    val max = (days.maxOfOrNull { it.second } ?: 1L).coerceAtLeast(1L)

    Row(
        Modifier.fillMaxWidth().height(126.dp).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        days.forEach { (date, value) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (value > 0L) statDuration(value) else "",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(3.dp))
                Box(
                    Modifier.fillMaxWidth()
                        .height(((value.toFloat() / max.toFloat()) * 76f).coerceAtLeast(5f).dp)
                        .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.78f))
                )
                Spacer(Modifier.height(4.dp))
                Text(date.dayOfWeek.name.take(3), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
