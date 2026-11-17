package app.noctorium.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import app.noctorium.core.AppState
import app.noctorium.domain.ProviderType
import app.noctorium.domain.Track

/** Whether [track] has an artist with a page to open: not a station, not a file on the phone, not a blank name. */
internal fun opensArtist(track: Track?): Boolean =
    track != null && track.provider != ProviderType.RADIO && track.provider != ProviderType.LOCAL && track.artistLine.isNotBlank()

/**
 * The artist's name under a song, made into the way to their page: tapping it opens the artist on the service the song
 * came from -- YouTube Music's artist for a YouTube Music song, the SoundCloud profile for a SoundCloud one -- in the
 * library, as their songs. Nothing for a song with no artist to open.
 */
internal fun Modifier.opensArtistOf(track: Track?, state: AppState): Modifier =
    if (!opensArtist(track)) this
    else clickable(role = Role.Button, onClickLabel = "Open the artist") { state.openArtistOf(track!!) }
