package app.noctorium.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.core.AppState
import app.noctorium.domain.ProviderType
import app.noctorium.settings.SettingsState

/*
 * The services that came after VK, on their pages in Settings: Apple Music, Deezer and Tidal. None of them plays
 * its own audio anywhere but in its own apps, so every one of their songs plays matched to the same recording on
 * YouTube Music, as a Spotify song does without Premium; each card says so once.
 *
 * Each is set up its own way, as Base reaches it: Apple Music by signing in on Apple's own page inside Noctorium, as
 * VK is; Deezer by naming a public profile, as Bandcamp is by a fan's name; and Tidal through a Tidal app of the
 * listener's own and a sign-in in the browser, as Spotify's own app is.
 */

/** What every one of the three says about where its songs are played from. */
private const val MATCHED = "Its songs play matched to the same recordings on YouTube Music: its own audio plays only in its own apps."

/**
 * Apple Music, through the token Apple's web player keeps once somebody is signed in to it.
 *
 * Searching it, its albums, artists and playlists, and its links answer anybody; signing in adds the library and
 * Home's rows. The sign-in is Apple's own page inside Noctorium, which hands the token over by itself; pasting the
 * web player's media-user-token cookie from a browser is the way round a page that will not finish, as VK's
 * cookies are.
 */
