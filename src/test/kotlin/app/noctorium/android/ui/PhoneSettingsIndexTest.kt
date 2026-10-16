package app.noctorium.android.ui

import app.noctorium.settings.ConnectPreferences
import app.noctorium.settings.NoctoriumPreferences
import app.noctorium.settings.PhonePreferences
import app.noctorium.settings.SettingMatchKind
import app.noctorium.settings.UpdatePreferences
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The settings search on the phone: every setting has an entry it can be found by, and the words people use find
 * the setting they mean.
 *
 * "Every setting" is held to by the settings file itself: every preference the phone keeps is changed by some
 * entry, or is said not to be a setting on the phone and why. A setting added to Base without a way to find it
 * here fails this, rather than quietly being missing from the search.
 */
class PhoneSettingsIndexTest {

    private fun names(descriptor: SerialDescriptor, prefix: String = ""): List<String> =
        (0 until descriptor.elementsCount).map { prefix + descriptor.getElementName(it) }

    /** Every preference as the settings file names it, the phone's own and Connect's and the updater's inside theirs. */
    private val everyPreference: List<String> =
        names(NoctoriumPreferences.serializer().descriptor).filterNot { it in setOf("phone", "connect", "updates") } +
            names(PhonePreferences.serializer().descriptor, "phone.") +
            names(ConnectPreferences.serializer().descriptor, "connect.") +
            names(UpdatePreferences.serializer().descriptor, "updates.")

    @Test
    fun `every preference the phone keeps is a setting the search can find, or is said not to be one`() {
        val claimed = PHONE_SETTINGS.flatMap(PhoneSetting::stores).toSet() + NOT_PHONE_SETTINGS.keys
        val missing = everyPreference.filter { it !in claimed }
        assertTrue(missing.isEmpty(), "No settings entry changes $missing: add one to PHONE_SETTINGS, or say why not in NOT_PHONE_SETTINGS")
        val unknown = claimed.filter { it !in everyPreference }
        assertTrue(unknown.isEmpty(), "Entries name preferences that do not exist: $unknown")
    }

    @Test
    fun `every setting has a name of its own and lives on a page`() {
        assertEquals(PHONE_SETTINGS.size, PHONE_SETTINGS.map(PhoneSetting::id).toSet().size, "ids are unique")
        assertEquals(PHONE_SETTINGS.size, PHONE_SETTINGS.map(PhoneSetting::title).toSet().size, "titles are unique")
        PHONE_SETTINGS.forEach { setting ->
            assertTrue(setting.title.isNotBlank() && setting.description.isNotBlank(), "${setting.id} says what it is")
            assertTrue(setting.entry.section.startsWith(setting.page.section.title), "${setting.id} is found under its section")
        }
    }

    @Test
    fun `every page has a setting the search can lead to`() {
        val pages = PHONE_SETTINGS.map(PhoneSetting::page).toSet()
        assertEquals(SettingsPage.entries.toSet(), pages)
    }

    @Test
    fun `the setting ids the cards mark are the ones the search knows`() {
        val ids = SettingIds::class.java.declaredFields
            .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .map { it.isAccessible = true; it.get(null) as String }
            .toSet()
        assertEquals(ids, PHONE_SETTINGS.map(PhoneSetting::id).toSet())
    }

    private fun first(query: String): PhoneSetting = searchPhoneSettings(query).first().first

