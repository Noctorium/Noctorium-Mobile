package app.noctorium.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.core.AppState
import app.noctorium.core.ArtistDetails
import app.noctorium.domain.Playlist
import app.noctorium.domain.Track
import coil.compose.AsyncImage

/**
 * An artist's page on the phone: their picture -- or four of their covers, where the service shows none -- "Artist" and
 * their name, the service, their songs and followers, what they say about themselves, Play, Shuffle and their page on
 * the service; then their songs, numbered. Back goes to wherever their name was tapped.
 */
@Composable
internal fun ArtistScreen(playlist: Playlist, artist: ArtistDetails, loading: Boolean, error: String?, state: AppState) {
    val playback by state.playback.collectAsState()
    ScreenScaffold {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(state::closePlaylist) { Icon(Glyph.ARROW_BACK.image, "Back") }
        }
        LazyColumn(Modifier.skinList(), contentPadding = chromePadding(24.dp)) {
            item(key = "artist:head") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ArtistPicture(artist.picture, playlist.tracks, Modifier.size(150.dp))
                    Spacer(Modifier.height(14.dp))
                    Text("ARTIST", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Text(
                        artist.name,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(
                            "On ${artist.provider.displayName}",
                            playlist.tracks.size.takeIf { it > 0 && !loading }?.let { if (it == 1) "1 song" else "$it songs" },
                            artist.followers?.let { "${followers(it)} followers" },
                        ).joinToString("  ·  "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                    artist.about?.let { about ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            about,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button({ state.playPlaylist(playlist) }, enabled = playlist.tracks.isNotEmpty()) {
                            Icon(Glyph.PLAY_ARROW.image, null); Spacer(Modifier.width(6.dp)); Text("Play")
                        }
                        FilledTonalButton({ state.shufflePlaylist(playlist) }, enabled = playlist.tracks.size > 1) {
                            Icon(Glyph.SHUFFLE.image, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Shuffle")
                        }
                    }
                    artist.pageUrl?.let { page ->
                        OutlinedButton({ state.openExternalUrl(page) }, Modifier.padding(top = 8.dp)) {
                            Icon(Glyph.OPEN_IN_NEW.image, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp))
                            Text(openOnLabel(artist.provider), maxLines = 1)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("Songs", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                }
            }
            when {
                loading && playlist.tracks.isEmpty() -> item(key = "artist:loading") {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    }
                }
                error != null -> item(key = "artist:problem") {
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(20.dp))
                }
                playlist.tracks.isEmpty() -> item(key = "artist:empty") {
                    EmptyNote("Nothing to play", "${artist.provider.displayName} has no songs by ${artist.name} to play.")
                }
                else -> itemsIndexed(playlist.tracks, key = { _, track -> track.queueKey }) { index, track ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${index + 1}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(30.dp),
                        )
                        Box(Modifier.weight(1f)) {
                            TrackRow(track, state, isCurrent = playback.track?.queueKey == track.queueKey) {
                                state.playPlaylist(playlist.copy(tracks = playlist.tracks), track)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** An artist's picture, round; or, where the service shows none, four of their covers in a circle. */
@Composable
private fun ArtistPicture(picture: String?, songs: List<Track>, modifier: Modifier) {
    val covers = remember(songs) { songs.mapNotNull { it.artworkUrl }.distinct().take(4) }
    Box(modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        when {
            picture != null -> AsyncImage(picture, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            covers.size >= 4 -> Column(Modifier.fillMaxSize()) {
                covers.chunked(2).forEach { pair ->
                    Row(Modifier.weight(1f)) {
                        pair.forEach { cover ->
                            AsyncImage(cover, null, Modifier.weight(1f).fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        }
                    }
                }
            }
            covers.isNotEmpty() -> AsyncImage(covers.first(), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            else -> Icon(Glyph.ACCOUNT_CIRCLE.image, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 950, 12K, 1.2M: followers as a page says them. */
internal fun followers(count: Long): String = when {
    count >= 1_000_000 -> String.format(java.util.Locale.ROOT, "%.1fM", count / 1_000_000.0).replace(".0M", "M")
    count >= 10_000 -> "${count / 1_000}K"
    count >= 1_000 -> String.format(java.util.Locale.ROOT, "%.1fK", count / 1_000.0).replace(".0K", "K")
    else -> count.toString()
}
