package app.noctorium.android.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.automirrored.sharp.*
import androidx.compose.material.icons.automirrored.twotone.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.sharp.*
import androidx.compose.material.icons.twotone.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import app.noctorium.settings.IconSet

/*
 * Every icon the phone draws, by what it means rather than by how it looks, and the one place that decides how it
 * looks: the icon set the listener chose ([IconSet]). A screen asks for [Glyph.PLAY_ARROW]'s [Glyph.image] and is
 * given the play button in Standard, Rounded, Outlined, Sharp, Two-tone or Line, whichever is in force.
 *
 * Standard is each icon exactly as the phone has always drawn it -- Compose's Material icons, which are Material's
 * filled set -- so nobody's phone changes until they choose. Filled is therefore the same as Standard here, and is
 * not offered. Rounded, Outlined, Sharp and Two-tone are Material's own styles of the same icons, Google's Material
 * icons (Apache License 2.0), which the app already carries. Line is Lucide's (ISC License; see LineGlyphs.kt).
 *
 * Every glyph has every set, or this would not build. Two that say whether something is on -- liked, pinned -- are
 * solid in every set and outlined when off, so the two states stay apart in all of them.
 */

/** The icon set in force: provided once at the root, from the preferences. */
internal val LocalIconSet = staticCompositionLocalOf { IconSet.STANDARD }

/** The icon sets the phone offers, in the order Customization lists them: Filled is Standard here. */
internal val PHONE_ICON_SETS: List<IconSet> = listOf(
    IconSet.STANDARD,
    IconSet.ROUNDED,
    IconSet.OUTLINED,
    IconSet.SHARP,
    IconSet.TWO_TONE,
    IconSet.LINE,
)

/** What each icon means. Its name is the Material icon Standard draws for it. */
internal enum class Glyph {
    ACCOUNT_CIRCLE,
    ADD,
    ALBUM,
    ALL_INCLUSIVE,
    ARROW_BACK,
    AUDIOTRACK,
    BATTERY_ALERT,
    BATTERY_FULL,
    BEDTIME,
    BOLT,
    CHECK,
    CHECK_CIRCLE,
    CHEVRON_RIGHT,
    CLEAR_ALL,
    CLEAR_ALL_BUT_THIS,
    CLOSE,
    CLOUD,
    CLOUD_UPLOAD,
    COLOR_LENS,
    COMPUTER,
    CONTENT_COPY,
    CONTENT_PASTE,
    DELETE,
    DEVICES,
    DIRECTIONS_CAR,
    DOWNLOAD,
    DOWNLOAD_DONE,
    DOWNLOAD_FOR_OFFLINE,
    DOWNLOADING,
    EDIT,
    EQUALIZER,
    ERROR_OUTLINE,
    EXPAND_LESS,
    EXPAND_MORE,
    FAVORITE,
    FAVORITE_BORDER,
    FORMAT_SIZE,
    GRAPHIC_EQ,
    HEADPHONES,
    HISTORY,
    HOME,
    ICONS,
    INSIGHTS,
    KEY,
    KEYBOARD_ARROW_DOWN,
    KEYBOARD_ARROW_UP,
    LIBRARY_MUSIC,
    LINK,
    LOCK,
    LYRICS,
    MONITOR_HEART,
    MORE_VERT,
    MUSIC_NOTE,
    OPEN_IN_NEW,
    PALETTE,
    PAUSE,
    PERSON_ADD,
    PHONE_ANDROID,
    PLAY_ARROW,
    PLAY_CIRCLE,
    PLAYLIST_ADD,
    PLAYLIST_PLAY,
    PUBLIC,
    PUSH_PIN,
    PUSH_PIN_OUTLINE,
    QUEUE_MUSIC,
    REFRESH,
    REMOVE_CIRCLE_OUTLINE,
    REPEAT,
    REPEAT_ONE,
    SAVE,
    SAVE_ALT,
    SEARCH,
    SETTINGS,
    SHARE,
    SHUFFLE,
    SKIP_NEXT,
    SKIP_PREVIOUS,
    SMART_DISPLAY,
    SPEAKER,
    SPEED,
    STATUS_BAR,
    STOP,
    STOP_AFTER,
    SYSTEM_UPDATE_ALT,
    TAB,
    TABLET,
    TERMINAL,
    TEXT_FIELDS,
    TUNE,
    TV,
    VIEW_COLUMN,
    VOLUME_DOWN,
    VOLUME_OFF,
    VOLUME_UP,
    WARNING,
    WAVES,
    WIFI,
    ;

    /** This glyph in [set]. */
    fun vector(set: IconSet): ImageVector = when (set) {
        IconSet.STANDARD, IconSet.FILLED -> standard()
        IconSet.ROUNDED -> rounded()
        IconSet.OUTLINED -> outlined()
        IconSet.SHARP -> sharp()
        IconSet.TWO_TONE -> twoTone()
        IconSet.LINE -> lineGlyph(this)
    }