    @Test
    fun `the words people use find the settings they mean`() {
        assertEquals(SettingIds.THEME, first("dark mode").id)
        assertEquals(SettingIds.THEME, first("light mode").id)
        assertEquals(SettingIds.VOLUME_BOOSTER, first("louder").id)
        assertEquals(SettingIds.VOLUME_BOOSTER, first("boost").id)
        assertEquals(SettingIds.EQUALISER, first("equalizer").id)
        assertEquals(SettingIds.EQUALISER, first("eq").id)
        assertEquals(SettingIds.VISUALISER, first("visualizer").id)
        assertEquals(SettingIds.ICON_SET, first("icons").id)
        assertEquals(SettingIds.SEEK_BAR, first("progress bar").id)
        assertEquals(SettingIds.SPEED, first("tempo").id)
        assertEquals(SettingIds.HAPTICS, first("vibration").id)
        assertEquals(SettingIds.KEEP_PLAYING, first("battery").id)
        assertEquals(SettingIds.LAST_FM, first("scrobble").id)
        assertEquals(SettingIds.HOME_PLAY, first("start radio").id)
        // The history on the profile page, before Home's row of the songs played lately.
        assertEquals(SettingIds.HISTORY, first("history").id)
        assertEquals(SettingIds.HISTORY, first("recently played").id)
        assertEquals(SettingIds.CLEAR_HISTORY, first("clear history").id)
        assertEquals(SettingsPage.PROFILE, first("history").page)
    }

    @Test
    fun `the badges' marks are found by what people call them, beside the icon set`() {
        assertEquals(SettingIds.SERVICE_BADGE_STYLE, first("service icons").id)
        assertEquals(SettingIds.SERVICE_BADGE_STYLE, first("badge style").id)
        assertEquals(SettingIds.SERVICE_BADGE_STYLE, first("marks").id)
        assertEquals(SettingIds.SERVICE_BADGE_STYLE, first("ytm").id)
        for (words in listOf("logo", "icons", "badges", "letters")) {
            assertTrue(searchPhoneSettings(words).any { it.first.id == SettingIds.SERVICE_BADGE_STYLE }, "\"$words\" does not find the badges")
        }
        assertEquals(SettingIds.ICON_SET, first("icons").id)
        assertEquals(SettingsPage.ICONS, first("marks").page)
    }

    @Test
    fun `Libre fm is found by its name and by scrobbling, after Last fm`() {
        for (words in listOf("libre.fm", "libre", "librefm", "libre fm", "gnu fm")) {
            assertEquals(SettingIds.LIBRE_FM, first(words).id, words)
        }
        assertEquals(SettingsPage.SCROBBLING, first("libre").page)
        val scrobblers = searchPhoneSettings("scrobble").map { it.first.id }
        assertEquals(listOf(SettingIds.LAST_FM, SettingIds.LIBRE_FM, SettingIds.LISTENBRAINZ), scrobblers.take(3))
    }

    /** The link kept while a sign-in waits, for a browser that did not open, is found by what its button says. */
    @Test
    fun `copy link finds the scrobblers whose sign-in page can be copied`() {
        assertEquals(listOf(SettingIds.LAST_FM, SettingIds.LIBRE_FM), searchPhoneSettings("copy link").map { it.first.id })
        assertEquals(SettingIds.LAST_FM, first("browser did not open").id)
    }

    /** Apple Music, Deezer and Tidal by name, and the parts of their setting up people look for by name. */
    @Test
    fun `Apple Music, Deezer and Tidal are found by name, with the developer token and the client id`() {
        assertEquals(SettingIds.APPLE_MUSIC_SIGN_IN, first("apple music").id)
        assertEquals(SettingIds.APPLE_MUSIC_SIGN_IN, first("apple").id)
        assertEquals(SettingsPage.APPLE_MUSIC, first("apple music").page)
        assertEquals(SettingIds.DEEZER_PROFILE, first("deezer").id)
        assertEquals(SettingsPage.DEEZER, first("deezer").page)
        assertEquals(SettingIds.TIDAL_SIGN_IN, first("tidal").id)
        assertEquals(SettingsPage.TIDAL, first("tidal").page)
        assertEquals(SettingIds.APPLE_MUSIC_DEVELOPER_TOKEN, first("developer token").id)
        assertEquals(SettingIds.APPLE_MUSIC_DEVELOPER_TOKEN, first("musickit").id)
        assertEquals(SettingIds.APPLE_MUSIC_SIGN_IN, first("media-user-token").id)
        // Spotify's own app and Tidal's both take a client id, and both are found by it.
        val clientId = searchPhoneSettings("client id").map { it.first.id }
        assertEquals(setOf(SettingIds.SPOTIFY_OWN_APP, SettingIds.TIDAL_APP), clientId.take(2).toSet())
        assertEquals(SettingIds.TIDAL_APP, first("client secret").id)
        assertEquals(SettingsPage.TIDAL, first("client secret").page)
        assertEquals(SettingIds.TIDAL_APP, first("developer.tidal.com").id)
    }

