package app.noctorium.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Which phone maker a phone is by, the pages of its own settings tried for each, and where it falls back to. */
class PhoneMakersTest {

    @Test
    fun `a phone is known by its brand first, then by its maker`() {
        assertEquals(PhoneMaker.XIAOMI, PhoneMaker.of("Xiaomi", "Redmi"))
        assertEquals(PhoneMaker.XIAOMI, PhoneMaker.of("Xiaomi", "POCO"))
        assertEquals(PhoneMaker.XIAOMI, PhoneMaker.of("Xiaomi", "Xiaomi"))
        assertEquals(PhoneMaker.HONOR, PhoneMaker.of("HONOR", "HONOR"))
        // An Honor from before Honor was its own company says Huawei as its maker.
        assertEquals(PhoneMaker.HONOR, PhoneMaker.of("HUAWEI", "HONOR"))
        assertEquals(PhoneMaker.HUAWEI, PhoneMaker.of("HUAWEI", "HUAWEI"))
        assertEquals(PhoneMaker.SAMSUNG, PhoneMaker.of("samsung", "samsung"))
        assertEquals(PhoneMaker.ONEPLUS, PhoneMaker.of("OnePlus", "OnePlus"))
        assertEquals(PhoneMaker.OPPO, PhoneMaker.of("OPPO", "OPPO"))
        assertEquals(PhoneMaker.REALME, PhoneMaker.of("realme", "realme"))
        assertEquals(PhoneMaker.VIVO, PhoneMaker.of("vivo", "iQOO"))
        assertEquals(PhoneMaker.INFINIX, PhoneMaker.of("INFINIX MOBILITY LIMITED", "Infinix"))
        assertEquals(PhoneMaker.TECNO, PhoneMaker.of("TECNO MOBILE LIMITED", "TECNO"))
        assertEquals(PhoneMaker.ITEL, PhoneMaker.of("ITEL", "Itel"))
        assertEquals(PhoneMaker.NOKIA, PhoneMaker.of("HMD Global", "Nokia"))
        assertEquals(PhoneMaker.ASUS, PhoneMaker.of("asus", "asus"))
        assertEquals(PhoneMaker.MEIZU, PhoneMaker.of("Meizu", "meizu"))
        assertEquals(PhoneMaker.MOTOROLA, PhoneMaker.of("motorola", "motorola"))
        assertEquals(PhoneMaker.LENOVO, PhoneMaker.of("LENOVO", "Lenovo"))
        assertEquals(PhoneMaker.LETV, PhoneMaker.of("LeMobile", "Letv"))
    }

    @Test
    fun `a phone whose maker leaves apps alone is no maker of these`() {
        assertNull(PhoneMaker.of("Google", "google"))
        assertNull(PhoneMaker.of("Fairphone", "Fairphone"))
        assertNull(PhoneMaker.of("", ""))
        assertNull(PhoneMaker.of(null, null))
        // A name begun by a known one is not that one.
        assertNull(PhoneMaker.of("Honorable Phones", "Honorable"))
    }

    @Test
    fun `Xiaomi's autostart is tried at its manager, then at Noctorium's own permissions, and its battery saver is the power keeper's`() {
        val autostart = assertNotNull(PhoneMaker.XIAOMI.page(MakerPageKind.AUTOSTART))
        assertEquals(
            listOf(
                "com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity",
                "com.miui.securitycenter/com.miui.permcenter.permissions.PermissionsEditorActivity",
            ),
            autostart.targets.map { "${it.packageName}/${it.className}" },
        )
        assertEquals(listOf("extra_pkgname"), autostart.targets[1].packageExtras)
        val battery = assertNotNull(PhoneMaker.XIAOMI.page(MakerPageKind.BATTERY))
        val keeper = battery.targets.single()
        assertEquals("com.miui.powerkeeper/com.miui.powerkeeper.ui.HiddenAppsConfigActivity", "${keeper.packageName}/${keeper.className}")
        assertEquals(listOf("package_name"), keeper.packageExtras)
        assertEquals("package_label", keeper.labelExtra)
        assertNotNull(PhoneMaker.XIAOMI.tip)
    }

    /**
     * Found on a Redmi with Android 14: without Autostart nothing brought Noctorium back, and with it but without
     * Android's exemption every start from the background was refused. So both are needed there, and said to be;
     * Autostart cannot be read back, so it says so too rather than sitting unticked like something optional.
     */
    @Test
    fun `on Xiaomi's phones both Autostart and Android's exemption are needed, and only there are they said to be`() {
        val autostart = PhoneMaker.XIAOMI.page(MakerPageKind.AUTOSTART)!!
        assertTrue(autostart.needed)
        assertTrue("never ticked" in autostart.detail)
        assertTrue(PhoneMaker.XIAOMI.needsBackgroundExemption)
        assertFalse(PhoneMaker.XIAOMI.page(MakerPageKind.BATTERY)!!.needed)
        for (maker in PhoneMaker.entries - PhoneMaker.XIAOMI) {
            assertFalse(maker.needsBackgroundExemption, maker.displayName)
            assertTrue(maker.pages.none { it.needed }, maker.displayName)
        }
    }

