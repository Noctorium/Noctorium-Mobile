package app.noctorium.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.core.AppState
import app.noctorium.core.AppUiState
import app.noctorium.core.SearchKind
import app.noctorium.domain.Track
import app.noctorium.radio.RadioCountry

/*
 * Radio in the search, the phone's way of what the desktop has: the search box's own small pill says Music or
 * Radio -- Music unless the listener picks the other, and Music again each time Noctorium starts -- and with Radio
 * chosen the same box finds stations from anywhere in the world, from Radio Browser, with a country to narrow them
 * to where the service chips would be.
 */

/** The glyph each kind is drawn with: a note for songs, a set for stations. */
internal fun SearchKind.glyph(): Glyph = if (this == SearchKind.RADIO) Glyph.RADIO else Glyph.MUSIC_NOTE

/** What each kind of search finds, under its name in the menu. */
internal fun SearchKind.about(): String = when (this) {
    SearchKind.MUSIC -> "Songs, artists and albums from your services"
    SearchKind.RADIO -> "Live stations from every country"
}

/** What the search box asks for, for each kind. */
internal fun SearchKind.placeholder(): String = when (this) {
    SearchKind.MUSIC -> "Songs, albums and artists"
    SearchKind.RADIO -> "Stations, cities or genres"
}

/**
 * The search's Music / Radio switch, just above the box rather than in it, so the box is only a search box: the kind
 * chosen as a glyph, a word and a caret, opening a menu of the two. No fill and no border -- small text in the
 * accent, with a touch target that is still a finger wide.
 */
@Composable
internal fun SearchKindPill(kind: SearchKind, choose: (SearchKind) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    Box(modifier) {
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { open = true }
                .padding(start = 4.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(kind.glyph().image, null, Modifier.size(14.dp), tint = accent)
            Spacer(Modifier.width(4.dp))
            Text(kind.displayName, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = accent)
            Icon(Glyph.EXPAND_MORE.image, "Music or radio", Modifier.size(16.dp), tint = accent)
        }
        DropdownMenu(open, { open = false }) {
            SearchKind.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(option.displayName, fontWeight = FontWeight.SemiBold)
                            Text(option.about(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    onClick = {
                        open = false
                        choose(option)
                    },
                    leadingIcon = { Icon(option.glyph().image, null) },
                    trailingIcon = { if (option == kind) Icon(Glyph.CHECK.image, null, tint = accent) },
                )
            }
        }
    }
}

/** "Germany", or "Everywhere" for no country. */
internal fun radioCountryName(code: String?, countries: List<RadioCountry>): String =
    code?.let { c -> countries.firstOrNull { it.code == c }?.name ?: c } ?: "Everywhere"

/** The countries [filter] picks out, by name or by code, as the picker lists them. */
internal fun filterRadioCountries(countries: List<RadioCountry>, filter: String): List<RadioCountry> {
    val wanted = filter.trim().lowercase()
    if (wanted.isEmpty()) return countries
    return countries.filter { it.name.lowercase().contains(wanted) || it.code.lowercase() == wanted }
}

/**
 * Where the radio search looks, in place of the service chips: everywhere, or one country picked from all of them --
 * a couple of hundred, so the menu has a box at its top to type a name into.
 */
@Composable
internal fun RadioCountryPicker(ui: AppUiState, choose: (String?) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }
    val chosen = ui.radioCountries.firstOrNull { it.code == ui.radioCountry }
    Box(modifier) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .7f))
                .clickable { open = true; filter = "" }
                .padding(start = 12.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A flag once a country is chosen -- Android draws them -- and the globe for the whole world.
            if (chosen != null && chosen.flag.isNotEmpty()) {
                Text(chosen.flag, fontSize = 15.sp)
            } else {
                Icon(Glyph.PUBLIC.image, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(7.dp))
            Text(radioCountryName(ui.radioCountry, ui.radioCountries), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Icon(Glyph.EXPAND_MORE.image, "Country", Modifier.size(20.dp))
        }
        DropdownMenu(open, { open = false }, Modifier.width(290.dp).heightIn(max = 420.dp)) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                placeholder = { Text("Find a country") },
                leadingIcon = { Icon(Glyph.SEARCH.image, null, Modifier.size(18.dp)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            )
            if (filter.isBlank()) {
                DropdownMenuItem(
                    text = { Text("Everywhere") },
                    onClick = { open = false; choose(null) },
                    leadingIcon = { Icon(Glyph.PUBLIC.image, null) },
                    trailingIcon = { if (ui.radioCountry == null) Icon(Glyph.CHECK.image, null, tint = MaterialTheme.colorScheme.primary) },
                )
            }
            val shown = filterRadioCountries(ui.radioCountries, filter)
            val quiet = MaterialTheme.colorScheme.onSurfaceVariant
            if (ui.radioCountries.isEmpty()) {
                Text("Fetching the countries…", fontSize = 12.sp, color = quiet, modifier = Modifier.padding(14.dp))
            } else if (shown.isEmpty()) {
                Text("No country called “$filter”.", fontSize = 12.sp, color = quiet, modifier = Modifier.padding(14.dp))
            }
            shown.forEach { country ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(country.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${country.stations}", fontSize = 11.sp, color = quiet)
                        }
                    },
                    onClick = { open = false; choose(country.code) },
                    leadingIcon = {
                        Text(country.flag.ifEmpty { country.code }, fontSize = if (country.flag.isEmpty()) 11.sp else 17.sp, modifier = Modifier.width(26.dp))
                    },
                    trailingIcon = { if (ui.radioCountry == country.code) Icon(Glyph.CHECK.image, null, tint = MaterialTheme.colorScheme.primary) },
                )
            }
        }
    }
}

/** What the station list is headed with: the most listened-to somewhere, or the stations matching the words. */
internal fun radioHeading(ui: AppUiState): String {
    val where = radioCountryName(ui.radioCountry, ui.radioCountries)
    return when {
        ui.searchQuery.isBlank() && ui.radioCountry == null -> "Most listened-to around the world"
        ui.searchQuery.isBlank() -> "Most listened-to in $where"
        ui.radioCountry == null -> "Stations for “${ui.searchQuery.trim()}”"
        else -> "Stations for “${ui.searchQuery.trim()}” in $where"
    }
}

/** The search page with Radio chosen: the stations found, or why there are none. */
@Composable
internal fun RadioResults(ui: AppUiState, state: AppState, playing: Track?) {
    when {
        ui.searchLoading && ui.radioStations.isEmpty() ->
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp))
        ui.errorMessage != null && ui.radioStations.isEmpty() -> EmptyNote("Radio is out of reach", ui.errorMessage.orEmpty())
        ui.radioStations.isEmpty() -> EmptyNote(
            "No stations found",
            "Try another name, a kind of music such as “jazz”, or Everywhere.",
        )
        else -> LazyColumn(Modifier.skinList(), contentPadding = chromePadding(24.dp)) {
            item(key = "radio:heading") {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 6.dp)) {
                    Text(radioHeading(ui), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(
                        "From Radio Browser, an open list of the world's stations. Tap one to tune in.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(ui.radioStations, key = { it.queueKey }) { station ->
                val onAir = playing?.queueKey == station.queueKey
                TrackRow(
                    station,
                    state,
                    isCurrent = onAir,
                    // A station is not a song to like, download or add to a playlist, so it has no song menu.
                    trailing = {
                        if (onAir) {
                            Text(
                                "ON AIR",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        } else {
                            ProviderBadge(station, Modifier.padding(horizontal = 8.dp))
                        }
                    },
                ) { state.playStation(station) }
            }
        }
    }
}
