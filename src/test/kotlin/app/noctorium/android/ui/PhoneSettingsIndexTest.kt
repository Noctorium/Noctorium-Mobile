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