    /** This glyph in the set in force. */
    val image: ImageVector
        @Composable @ReadOnlyComposable
        get() = vector(LocalIconSet.current)

    /** Exactly the icon the phone has always drawn for it. */
    private fun standard(): ImageVector = when (this) {
        ACCOUNT_CIRCLE -> Icons.Filled.AccountCircle
        ADD -> Icons.Filled.Add
        ALBUM -> Icons.Filled.Album
        ALL_INCLUSIVE -> Icons.Filled.AllInclusive
        ARROW_BACK -> Icons.AutoMirrored.Filled.ArrowBack
        AUDIOTRACK -> Icons.Filled.Audiotrack
        BATTERY_ALERT -> Icons.Filled.BatteryAlert
        BATTERY_FULL -> Icons.Filled.BatteryFull
        BEDTIME -> Icons.Filled.Bedtime
        BOLT -> Icons.Filled.Bolt
        CHECK -> Icons.Filled.Check
        CHECK_CIRCLE -> Icons.Filled.CheckCircle
        CHEVRON_RIGHT -> Icons.Filled.ChevronRight
        CLEAR_ALL -> Icons.Filled.ClearAll
        CLEAR_ALL_BUT_THIS -> Icons.Filled.PlaylistRemove
        CLOSE -> Icons.Filled.Close
        CLOUD -> Icons.Filled.Cloud
        CLOUD_UPLOAD -> Icons.Filled.CloudUpload
        COLOR_LENS -> Icons.Filled.ColorLens
        COMPUTER -> Icons.Filled.Computer
        CONTENT_COPY -> Icons.Filled.ContentCopy
        CONTENT_PASTE -> Icons.Filled.ContentPaste
        DELETE -> Icons.Filled.Delete
        DEVICES -> Icons.Filled.Devices
        DIRECTIONS_CAR -> Icons.Filled.DirectionsCar
        DOWNLOAD -> Icons.Filled.Download
        DOWNLOAD_DONE -> Icons.Filled.DownloadDone
        DOWNLOAD_FOR_OFFLINE -> Icons.Filled.DownloadForOffline
        DOWNLOADING -> Icons.Filled.Downloading
        EDIT -> Icons.Filled.Edit
        EQUALIZER -> Icons.Filled.Equalizer
        ERROR_OUTLINE -> Icons.Filled.ErrorOutline
        EXPAND_LESS -> Icons.Filled.ExpandLess
        EXPAND_MORE -> Icons.Filled.ExpandMore
        FAVORITE -> Icons.Filled.Favorite
        FAVORITE_BORDER -> Icons.Filled.FavoriteBorder
        FORMAT_SIZE -> Icons.Filled.FormatSize
        GRAPHIC_EQ -> Icons.Filled.GraphicEq
        HEADPHONES -> Icons.Filled.Headphones
        HISTORY -> Icons.Filled.History
        HOME -> Icons.Filled.Home
        ICONS -> Icons.Filled.Interests
        INSIGHTS -> Icons.Filled.Insights
        KEY -> Icons.Filled.Key
        KEYBOARD_ARROW_DOWN -> Icons.Filled.KeyboardArrowDown
        KEYBOARD_ARROW_UP -> Icons.Filled.KeyboardArrowUp
        LIBRARY_MUSIC -> Icons.Filled.LibraryMusic
        LINK -> Icons.Filled.Link
        LOCK -> Icons.Filled.Lock
        LYRICS -> Icons.Filled.Lyrics
        MONITOR_HEART -> Icons.Filled.MonitorHeart
        MORE_VERT -> Icons.Filled.MoreVert
        MUSIC_NOTE -> Icons.Filled.MusicNote
        OPEN_IN_NEW -> Icons.AutoMirrored.Filled.OpenInNew
        PALETTE -> Icons.Filled.Palette
        PAUSE -> Icons.Filled.Pause
        PERSON_ADD -> Icons.Filled.PersonAdd
        PHONE_ANDROID -> Icons.Filled.PhoneAndroid
        PLAY_ARROW -> Icons.Filled.PlayArrow
        PLAY_CIRCLE -> Icons.Filled.PlayCircle
        PLAYLIST_ADD -> Icons.AutoMirrored.Filled.PlaylistAdd
        PLAYLIST_PLAY -> Icons.AutoMirrored.Filled.PlaylistPlay
        PUBLIC -> Icons.Filled.Public
        PUSH_PIN -> Icons.Filled.PushPin
        PUSH_PIN_OUTLINE -> Icons.Outlined.PushPin
        QUEUE_MUSIC -> Icons.AutoMirrored.Filled.QueueMusic
        REFRESH -> Icons.Filled.Refresh
        REMOVE_CIRCLE_OUTLINE -> Icons.Filled.RemoveCircleOutline
        REPEAT -> Icons.Filled.Repeat
        REPEAT_ONE -> Icons.Filled.RepeatOne
        SAVE -> Icons.Filled.Save
        SAVE_ALT -> Icons.Filled.SaveAlt
        SEARCH -> Icons.Filled.Search
        SETTINGS -> Icons.Filled.Settings
        SHARE -> Icons.Filled.Share
        SHUFFLE -> Icons.Filled.Shuffle
        SKIP_NEXT -> Icons.Filled.SkipNext
        SKIP_PREVIOUS -> Icons.Filled.SkipPrevious
        SMART_DISPLAY -> Icons.Filled.SmartDisplay
        SPEAKER -> Icons.Filled.Speaker
        SPEED -> Icons.Filled.Speed
        STATUS_BAR -> Icons.Filled.CallToAction
        STOP -> Icons.Filled.Stop
        STOP_AFTER -> Icons.Filled.StopCircle
        SYSTEM_UPDATE_ALT -> Icons.Filled.SystemUpdateAlt
        TAB -> Icons.Filled.Tab
        TABLET -> Icons.Filled.Tablet
        TERMINAL -> Icons.Filled.Terminal
        TEXT_FIELDS -> Icons.Filled.TextFields
        TUNE -> Icons.Filled.Tune
        TV -> Icons.Filled.Tv
        VIEW_COLUMN -> Icons.Filled.ViewColumn
        VOLUME_DOWN -> Icons.AutoMirrored.Filled.VolumeDown
        VOLUME_OFF -> Icons.AutoMirrored.Filled.VolumeOff
        VOLUME_UP -> Icons.AutoMirrored.Filled.VolumeUp
        WARNING -> Icons.Filled.Warning
        WAVES -> Icons.Filled.Waves
        WIFI -> Icons.Filled.Wifi
    }