    @Test
    fun `each Toolbar is found by its name`() {
        for (words in listOf("toolbar", "toolbar 2", "toolbar2")) {
            assertEquals(SettingIds.PLAYER_BAR_LAYOUT, first(words).id, words)
        }
    }

    /** The Connect tab is found by what people call it, and opened where it is: on its tab, not in Settings. */
    @Test
    fun `the Connect tab is found by the words for it, and opens the tab`() {
        for (words in listOf("devices", "connect tab", "play on computer", "where is the music")) {
            val (setting, match) = searchPhoneSettings(words).first()
            assertEquals(SettingIds.CONNECT_TAB, setting.id, words)
            assertEquals(app.noctorium.core.Destination.CONNECT, setting.tab)
            assertTrue(resultDetail(setting, match).startsWith("Connect tab"), resultDetail(setting, match))
        }
        assertEquals(SettingIds.CONNECT_ADDRESS, first("ip address").id)
        assertEquals(app.noctorium.core.Destination.CONNECT, first("ip address").tab)
        // The settings that are in Settings still open there.
        assertEquals(null, first("dark mode").tab)
    }

    /** The switch is found by its name and by every way of saying "turn it off", and opens in Settings, at its card. */
    @Test
    fun `the Connect switch is found by its name, and by turning it off`() {
        for (words in listOf("connect", "noctorium connect", "turn off connect", "disable connect", "switch off connect", "enable connect", "turn on connect")) {
            val (setting, _) = searchPhoneSettings(words).first()
            assertEquals(SettingIds.CONNECT_ENABLED, setting.id, words)
            assertEquals(SettingsPage.CONNECT, setting.page, words)
            assertEquals(null, setting.tab, words)
        }
        val (setting, match) = searchPhoneSettings("disable connect").first()
        assertEquals("This phone › Noctorium Connect · also “disable connect”", resultDetail(setting, match))
        // The tab is still found by the words for it, and the address still by its own.
        assertEquals(SettingIds.CONNECT_TAB, first("devices").id)
        assertEquals(SettingIds.CONNECT_ADDRESS, first("ip address").id)
    }

    @Test
    fun `a setting is found by the start of its name before anything else`() {
        val found = searchPhoneSettings("vol")
        assertEquals(SettingIds.VOLUME_BOOSTER, found.first().first.id)
        assertEquals(SettingMatchKind.TITLE_START, found.first().second.kind)
    }

    @Test
    fun `a typo in a long word is forgiven`() {
        assertEquals(SettingIds.VISUALISER, first("visualsier").id)
        assertEquals(SettingIds.ANIMATIONS, first("animatoins").id)
    }

    @Test
    fun `nothing typed finds nothing, and nothing that is not a setting finds nothing`() {
        assertTrue(searchPhoneSettings("").isEmpty())
        assertTrue(searchPhoneSettings("zebra crossing").isEmpty())
    }

    @Test
    fun `a result says where the setting is, and the other word it was found by`() {
        val (setting, match) = searchPhoneSettings("dark mode").first()
        assertEquals("Look › Theme and colour · also “dark mode”", resultDetail(setting, match))
        val (history, how) = searchPhoneSettings("watch history").first()
        assertTrue(resultDetail(history, how).endsWith("Once signed in to YouTube Music."))
    }
}
