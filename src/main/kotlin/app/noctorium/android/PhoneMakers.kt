package app.noctorium.android

/*
 * The phone makers whose own power managers stop apps that Android itself would leave running, and where on each of
 * their phones the listener lets Noctorium off.
 *
 * None of these pages is Android's: each maker adds its own, under its own name, in its own settings app, and moves
 * it between versions. So each page is a short list of the places it has been, tried in turn, and where none of them
 * opens, Noctorium's own app info page does -- where the same choice is usually a tap or two further in. The places
 * are the ones long known from these phones (dontkillmyapp.com keeps the how-to for each); none is a promise.
 */

/** One way into a maker's settings page: an activity by name, or an action, with what it is told about Noctorium. */
internal data class MakerTarget(
    val packageName: String,
    val className: String? = null,
    val action: String? = null,
    /** An address to open, for the pages that are reached by one. */
    val data: String? = null,
    /** Extras that are Noctorium's package name, which some pages open straight onto. */
    val packageExtras: List<String> = emptyList(),
    /** An extra that is Noctorium's name, for the page that shows it at the top. */
    val labelExtra: String? = null,
)

/** What one of a maker's pages is for. */
internal enum class MakerPageKind {
    /** Starting by itself: after the phone restarts, and again after it was stopped. */
    AUTOSTART,

    /** Its battery saver for Noctorium, where "No restrictions" or the like is chosen. */
    BATTERY,
}

/** One of a maker's pages, said the way the checklist says it, and the places it has been. */
internal data class MakerPage(
    val kind: MakerPageKind,
    val title: String,
    val detail: String,
    val targets: List<MakerTarget>,
)

private const val AUTOSTART_DETAIL = "Lets Noctorium start by itself after the phone restarts, and again when it is stopped."