    private fun rounded(): ImageVector = when (this) {
        ACCOUNT_CIRCLE -> Icons.Rounded.AccountCircle
        ADD -> Icons.Rounded.Add
        ALBUM -> Icons.Rounded.Album
        ALL_INCLUSIVE -> Icons.Rounded.AllInclusive
        ARROW_BACK -> Icons.AutoMirrored.Rounded.ArrowBack
        AUDIOTRACK -> Icons.Rounded.Audiotrack
        BATTERY_ALERT -> Icons.Rounded.BatteryAlert
        BATTERY_FULL -> Icons.Rounded.BatteryFull
        BEDTIME -> Icons.Rounded.Bedtime
        BOLT -> Icons.Rounded.Bolt
        CHECK -> Icons.Rounded.Check
        CHECK_CIRCLE -> Icons.Rounded.CheckCircle
        CHEVRON_RIGHT -> Icons.Rounded.ChevronRight
        CLEAR_ALL -> Icons.Rounded.ClearAll
        CLEAR_ALL_BUT_THIS -> Icons.Rounded.PlaylistRemove
        CLOSE -> Icons.Rounded.Close
        CLOUD -> Icons.Rounded.Cloud
        CLOUD_UPLOAD -> Icons.Rounded.CloudUpload
        COLOR_LENS -> Icons.Rounded.ColorLens
        COMPUTER -> Icons.Rounded.Computer
        CONTENT_COPY -> Icons.Rounded.ContentCopy
        CONTENT_PASTE -> Icons.Rounded.ContentPaste
        DELETE -> Icons.Rounded.Delete
        DEVICES -> Icons.Rounded.Devices
        DIRECTIONS_CAR -> Icons.Rounded.DirectionsCar
        DOWNLOAD -> Icons.Rounded.Download
        DOWNLOAD_DONE -> Icons.Rounded.DownloadDone
        DOWNLOAD_FOR_OFFLINE -> Icons.Rounded.DownloadForOffline
        DOWNLOADING -> Icons.Rounded.Downloading
        EDIT -> Icons.Rounded.Edit
        EQUALIZER -> Icons.Rounded.Equalizer
        ERROR_OUTLINE -> Icons.Rounded.ErrorOutline
        EXPAND_LESS -> Icons.Rounded.ExpandLess
        EXPAND_MORE -> Icons.Rounded.ExpandMore
        FAVORITE -> Icons.Rounded.Favorite
        FAVORITE_BORDER -> Icons.Rounded.FavoriteBorder
        FORMAT_SIZE -> Icons.Rounded.FormatSize
        GRAPHIC_EQ -> Icons.Rounded.GraphicEq
        HEADPHONES -> Icons.Rounded.Headphones
        HISTORY -> Icons.Rounded.History
        HOME -> Icons.Rounded.Home
        ICONS -> Icons.Rounded.Interests
        INSIGHTS -> Icons.Rounded.Insights
        KEY -> Icons.Rounded.Key
        KEYBOARD_ARROW_DOWN -> Icons.Rounded.KeyboardArrowDown
        KEYBOARD_ARROW_UP -> Icons.Rounded.KeyboardArrowUp
        LIBRARY_MUSIC -> Icons.Rounded.LibraryMusic
        LINK -> Icons.Rounded.Link
        LOCK -> Icons.Rounded.Lock
        LYRICS -> Icons.Rounded.Lyrics
        MONITOR_HEART -> Icons.Rounded.MonitorHeart
        MORE_VERT -> Icons.Rounded.MoreVert
        MUSIC_NOTE -> Icons.Rounded.MusicNote
        OPEN_IN_NEW -> Icons.AutoMirrored.Rounded.OpenInNew
        PALETTE -> Icons.Rounded.Palette
        PAUSE -> Icons.Rounded.Pause
        PERSON_ADD -> Icons.Rounded.PersonAdd
        PHONE_ANDROID -> Icons.Rounded.PhoneAndroid
        PLAY_ARROW -> Icons.Rounded.PlayArrow
        PLAY_CIRCLE -> Icons.Rounded.PlayCircle
        PLAYLIST_ADD -> Icons.AutoMirrored.Rounded.PlaylistAdd
        PLAYLIST_PLAY -> Icons.AutoMirrored.Rounded.PlaylistPlay
        PUBLIC -> Icons.Rounded.Public
        PUSH_PIN -> Icons.Rounded.PushPin
        PUSH_PIN_OUTLINE -> Icons.Outlined.PushPin
        QUEUE_MUSIC -> Icons.AutoMirrored.Rounded.QueueMusic
        REFRESH -> Icons.Rounded.Refresh
        REMOVE_CIRCLE_OUTLINE -> Icons.Rounded.RemoveCircleOutline
        REPEAT -> Icons.Rounded.Repeat
        REPEAT_ONE -> Icons.Rounded.RepeatOne
        SAVE -> Icons.Rounded.Save
        SAVE_ALT -> Icons.Rounded.SaveAlt
        SEARCH -> Icons.Rounded.Search
        SETTINGS -> Icons.Rounded.Settings
        SHARE -> Icons.Rounded.Share
        SHUFFLE -> Icons.Rounded.Shuffle
        SKIP_NEXT -> Icons.Rounded.SkipNext
        SKIP_PREVIOUS -> Icons.Rounded.SkipPrevious
        SMART_DISPLAY -> Icons.Rounded.SmartDisplay
        SPEAKER -> Icons.Rounded.Speaker
        SPEED -> Icons.Rounded.Speed
        STATUS_BAR -> Icons.Rounded.CallToAction
        STOP -> Icons.Rounded.Stop
        STOP_AFTER -> Icons.Rounded.StopCircle
        SYSTEM_UPDATE_ALT -> Icons.Rounded.SystemUpdateAlt
        TAB -> Icons.Rounded.Tab
        TABLET -> Icons.Rounded.Tablet
        TERMINAL -> Icons.Rounded.Terminal
        TEXT_FIELDS -> Icons.Rounded.TextFields
        TUNE -> Icons.Rounded.Tune
        TV -> Icons.Rounded.Tv
        VIEW_COLUMN -> Icons.Rounded.ViewColumn
        VOLUME_DOWN -> Icons.AutoMirrored.Rounded.VolumeDown
        VOLUME_OFF -> Icons.AutoMirrored.Rounded.VolumeOff
        VOLUME_UP -> Icons.AutoMirrored.Rounded.VolumeUp
        WARNING -> Icons.Rounded.Warning
        WAVES -> Icons.Rounded.Waves
        WIFI -> Icons.Rounded.Wifi
    }