@Composable
internal fun AppleMusicCard(settings: SettingsState, state: AppState, signIn: (ProviderType) -> Unit) {
    val apple = settings.appleMusic
    val focus = LocalFocusManager.current
    var pasteOpen by remember { mutableStateOf(false) }
    var pasted by remember { mutableStateOf("") }

    fun usePasted() {
        if (pasted.isBlank() || apple.checking) return
        focus.clearFocus()
        state.completeAppleMusicSignIn(pasted)
        // Gone from the screen and from here as soon as it is handed over: it is a sign-in, not a note.
        pasted = ""
    }

    SettingsCardShell(anchor = SettingIds.APPLE_MUSIC_SIGN_IN) {
        CardHeading(SettingsPage.APPLE_MUSIC.glyph.image, "Apple Music", tint = ProviderType.APPLE_MUSIC.tint())
        Spacer(Modifier.height(6.dp))
        if (apple.connected) {
            Text("Signed in.", fontSize = 12.sp)
            Text(
                "Your playlists, albums and songs are in the library, and Home has rows from Apple Music. $MATCHED",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            Text(
                "Searching Apple Music, its albums, artists and playlists, and its links work without signing in. " +
                    "Sign in on Apple's own page for your library and rows on Home. $MATCHED",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
        if (apple.storefront.isNotBlank()) {
            Text(
                "Storefront: ${apple.storefront.uppercase()}" +
                    if (apple.connected) ", your account's." else ", from this phone's country until you sign in.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        if (apple.connected) {
            SkinnedOutlinedButton(state::disconnectAppleMusic, enabled = !apple.checking) { Text("Disconnect") }
        } else {
            SkinnedButton({ signIn(ProviderType.APPLE_MUSIC) }, enabled = !apple.checking) { Text("Sign in on music.apple.com") }
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth().clickable { pasteOpen = !pasteOpen }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Paste the media-user-token cookie instead", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Icon(if (pasteOpen) Glyph.EXPAND_LESS.image else Glyph.EXPAND_MORE.image, if (pasteOpen) "Fold away" else "Show")
            }
            if (pasteOpen) {
                Text(
                    "From a browser signed in on music.apple.com: the media-user-token cookie there, on its own or as " +
                        "a Cookie: line, however the browser copies it.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    pasted,
                    { pasted = it.take(8_000) },
                    label = { Text("media-user-token") },
                    // Hidden as a password is: whoever holds it is signed in to Apple Music as you.
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { usePasted() }),
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                SkinnedButton({ usePasted() }, enabled = pasted.isNotBlank() && !apple.checking) { Text("Use this") }
            }
        }
        if (apple.checking) Checking("Checking with Apple…")
        apple.message?.let { Note(it) }
    }
}

/**
 * A MusicKit developer token of the listener's own, folded away: Apple Music is read with the one its web player is
 * served unless one is given, and this is for somebody who has an Apple Developer account and would rather use it.
 */
@Composable
internal fun AppleMusicTokenCard(settings: SettingsState, state: AppState) {
    val apple = settings.appleMusic
    val focus = LocalFocusManager.current
    var open by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }

    SettingsCardShell(anchor = SettingIds.APPLE_MUSIC_DEVELOPER_TOKEN) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Your own developer token", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    if (apple.ownDeveloperToken) "In use." else "Optional. Without one, Apple Music is read as its web player reads it.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
            Icon(if (open) Glyph.EXPAND_LESS.image else Glyph.EXPAND_MORE.image, if (open) "Fold away" else "Show")
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            Text(
                "A MusicKit developer token you made with a key from your own Apple Developer account. It is kept in " +
                    "this phone's secure store and never shown again.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                token,
                { token = it.trim().take(4_000) },
                label = { Text("Developer token") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SkinnedButton({ focus.clearFocus(); state.setAppleMusicDeveloperToken(token); token = "" }, enabled = token.isNotBlank()) {
                    Text("Use this token")
                }
                if (apple.ownDeveloperToken) {
                    SkinnedOutlinedButton({ focus.clearFocus(); token = ""; state.setAppleMusicDeveloperToken("") }) { Text("Remove") }
                }
            }
        }
    }
}

/**
 * Deezer, which needs no sign-in: a public profile is shown to anybody, so its link or its number is all it takes to
 * bring its playlists, loved tracks and albums into the library, with Deezer's charts and editors' picks on Home.
 *
 * Checked with Deezer when it is saved, as Bandcamp's name is, so a mistyped number is a message rather than an empty
 * library. Searching Deezer and its links want no profile at all, and the card says so.
 */
@Composable
internal fun DeezerCard(settings: SettingsState, state: AppState) {
    val deezer = settings.deezer
    val saved = deezer.profileId
    val focus = LocalFocusManager.current
    var typed by remember(saved) { mutableStateOf(saved) }
    // Only a profile other than the one kept is worth asking Deezer about; a share link says which only once followed.
    val number = deezerProfileNumber(typed)
    val canSave = !deezer.checking && typed.isNotBlank() && (number == null || number != saved)

    fun save() {
        if (!canSave) return
        focus.clearFocus()
        state.setDeezerProfile(typed)
    }

    SettingsCardShell(anchor = SettingIds.DEEZER_PROFILE) {
        CardHeading(SettingsPage.DEEZER.glyph.image, "Deezer", tint = ProviderType.DEEZER.tint())
        Spacer(Modifier.height(6.dp))
        Text(
            "No sign-in is needed. Give your Deezer profile, by its link or the number at the end of " +
                "deezer.com/profile/<number>, and its public playlists, loved tracks and albums appear in the library, " +
                "with Deezer's charts and editors' picks on Home. Searching Deezer and its links work without one.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
        Text(MATCHED, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            typed,
            { typed = it.take(300) },
            label = { Text("Your Deezer profile") },
            placeholder = { Text("deezer.com/profile/123456") },
            singleLine = true,
            supportingText = { Text("Its link, a share link, or its number.") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkinnedButton({ save() }, enabled = canSave) { Text("Save") }
            if (saved.isNotBlank()) {
                SkinnedOutlinedButton({ focus.clearFocus(); state.setDeezerProfile("") }, enabled = !deezer.checking) { Text("Remove") }
            }
        }
        deezerStatus(deezer)?.let { status ->
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (deezer.checking) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(status, fontSize = 12.sp)
            }
        }
        deezer.message?.let { Note(it) }
    }
}

/**
 * Tidal's sign-in, on Tidal's own page in the browser, with the reply caught on this phone as Spotify's is.
 *
 * There is no Tidal app of Noctorium's own yet, so until the listener has given one of theirs (see [TidalAppCard])
 * there is nothing to sign in through, and the card says that first, in so many words, rather than offering a
 * Connect that can only fail.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TidalCard(settings: SettingsState, state: AppState) {
    val tidal = settings.tidal
    SettingsCardShell(anchor = SettingIds.TIDAL_SIGN_IN) {
        CardHeading(SettingsPage.TIDAL.glyph.image, "Tidal", tint = ProviderType.TIDAL.tint())
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                !tidal.configured ->
                    "Tidal lets other apps in only through an app registered with it, and Noctorium has none of its own " +
                        "yet. Register one of your own, free, at developer.tidal.com and give its client id below; then " +
                        "connect here."
                tidal.connected && tidal.accountName.isNotBlank() ->
                    "Signed in as ${tidal.accountName}. Your collection is in the library, and your mixes are on Home."
                tidal.connected -> "Signed in. Your collection is in the library, and your mixes are on Home."
                tidal.readsCatalogue ->
                    "Tidal can be searched already. Connect it to bring your collection into the library and your " +
                        "mixes to Home: you sign in on Tidal's own page, in your browser."
                else ->
                    "Connect Tidal to search it, and to bring your collection into the library and your mixes to Home. " +
                        "You sign in on Tidal's own page, in your browser."
            },
            color = if (tidal.configured) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
        )
        Text(MATCHED, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (tidal.connected) {
                SkinnedOutlinedButton(state::connectTidal, enabled = !tidal.connecting) { Text("Reconnect") }
                SkinnedOutlinedButton(state::disconnectTidal, enabled = !tidal.connecting) { Text("Disconnect") }
            } else {
                SkinnedButton(state::connectTidal, enabled = tidal.configured && !tidal.connecting) { Text("Connect Tidal") }
            }
        }
        if (tidal.connecting) Checking("Waiting for Tidal in your browser…")
        tidal.message?.let { Note(it) }
    }
}

/**
 * The Tidal app Noctorium signs in through: one the listener registers at developer.tidal.com, with this phone's
 * redirect address among its redirect URIs, and its client id here -- and its secret too, if Tidal is to be searched
 * without signing in.
 *
 * Open until there is one, since nothing works without it, and folded away once there is. Both are kept in the
 * credential store and never read back, so the fields start empty and the heading says whether one is in use.
 */
@Composable
internal fun TidalAppCard(settings: SettingsState, state: AppState) {
    val tidal = settings.tidal
    val focus = LocalFocusManager.current
    var open by remember { mutableStateOf(!tidal.ownApp) }
    var clientId by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }

    SettingsCardShell(anchor = SettingIds.TIDAL_APP) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Your Tidal app", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    when {
                        tidal.ownApp -> "In use."
                        tidal.configured -> "Optional while Noctorium's own is in use."
                        else -> "Needed until Noctorium has a Tidal app of its own."
                    },
                    color = if (tidal.configured) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    fontSize = 11.sp,
                )
            }
            Icon(if (open) Glyph.EXPAND_LESS.image else Glyph.EXPAND_MORE.image, if (open) "Fold away" else "Show")
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Create an app at developer.tidal.com, add this address to its redirect URIs exactly as it is written, " +
                    "then give its client id below. Its secret is optional: with it, Tidal can be searched without " +
                    "signing in.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tidal.redirectUri, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                IconButton(state::copyTidalRedirectUri, Modifier.size(30.dp)) {
                    Icon(Glyph.CONTENT_COPY.image, "Copy the redirect address", Modifier.size(15.dp))
                }
            }
            SkinnedOutlinedButton(state::openTidalDeveloperPortal) {
                Icon(Glyph.OPEN_IN_NEW.image, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Open developer.tidal.com")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                clientId,
                { clientId = it.trim().take(128) },
                label = { Text("Client id") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                secret,
                { secret = it.trim().take(256) },
                label = { Text("Client secret, if you like") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                supportingText = { Text("Both are kept in this phone's secure store.") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SkinnedButton(
                    {
                        focus.clearFocus()
                        state.configureTidalApplication(clientId, secret)
                        // Kept in the credential store now, and not on the screen.
                        clientId = ""
                        secret = ""
                    },
                    enabled = clientId.isNotBlank() && !tidal.connecting,
                ) { Icon(Glyph.SAVE.image, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Use this app") }
                if (tidal.ownApp) {
                    SkinnedOutlinedButton({ focus.clearFocus(); clientId = ""; secret = ""; state.configureTidalApplication("", "") }) {
                        Text("Remove")
                    }
                }
            }
        }
    }
}

/** A spinner with what is being waited for, under a card's buttons. */
@Composable
private fun Checking(what: String) {
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text(what, fontSize = 12.sp)
    }
}

/** What the service said about the last thing done, under everything else on its card. */
@Composable
private fun Note(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
}