/** ColorOS's start-up list, which Oppo, realme and the later OnePlus phones share. */
private val COLOR_OS_STARTUP = listOf(
    MakerTarget("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
    MakerTarget("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
    MakerTarget("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
    MakerTarget("com.coloros.safecenter", "com.coloros.privacypermissionsentry.PermissionTopActivity"),
)

/** Huawei's start-up manager, which the Honor phones from before Honor was its own company also have. */
private val HUAWEI_STARTUP = listOf(
    MakerTarget("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
    MakerTarget("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
    MakerTarget("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
)

/** Transsion's Phone Master, on Tecno, Infinix and itel phones. */
private val PHONE_MASTER = listOf(
    MakerTarget("com.transsion.phonemaster", "com.cyin.himgr.autostart.AutoStartActivity"),
    MakerTarget("com.itel.autobootmanager", "com.itel.autobootmanager.activity.AutoBootMgrActivity"),
)

private fun autostart(title: String, targets: List<MakerTarget>, detail: String = AUTOSTART_DETAIL) =
    MakerPage(MakerPageKind.AUTOSTART, title, detail, targets)

/**
 * A phone maker known to stop apps in the background, by the names its phones give themselves.
 *
 * [names] are what `Build.BRAND` and `Build.MANUFACTURER` say, in lower case: a Redmi says Xiaomi as its maker and
 * Redmi as its brand, a Nokia says HMD Global and Nokia. [guide] is the maker's page on dontkillmyapp.com. [tip] is
 * the one thing on its phones no page can be opened for.
 */
internal enum class PhoneMaker(
    val displayName: String,
    val names: Set<String>,
    val guide: String,
    val pages: List<MakerPage> = emptyList(),
    val tip: String? = null,
) {
    XIAOMI(
        "Xiaomi",
        setOf("xiaomi", "redmi", "poco"),
        "xiaomi",
        listOf(
            autostart(
                "Autostart, in Xiaomi's settings",
                listOf(
                    MakerTarget("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                    // Noctorium's own page of Xiaomi's permissions, where Autostart is one of the switches.
                    MakerTarget(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.permissions.PermissionsEditorActivity",
                        action = "miui.intent.action.APP_PERM_EDITOR",
                        packageExtras = listOf("extra_pkgname"),
                    ),
                ),
            ),
            // The power keeper's page, which is not Android's battery optimisation: on these phones both need a yes.
            MakerPage(
                MakerPageKind.BATTERY,
                "Battery saver: No restrictions",
                "Xiaomi's own battery saver for Noctorium, apart from Android's, which closes it with the screen locked unless told not to.",
                listOf(
                    MakerTarget(
                        "com.miui.powerkeeper",
                        "com.miui.powerkeeper.ui.HiddenAppsConfigActivity",
                        packageExtras = listOf("package_name"),
                        labelExtra = "package_label",
                    ),
                ),
            ),
        ),
        tip = "In the recent apps, hold Noctorium's card and lock it, so clearing them leaves it running.",
    ),
    HUAWEI(
        "Huawei",
        setOf("huawei"),
        "huawei",
        listOf(autostart("App launch, in Huawei's settings", HUAWEI_STARTUP, "Set Noctorium to Manage manually, with all three switches on.")),
    ),
    HONOR(
        "Honor",
        setOf("honor"),
        "huawei",
        listOf(
            autostart(
                "App launch, in Honor's settings",
                listOf(MakerTarget("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity")) +
                    HUAWEI_STARTUP,
                "Set Noctorium to Manage manually, with all three switches on.",
            ),
        ),
    ),
    SAMSUNG(
        "Samsung",
        setOf("samsung"),
        "samsung",
        listOf(
            MakerPage(
                MakerPageKind.BATTERY,
                "Never sleeping apps, in Samsung's battery settings",
                "Under Background usage limits, add Noctorium to Never sleeping apps, and take it out of Sleeping apps.",
                listOf(
                    MakerTarget("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
                    MakerTarget("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
                    MakerTarget("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.usage.CheckableAppListActivity"),
                ),
            ),
        ),
    ),
    ONEPLUS(
        "OnePlus",
        setOf("oneplus"),
        "oneplus",
        listOf(
            autostart(
                "Auto-launch, in OnePlus's settings",
                listOf(
                    MakerTarget("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
                ) + COLOR_OS_STARTUP,
            ),
        ),
    ),
    OPPO("Oppo", setOf("oppo"), "oppo", listOf(autostart("Auto-launch, in Oppo's settings", COLOR_OS_STARTUP))),
    REALME("realme", setOf("realme"), "realme", listOf(autostart("Auto-launch, in realme's settings", COLOR_OS_STARTUP))),
    VIVO(
        "Vivo",
        setOf("vivo", "iqoo"),
        "vivo",
        listOf(
            autostart(
                "Background start-up, in Vivo's settings",
                listOf(
                    MakerTarget("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                    MakerTarget("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
                    MakerTarget("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                ),
            ),
        ),
    ),
    TECNO("Tecno", setOf("tecno"), "tecno", listOf(autostart("Auto-start, in Phone Master", PHONE_MASTER))),
    INFINIX("Infinix", setOf("infinix"), "tecno", listOf(autostart("Auto-start, in Phone Master", PHONE_MASTER))),
    ITEL("itel", setOf("itel"), "tecno", listOf(autostart("Auto-start, in Phone Master", PHONE_MASTER))),
    ASUS(
        "Asus",
        setOf("asus"),
        "asus",
        listOf(
            autostart(
                "Auto-start, in Asus's Mobile Manager",
                listOf(
                    MakerTarget("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"),
                    MakerTarget(
                        "com.asus.mobilemanager",
                        "com.asus.mobilemanager.entry.FunctionActivity",
                        data = "mobilemanager://function/entry/AutoStart",
                    ),
                    MakerTarget("com.asus.mobilemanager", "com.asus.mobilemanager.powersaver.PowerSaverSettings"),
                ),
            ),
        ),
    ),
    MEIZU(
        "Meizu",
        setOf("meizu"),
        "meizu",
        listOf(
            autostart(
                "Background management, in Meizu's settings",
                listOf(
                    MakerTarget(
                        "com.meizu.safe",
                        "com.meizu.safe.security.AppSecActivity",
                        action = "com.meizu.safe.security.SHOW_APPSEC",
                        packageExtras = listOf("packageName"),
                    ),
                    MakerTarget("com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity"),
                ),
                "Let Noctorium run in the background.",
            ),
        ),
    ),
    NOKIA(
        "Nokia",
        setOf("nokia", "hmd global", "hmd"),
        "nokia",
        listOf(
            MakerPage(
                MakerPageKind.BATTERY,
                "Battery protection, in Nokia's settings",
                "Leave Noctorium out of battery protection, so it is not closed in the background.",
                listOf(MakerTarget("com.evenwell.powersaving.g3", "com.evenwell.powersaving.g3.exception.PowerSaverExceptionActivity")),
            ),
        ),
    ),
    LETV(
        "LeEco",
        setOf("letv", "leeco"),
        "general",
        listOf(autostart("Auto-start, in LeEco's settings", listOf(MakerTarget("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")))),
    ),
    LENOVO(
        "Lenovo",
        setOf("lenovo"),
        "lenovo",
        listOf(
            MakerPage(
                MakerPageKind.BATTERY,
                "Background apps, in Lenovo's settings",
                "Let Noctorium run in the background.",
                listOf(MakerTarget("com.lenovo.security", "com.lenovo.security.purebackground.PureBackgroundActivity")),
            ),
        ),
    ),
    // Android's own settings are all there is on a Motorola; the guide says which of them matter.
    MOTOROLA("Motorola", setOf("motorola"), "motorola"),
    ;

    /** The page of [kind], if this maker has one. */
    fun page(kind: MakerPageKind): MakerPage? = pages.firstOrNull { it.kind == kind }

    companion object {
        /**
         * The maker of the phone that says it is by [manufacturer], of [brand]; null for one not known to stop apps.
         *
         * The brand first, since it is the more particular: a Redmi or a POCO says Xiaomi as its maker, a Nokia HMD
         * Global. A name is matched whole, or as the first word of a longer one ("Infinix Mobility Limited").
         */
        fun of(manufacturer: String?, brand: String?): PhoneMaker? {
            val said = listOfNotNull(brand, manufacturer).map { it.trim().lowercase() }.filter(String::isNotEmpty)
            for (name in said) {
                entries.firstOrNull { maker -> maker.names.any { name == it || name.startsWith("$it ") } }?.let { return it }
            }
            return null
        }

        /** This phone's maker, from what it says about itself. */
        val thisPhone: PhoneMaker? by lazy { of(android.os.Build.MANUFACTURER, android.os.Build.BRAND) }
    }
}

/**
 * The page on dontkillmyapp.com for this phone: its maker's, or, for a phone whose maker has none there, the general
 * one, which says what Android itself has to be told.
 */
internal fun dontKillMyAppPage(maker: PhoneMaker?, manufacturer: String?): String {
    val own = manufacturer?.trim()?.lowercase()?.takeIf { it in DONT_KILL_MY_APP_MAKERS }
    return "https://dontkillmyapp.com/" + (maker?.guide ?: own ?: "general")
}

/** The makers dontkillmyapp.com has a page of its own for, besides the ones above: Google's phones among them. */
private val DONT_KILL_MY_APP_MAKERS = setOf("google", "sony", "htc", "wiko", "blackview", "ulefone", "unihertz")