    private fun outlined(): ImageVector = when (this) {
        ACCOUNT_CIRCLE -> Icons.Outlined.AccountCircle
        ADD -> Icons.Outlined.Add
        ALBUM -> Icons.Outlined.Album
        ALL_INCLUSIVE -> Icons.Outlined.AllInclusive
        ARROW_BACK -> Icons.AutoMirrored.Outlined.ArrowBack
        AUDIOTRACK -> Icons.Outlined.Audiotrack
        BATTERY_ALERT -> Icons.Outlined.BatteryAlert
        BATTERY_FULL -> Icons.Outlined.BatteryFull
        BEDTIME -> Icons.Outlined.Bedtime
        BOLT -> Icons.Outlined.Bolt
        CHECK -> Icons.Outlined.Check
        CHECK_CIRCLE -> Icons.Outlined.CheckCircle
        CHEVRON_RIGHT -> Icons.Outlined.ChevronRight
        CLEAR_ALL -> Icons.Outlined.ClearAll
        CLEAR_ALL_BUT_THIS -> Icons.Outlined.PlaylistRemove
        CLOSE -> Icons.Outlined.Close
        CLOUD -> Icons.Outlined.Cloud
        CLOUD_UPLOAD -> Icons.Outlined.CloudUpload
        COLOR_LENS -> Icons.Outlined.ColorLens
        COMPUTER -> Icons.Outlined.Computer
        CONTENT_COPY -> Icons.Outlined.ContentCopy
        CONTENT_PASTE -> Icons.Outlined.ContentPaste
        DELETE -> Icons.Outlined.Delete
        DEVICES -> Icons.Outlined.Devices
        DIRECTIONS_CAR -> Icons.Outlined.DirectionsCar
        DOWNLOAD -> Icons.Outlined.Download
        DOWNLOAD_DONE -> Icons.Outlined.DownloadDone
        DOWNLOAD_FOR_OFFLINE -> Icons.Outlined.DownloadForOffline
        DOWNLOADING -> Icons.Outlined.Downloading
        EDIT -> Icons.Outlined.Edit
        EQUALIZER -> Icons.Outlined.Equalizer
        ERROR_OUTLINE -> Icons.Outlined.ErrorOutline
        EXPAND_LESS -> Icons.Outlined.ExpandLess
        EXPAND_MORE -> Icons.Outlined.ExpandMore
        FAVORITE -> Icons.Filled.Favorite
        FAVORITE_BORDER -> Icons.Outlined.FavoriteBorder
        FORMAT_SIZE -> Icons.Outlined.FormatSize
        GRAPHIC_EQ -> Icons.Outlined.GraphicEq
        HEADPHONES -> Icons.Outlined.Headphones
        HISTORY -> Icons.Outlined.History
        HOME -> Icons.Outlined.Home
        ICONS -> Icons.Outlined.Interests
        INSIGHTS -> Icons.Outlined.Insights
        KEY -> Icons.Outlined.Key
        KEYBOARD_ARROW_DOWN -> Icons.Outlined.KeyboardArrowDown
        KEYBOARD_ARROW_UP -> Icons.Outlined.KeyboardArrowUp
        LIBRARY_MUSIC -> Icons.Outlined.LibraryMusic
        LINK -> Icons.Outlined.Link
        LOCK -> Icons.Outlined.Lock
        LYRICS -> Icons.Outlined.Lyrics
        MONITOR_HEART -> Icons.Outlined.MonitorHeart
        MORE_VERT -> Icons.Outlined.MoreVert
        MUSIC_NOTE -> Icons.Outlined.MusicNote
        OPEN_IN_NEW -> Icons.AutoMirrored.Outlined.OpenInNew
        PALETTE -> Icons.Outlined.Palette
        PAUSE -> Icons.Outlined.Pause
        PERSON_ADD -> Icons.Outlined.PersonAdd
        PHONE_ANDROID -> Icons.Outlined.PhoneAndroid
        PLAY_ARROW -> Icons.Outlined.PlayArrow
        PLAY_CIRCLE -> Icons.Outlined.PlayCircle
        PLAYLIST_ADD -> Icons.AutoMirrored.Outlined.PlaylistAdd
        PLAYLIST_PLAY -> Icons.AutoMirrored.Outlined.PlaylistPlay
        PUBLIC -> Icons.Outlined.Public
        PUSH_PIN -> Icons.Filled.PushPin
        PUSH_PIN_OUTLINE -> Icons.Outlined.PushPin
        QUEUE_MUSIC -> Icons.AutoMirrored.Outlined.QueueMusic
        REFRESH -> Icons.Outlined.Refresh
        REMOVE_CIRCLE_OUTLINE -> Icons.Outlined.RemoveCircleOutline
        REPEAT -> Icons.Outlined.Repeat
        REPEAT_ONE -> Icons.Outlined.RepeatOne
        SAVE -> Icons.Outlined.Save
        SAVE_ALT -> Icons.Outlined.SaveAlt
        SEARCH -> Icons.Outlined.Search
        SETTINGS -> Icons.Outlined.Settings
        SHARE -> Icons.Outlined.Share
        SHUFFLE -> Icons.Outlined.Shuffle
        SKIP_NEXT -> Icons.Outlined.SkipNext
        SKIP_PREVIOUS -> Icons.Outlined.SkipPrevious
        SMART_DISPLAY -> Icons.Outlined.SmartDisplay
        SPEAKER -> Icons.Outlined.Speaker
        SPEED -> Icons.Outlined.Speed
        STATUS_BAR -> Icons.Outlined.CallToAction
        STOP -> Icons.Outlined.Stop
        STOP_AFTER -> Icons.Outlined.StopCircle
        SYSTEM_UPDATE_ALT -> Icons.Outlined.SystemUpdateAlt
        TAB -> Icons.Outlined.Tab
        TABLET -> Icons.Outlined.Tablet
        TERMINAL -> Icons.Outlined.Terminal
        TEXT_FIELDS -> Icons.Outlined.TextFields
        TUNE -> Icons.Outlined.Tune
        TV -> Icons.Outlined.Tv
        VIEW_COLUMN -> Icons.Outlined.ViewColumn
        VOLUME_DOWN -> Icons.AutoMirrored.Outlined.VolumeDown
        VOLUME_OFF -> Icons.AutoMirrored.Outlined.VolumeOff
        VOLUME_UP -> Icons.AutoMirrored.Outlined.VolumeUp
        WARNING -> Icons.Outlined.Warning
        WAVES -> Icons.Outlined.Waves
        WIFI -> Icons.Outlined.Wifi
    }