    @Test
    fun `every maker the user's list names has a page to try, or is known to need only Android's own settings`() {
        val withPages = listOf(
            PhoneMaker.XIAOMI, PhoneMaker.HUAWEI, PhoneMaker.HONOR, PhoneMaker.SAMSUNG, PhoneMaker.ONEPLUS, PhoneMaker.OPPO,
            PhoneMaker.REALME, PhoneMaker.VIVO, PhoneMaker.TECNO, PhoneMaker.INFINIX, PhoneMaker.ITEL, PhoneMaker.ASUS,
            PhoneMaker.MEIZU, PhoneMaker.NOKIA, PhoneMaker.LETV, PhoneMaker.LENOVO,
        )
        for (maker in withPages) {
            assertTrue(maker.pages.isNotEmpty(), "${maker.displayName} has a page")
            maker.pages.forEach { page ->
                assertTrue(page.targets.isNotEmpty(), "${maker.displayName}'s ${page.title} has somewhere to look")
                assertTrue(page.title.isNotBlank() && page.detail.isNotBlank(), "${maker.displayName}'s page says what it is")
                page.targets.forEach { assertTrue(it.className != null || it.action != null, "${it.packageName} is a page, not just an app") }
            }
        }
        assertTrue(PhoneMaker.MOTOROLA.pages.isEmpty())
        assertEquals(PhoneMaker.entries.toSet(), (withPages + PhoneMaker.MOTOROLA).toSet(), "every maker is accounted for here")
    }

    @Test
    fun `ColorOS's start-up list is tried on Oppo, realme and OnePlus, after OnePlus's own`() {
        val colorOs = "com.coloros.safecenter/com.coloros.safecenter.permission.startup.StartupAppListActivity"
        fun places(maker: PhoneMaker) = maker.page(MakerPageKind.AUTOSTART)!!.targets.map { "${it.packageName}/${it.className}" }
        assertEquals(colorOs, places(PhoneMaker.OPPO).first())
        assertEquals(colorOs, places(PhoneMaker.REALME).first())
        assertEquals("com.oneplus.security/com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity", places(PhoneMaker.ONEPLUS).first())
        assertTrue(colorOs in places(PhoneMaker.ONEPLUS))
        // And Honor's own manager first, then Huawei's, which the older Honors have.
        assertEquals("com.hihonor.systemmanager", PhoneMaker.HONOR.page(MakerPageKind.AUTOSTART)!!.targets.first().packageName)
        assertTrue(PhoneMaker.HONOR.page(MakerPageKind.AUTOSTART)!!.targets.drop(1).all { it.packageName == "com.huawei.systemmanager" })
    }

    @Test
    fun `the first place that opens is used, and Noctorium's app info page where none does`() {
        val page = PhoneMaker.VIVO.page(MakerPageKind.AUTOSTART)!!
        val tried = mutableListOf<String>()
        var appInfo = false
        fun opens(only: String?) = openFirst(page.targets, { tried += it.className!!; it.className == only }) { appInfo = true; true }

        assertTrue(opens(only = "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"))
        assertEquals(
            listOf("com.vivo.permissionmanager.activity.BgStartUpManagerActivity", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
            tried,
            "in order, and no further once one opened",
        )
        assertFalse(appInfo)

        tried.clear()
        assertTrue(opens(only = null))
        assertEquals(page.targets.size, tried.size, "every place tried")
        assertTrue(appInfo, "then the app info page")
    }

    @Test
    fun `with no page at all, it is the app info page straight away`() {
        var appInfo = false
        assertTrue(openFirst(emptyList<MakerTarget>(), { error("nothing to try") }) { appInfo = true; true })
        assertTrue(appInfo)
        // And where even that cannot be opened, it says so.
        assertFalse(openFirst(PhoneMaker.SAMSUNG.pages.single().targets, { false }) { false })
    }

    @Test
    fun `dontkillmyapp's page is the maker's, or the general one`() {
        assertEquals("https://dontkillmyapp.com/xiaomi", dontKillMyAppPage(PhoneMaker.XIAOMI, "Xiaomi"))
        assertEquals("https://dontkillmyapp.com/huawei", dontKillMyAppPage(PhoneMaker.HONOR, "HONOR"))
        assertEquals("https://dontkillmyapp.com/vivo", dontKillMyAppPage(PhoneMaker.VIVO, "vivo"))
        assertEquals("https://dontkillmyapp.com/tecno", dontKillMyAppPage(PhoneMaker.INFINIX, "INFINIX MOBILITY LIMITED"))
        assertEquals("https://dontkillmyapp.com/nokia", dontKillMyAppPage(PhoneMaker.NOKIA, "HMD Global"))
        assertEquals("https://dontkillmyapp.com/google", dontKillMyAppPage(null, "Google"))
        assertEquals("https://dontkillmyapp.com/sony", dontKillMyAppPage(null, "Sony"))
        assertEquals("https://dontkillmyapp.com/general", dontKillMyAppPage(null, "Fairphone"))
        assertEquals("https://dontkillmyapp.com/general", dontKillMyAppPage(PhoneMaker.LETV, "LeMobile"))
    }
}