    private fun sharp(): ImageVector = when (this) {
        ACCOUNT_CIRCLE -> Icons.Sharp.AccountCircle
        ADD -> Icons.Sharp.Add
        ALBUM -> Icons.Sharp.Album
        ALL_INCLUSIVE -> Icons.Sharp.AllInclusive
        ARROW_BACK -> Icons.AutoMirrored.Sharp.ArrowBack
        AUDIOTRACK -> Icons.Sharp.Audiotrack
        BATTERY_ALERT -> Icons.Sharp.BatteryAlert
        BATTERY_FULL -> Icons.Sharp.BatteryFull
        BEDTIME -> Icons.Sharp.Bedtime
        BOLT -> Icons.Sharp.Bolt
        CHECK -> Icons.Sharp.Check
        CHECK_CIRCLE -> Icons.Sharp.CheckCircle
        CHEVRON_RIGHT -> Icons.Sharp.ChevronRight
        CLEAR_ALL -> Icons.Sharp.ClearAll
        CLEAR_ALL_BUT_THIS -> Icons.Sharp.PlaylistRemove
        CLOSE -> Icons.Sharp.Close
        CLOUD -> Icons.Sharp.Cloud
        CLOUD_UPLOAD -> Icons.Sharp.CloudUpload
        COLOR_LENS -> Icons.Sharp.ColorLens
        COMPUTER -> Icons.Sharp.Computer
        CONTENT_COPY -> Icons.Sharp.ContentCopy
        CONTENT_PASTE -> Icons.Sharp.ContentPaste
        DELETE -> Icons.Sharp.Delete
        DEVICES -> Icons.Sharp.Devices
        DIRECTIONS_CAR -> Icons.Sharp.DirectionsCar
        DOWNLOAD -> Icons.Sharp.Download
        DOWNLOAD_DONE -> Icons.Sharp.DownloadDone
        DOWNLOAD_FOR_OFFLINE -> Icons.Sharp.DownloadForOffline
        DOWNLOADING -> Icons.Sharp.Downloading
        EDIT -> Icons.Sharp.Edit
        EQUALIZER -> Icons.Sharp.Equalizer
        ERROR_OUTLINE -> Icons.Sharp.ErrorOutline
        EXPAND_LESS -> Icons.Sharp.ExpandLess
        EXPAND_MORE -> Icons.Sharp.ExpandMore
        FAVORITE -> Icons.Sharp.Favorite
        FAVORITE_BORDER -> Icons.Sharp.FavoriteBorder
        FORMAT_SIZE -> Icons.Sharp.FormatSize
        GRAPHIC_EQ -> Icons.Sharp.GraphicEq
        HEADPHONES -> Icons.Sharp.Headphones
        HISTORY -> Icons.Sharp.History
        HOME -> Icons.Sharp.Home
        ICONS -> Icons.Sharp.Interests
        INSIGHTS -> Icons.Sharp.Insights
        KEY -> Icons.Sharp.Key
        KEYBOARD_ARROW_DOWN -> Icons.Sharp.KeyboardArrowDown
        KEYBOARD_ARROW_UP -> Icons.Sharp.KeyboardArrowUp
        LIBRARY_MUSIC -> Icons.Sharp.LibraryMusic
        LINK -> Icons.Sharp.Link
        LOCK -> Icons.Sharp.Lock
        LYRICS -> Icons.Sharp.Lyrics
        MONITOR_HEART -> Icons.Sharp.MonitorHeart
        MORE_VERT -> Icons.Sharp.MoreVert
        MUSIC_NOTE -> Icons.Sharp.MusicNote
        OPEN_IN_NEW -> Icons.AutoMirrored.Sharp.OpenInNew
        PALETTE -> Icons.Sharp.Palette
        PAUSE -> Icons.Sharp.Pause
        PERSON_ADD -> Icons.Sharp.PersonAdd
        PHONE_ANDROID -> Icons.Sharp.PhoneAndroid
        PLAY_ARROW -> Icons.Sharp.PlayArrow
        PLAY_CIRCLE -> Icons.Sharp.PlayCircle
        PLAYLIST_ADD -> Icons.AutoMirrored.Sharp.PlaylistAdd
        PLAYLIST_PLAY -> Icons.AutoMirrored.Sharp.PlaylistPlay
        PUBLIC -> Icons.Sharp.Public
        PUSH_PIN -> Icons.Sharp.PushPin
        PUSH_PIN_OUTLINE -> Icons.Outlined.PushPin
        QUEUE_MUSIC -> Icons.AutoMirrored.Sharp.QueueMusic
        REFRESH -> Icons.Sharp.Refresh
        REMOVE_CIRCLE_OUTLINE -> Icons.Sharp.RemoveCircleOutline
        REPEAT -> Icons.Sharp.Repeat
        REPEAT_ONE -> Icons.Sharp.RepeatOne
        SAVE -> Icons.Sharp.Save
        SAVE_ALT -> Icons.Sharp.SaveAlt
        SEARCH -> Icons.Sharp.Search
        SETTINGS -> Icons.Sharp.Settings
        SHARE -> Icons.Sharp.Share
        SHUFFLE -> Icons.Sharp.Shuffle
        SKIP_NEXT -> Icons.Sharp.SkipNext
        SKIP_PREVIOUS -> Icons.Sharp.SkipPrevious
        SMART_DISPLAY -> Icons.Sharp.SmartDisplay
        SPEAKER -> Icons.Sharp.Speaker
        SPEED -> Icons.Sharp.Speed
        STATUS_BAR -> Icons.Sharp.CallToAction
        STOP -> Icons.Sharp.Stop
        STOP_AFTER -> Icons.Sharp.StopCircle
        SYSTEM_UPDATE_ALT -> Icons.Sharp.SystemUpdateAlt
        TAB -> Icons.Sharp.Tab
        TABLET -> Icons.Sharp.Tablet
        TERMINAL -> Icons.Sharp.Terminal
        TEXT_FIELDS -> Icons.Sharp.TextFields
        TUNE -> Icons.Sharp.Tune
        TV -> Icons.Sharp.Tv
        VIEW_COLUMN -> Icons.Sharp.ViewColumn
        VOLUME_DOWN -> Icons.AutoMirrored.Sharp.VolumeDown
        VOLUME_OFF -> Icons.AutoMirrored.Sharp.VolumeOff
        VOLUME_UP -> Icons.AutoMirrored.Sharp.VolumeUp
        WARNING -> Icons.Sharp.Warning
        WAVES -> Icons.Sharp.Waves
        WIFI -> Icons.Sharp.Wifi
    }

    private fun twoTone(): ImageVector = when (this) {
        ACCOUNT_CIRCLE -> Icons.TwoTone.AccountCircle
        ADD -> Icons.TwoTone.Add
        ALBUM -> Icons.TwoTone.Album
        ALL_INCLUSIVE -> Icons.TwoTone.AllInclusive
        ARROW_BACK -> Icons.AutoMirrored.TwoTone.ArrowBack
        AUDIOTRACK -> Icons.TwoTone.Audiotrack
        BATTERY_ALERT -> Icons.TwoTone.BatteryAlert
        BATTERY_FULL -> Icons.TwoTone.BatteryFull
        BEDTIME -> Icons.TwoTone.Bedtime
        BOLT -> Icons.TwoTone.Bolt
        CHECK -> Icons.TwoTone.Check
        CHECK_CIRCLE -> Icons.TwoTone.CheckCircle
        CHEVRON_RIGHT -> Icons.TwoTone.ChevronRight
        CLEAR_ALL -> Icons.TwoTone.ClearAll
        CLEAR_ALL_BUT_THIS -> Icons.TwoTone.PlaylistRemove
        CLOSE -> Icons.TwoTone.Close
        CLOUD -> Icons.TwoTone.Cloud
        CLOUD_UPLOAD -> Icons.TwoTone.CloudUpload
        COLOR_LENS -> Icons.TwoTone.ColorLens
        COMPUTER -> Icons.TwoTone.Computer
        CONTENT_COPY -> Icons.TwoTone.ContentCopy
        CONTENT_PASTE -> Icons.TwoTone.ContentPaste
        DELETE -> Icons.TwoTone.Delete
        DEVICES -> Icons.TwoTone.Devices
        DIRECTIONS_CAR -> Icons.TwoTone.DirectionsCar
        DOWNLOAD -> Icons.TwoTone.Download
        DOWNLOAD_DONE -> Icons.TwoTone.DownloadDone
        DOWNLOAD_FOR_OFFLINE -> Icons.TwoTone.DownloadForOffline
        DOWNLOADING -> Icons.TwoTone.Downloading
        EDIT -> Icons.TwoTone.Edit
        EQUALIZER -> Icons.TwoTone.Equalizer
        ERROR_OUTLINE -> Icons.TwoTone.ErrorOutline
        EXPAND_LESS -> Icons.TwoTone.ExpandLess
        EXPAND_MORE -> Icons.TwoTone.ExpandMore
        FAVORITE -> Icons.TwoTone.Favorite
        FAVORITE_BORDER -> Icons.TwoTone.FavoriteBorder
        FORMAT_SIZE -> Icons.TwoTone.FormatSize
        GRAPHIC_EQ -> Icons.TwoTone.GraphicEq
        HEADPHONES -> Icons.TwoTone.Headphones
        HISTORY -> Icons.TwoTone.History
        HOME -> Icons.TwoTone.Home
        ICONS -> Icons.TwoTone.Interests
        INSIGHTS -> Icons.TwoTone.Insights
        KEY -> Icons.TwoTone.Key
        KEYBOARD_ARROW_DOWN -> Icons.TwoTone.KeyboardArrowDown
        KEYBOARD_ARROW_UP -> Icons.TwoTone.KeyboardArrowUp
        LIBRARY_MUSIC -> Icons.TwoTone.LibraryMusic
        LINK -> Icons.TwoTone.Link
        LOCK -> Icons.TwoTone.Lock
        LYRICS -> Icons.TwoTone.Lyrics
        MONITOR_HEART -> Icons.TwoTone.MonitorHeart
        MORE_VERT -> Icons.TwoTone.MoreVert
        MUSIC_NOTE -> Icons.TwoTone.MusicNote
        OPEN_IN_NEW -> Icons.AutoMirrored.TwoTone.OpenInNew
        PALETTE -> Icons.TwoTone.Palette
        PAUSE -> Icons.TwoTone.Pause
        PERSON_ADD -> Icons.TwoTone.PersonAdd
        PHONE_ANDROID -> Icons.TwoTone.PhoneAndroid
        PLAY_ARROW -> Icons.TwoTone.PlayArrow
        PLAY_CIRCLE -> Icons.TwoTone.PlayCircle
        PLAYLIST_ADD -> Icons.AutoMirrored.TwoTone.PlaylistAdd
        PLAYLIST_PLAY -> Icons.AutoMirrored.TwoTone.PlaylistPlay
        PUBLIC -> Icons.TwoTone.Public
        PUSH_PIN -> Icons.TwoTone.PushPin
        PUSH_PIN_OUTLINE -> Icons.Outlined.PushPin
        QUEUE_MUSIC -> Icons.AutoMirrored.TwoTone.QueueMusic
        REFRESH -> Icons.TwoTone.Refresh
        REMOVE_CIRCLE_OUTLINE -> Icons.TwoTone.RemoveCircleOutline
        REPEAT -> Icons.TwoTone.Repeat
        REPEAT_ONE -> Icons.TwoTone.RepeatOne
        SAVE -> Icons.TwoTone.Save
        SAVE_ALT -> Icons.TwoTone.SaveAlt
        SEARCH -> Icons.TwoTone.Search
        SETTINGS -> Icons.TwoTone.Settings
        SHARE -> Icons.TwoTone.Share
        SHUFFLE -> Icons.TwoTone.Shuffle
        SKIP_NEXT -> Icons.TwoTone.SkipNext
        SKIP_PREVIOUS -> Icons.TwoTone.SkipPrevious
        SMART_DISPLAY -> Icons.TwoTone.SmartDisplay
        SPEAKER -> Icons.TwoTone.Speaker
        SPEED -> Icons.TwoTone.Speed
        STATUS_BAR -> Icons.TwoTone.CallToAction
        STOP -> Icons.TwoTone.Stop
        STOP_AFTER -> Icons.TwoTone.StopCircle
        SYSTEM_UPDATE_ALT -> Icons.TwoTone.SystemUpdateAlt
        TAB -> Icons.TwoTone.Tab
        TABLET -> Icons.TwoTone.Tablet
        TERMINAL -> Icons.TwoTone.Terminal
        TEXT_FIELDS -> Icons.TwoTone.TextFields
        TUNE -> Icons.TwoTone.Tune
        TV -> Icons.TwoTone.Tv
        VIEW_COLUMN -> Icons.TwoTone.ViewColumn
        VOLUME_DOWN -> Icons.AutoMirrored.TwoTone.VolumeDown
        VOLUME_OFF -> Icons.AutoMirrored.TwoTone.VolumeOff
        VOLUME_UP -> Icons.AutoMirrored.TwoTone.VolumeUp
        WARNING -> Icons.TwoTone.Warning
        WAVES -> Icons.TwoTone.Waves
        WIFI -> Icons.TwoTone.Wifi
    }
}
