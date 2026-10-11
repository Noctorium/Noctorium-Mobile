package app.noctorium.android.ui

import app.noctorium.settings.SettingEntry
import app.noctorium.settings.SettingMatch
import app.noctorium.settings.SettingsSearch

/*
 * Settings as the phone has them: the sections the list is grouped into, the pages behind its tiles, and every
 * setting on those pages, by name, with the other words people use for it -- which is what the search box at the
 * top of Settings finds them by (Base's SettingsSearch), and what takes the listener to the one they meant.
 */

/** The groups Settings' tiles are in, top to bottom. */
internal enum class SettingsSection(val title: String) {
    YOU("You"),
    LOOK("Look"),
    LISTENING("Listening"),
    SERVICES("Services"),
    PHONE("This phone"),
    ABOUT("About"),
}

/**
 * The pages under Settings, in the order the list shows them, each in its section.
 *
 * Laid out the way the desktop's are: a short list of tiles, each saying where it stands, and one page behind
 * each. What was one long page of everything about the look -- a dozen cards, the length of several screens -- is
 * a page for each part of it now, and the playback page's autoplay has a page of its own.
 */
internal enum class SettingsPage(val title: String, val section: SettingsSection, val glyph: Glyph) {
    ACCOUNT("Noctorium account", SettingsSection.YOU, Glyph.INSIGHTS),
    THEME("Theme and colour", SettingsSection.LOOK, Glyph.PALETTE),
    TEXT_AND_LAYOUT("Text and layout", SettingsSection.LOOK, Glyph.TEXT_FIELDS),
    HOME_LOOK("Home", SettingsSection.LOOK, Glyph.HOME),
    PLAYER_BAR("Player bar", SettingsSection.LOOK, Glyph.SMART_DISPLAY),
    NOW_PLAYING("Now playing", SettingsSection.LOOK, Glyph.ALBUM),
    ICONS("Icons", SettingsSection.LOOK, Glyph.ICONS),
    TABS("Tabs", SettingsSection.LOOK, Glyph.TAB),
    PLAYBACK("Playback", SettingsSection.LISTENING, Glyph.GRAPHIC_EQ),
    AUTOPLAY("Autoplay and the queue", SettingsSection.LISTENING, Glyph.ALL_INCLUSIVE),
    SOUND("Sound", SettingsSection.LISTENING, Glyph.EQUALIZER),
    LYRICS("Lyrics", SettingsSection.LISTENING, Glyph.LYRICS),
    SCROBBLING("Scrobbling", SettingsSection.LISTENING, Glyph.HISTORY),
    YOUTUBE("YouTube Music", SettingsSection.SERVICES, Glyph.PLAY_CIRCLE),
    SOUNDCLOUD("SoundCloud", SettingsSection.SERVICES, Glyph.CLOUD),
    SPOTIFY("Spotify", SettingsSection.SERVICES, Glyph.LIBRARY_MUSIC),
    BANDCAMP("Bandcamp", SettingsSection.SERVICES, Glyph.ALBUM),
    VK("VK Music", SettingsSection.SERVICES, Glyph.AUDIOTRACK),
    SEARCH("Search", SettingsSection.SERVICES, Glyph.SEARCH),
    PHONE("On this phone", SettingsSection.PHONE, Glyph.PHONE_ANDROID),
    CONNECT("Noctorium Connect", SettingsSection.PHONE, Glyph.DEVICES),
    SAVING("Saving music", SettingsSection.PHONE, Glyph.SAVE),
    UPDATES("Updates", SettingsSection.ABOUT, Glyph.SYSTEM_UPDATE_ALT),
    DIAGNOSTICS("Diagnostics", SettingsSection.ABOUT, Glyph.MONITOR_HEART),
    ;

    /** "Look › Player bar": where a setting on this page is, as the search says it. */
    val path: String get() = "${section.title} › $title"
}

/**
 * One setting as the phone has it: what the search knows it by, the page and the card it is on, and the
 * preferences it changes, named as the settings file names them (`phone.` and the like for those kept inside
 * another) -- which is how a test makes sure no setting is left out of the search.
 *
 * [whenShown] is said for a setting that is only on its page some of the time -- once signed in, say -- since
 * the search still takes the listener to its card, and should say why it is not there yet.
 */
internal data class PhoneSetting(
    val id: String,
    val title: String,
    val page: SettingsPage,
    val card: String,
    val description: String = "",
    val keywords: List<String> = emptyList(),
    val stores: List<String> = emptyList(),
    val whenShown: String? = null,
) {
    val entry: SettingEntry get() = SettingEntry(id, title, page.path, description, keywords)
}

/** The setting ids, for the places in the cards that mark where each one is. */
internal object SettingIds {
    const val ACCOUNT = "noctorium-account"
    const val YOUR_NAME = "your-name"
    const val THEME = "theme"
    const val ACCENT = "accent"
    const val SURFACES = "surfaces"
    const val CORNERS = "corners"
    const val TEXT_SIZE = "text-size"
    const val TYPEFACE = "typeface"
    const val CARD_SIZE = "card-size"
    const val SERVICE_BADGES = "service-badges"
    const val OPENS_ON = "opens-on"
    const val ANIMATIONS = "animations"
    const val HOME_PARTS = "home-parts"
    const val SEEK_BAR = "seek-bar"
    const val PLAYER_BAR_POSITION = "player-bar-position"
    const val PLAYER_BAR_LAYOUT = "player-bar-layout"
    const val TASKBAR_CLOCK = "taskbar-clock"
    const val TIME_DISPLAY = "time-display"
    const val PLAYER_BUTTONS = "player-buttons"
    const val NOW_PLAYING_LAYOUT = "now-playing-layout"
    const val COVER_SHAPE = "cover-shape"
    const val AMBIENT_BACKDROP = "ambient-backdrop"
    const val VISUALISER = "visualiser"
    const val QUEUE_COLUMNS = "queue-columns"
    const val STATUS_BAR = "status-bar"
    const val ICON_SET = "icon-set"
    const val TABS = "tabs"
    const val TAB_LABELS = "tab-labels"
    const val SWIPE_BAR = "swipe-bar"
    const val DOUBLE_TAP = "double-tap"
    const val SPEED = "speed"
    const val SKIP_SILENCE = "skip-silence"
    const val SKIP_NON_MUSIC = "skip-non-music"
    const val SLEEP_FADE = "sleep-fade"
    const val AUTOPLAY = "autoplay"
    const val AUTOPLAY_FROM = "autoplay-from"
    const val AUTOPLAY_AVOID_RECENT = "autoplay-avoid-recent"
    const val HOME_PLAY = "home-play"
    const val KEEP_QUEUE = "keep-queue"
    const val VOLUME_BOOSTER = "volume-booster"
    const val EQUALISER = "equaliser"
    const val LYRICS_SOURCE = "lyrics-source"
    const val LYRICS_SIZE = "lyrics-size"
    const val LYRICS_ALIGNMENT = "lyrics-alignment"
    const val LYRICS_DIM = "lyrics-dim"
    const val LAST_FM = "lastfm"
    const val LISTENBRAINZ = "listenbrainz"
    const val YOUTUBE_SIGN_IN = "youtube-sign-in"
    const val YOUTUBE_HISTORY = "youtube-history"
    const val SOUNDCLOUD_SIGN_IN = "soundcloud-sign-in"
    const val SOUNDCLOUD_PROFILE = "soundcloud-profile"
    const val SPOTIFY_SIGN_IN = "spotify-sign-in"
    const val SPOTIFY_PLAYBACK = "spotify-playback"
    const val SPOTIFY_OWN_APP = "spotify-own-app"
    const val BANDCAMP_NAME = "bandcamp-name"
    const val BANDCAMP_GENRES = "bandcamp-genres"
    const val VK_SIGN_IN = "vk-sign-in"
    const val HYBRID_SEARCH = "hybrid-search"
    const val KEEP_PLAYING = "keep-playing"
    const val KEEP_SCREEN_ON = "keep-screen-on"
    const val WIFI_ONLY = "wifi-only"
    const val DATA_SAVER = "data-saver"
    const val HAPTICS = "haptics"
    const val CONNECT_NAME = "connect-name"
    const val CONNECT_ENABLED = "connect-enabled"
    const val EXPORT_FOLDER = "export-folder"
    const val CHECK_UPDATES = "check-updates"
    const val DIAGNOSTICS = "diagnostics"
}

/**
 * Every setting the phone has, in the order of its pages, so that among equally good matches the search lists them
 * the way Settings does. The other words are what somebody types who does not know a setting's name: "dark mode"
 * for the theme, "louder" for the booster, "equalizer" spelt the American way.
 */
internal val PHONE_SETTINGS: List<PhoneSetting> = listOf(
    // You
    PhoneSetting(
        SettingIds.ACCOUNT, "Noctorium account", SettingsPage.ACCOUNT, "account",
        "Sign in to count what you listen to, on this phone and the desktop together.",
        listOf("sign in", "log in", "login", "profile", "statistics", "stats", "streams", "email", "password"),
    ),
    PhoneSetting(
        SettingIds.YOUR_NAME, "Your name", SettingsPage.ACCOUNT, "name",
        "What Noctorium calls you on Home's greeting.",
        listOf("profile name", "nickname", "greeting", "what to call me", "username"),
        stores = listOf("profileName"),
    ),
    // Look
    PhoneSetting(
        SettingIds.THEME, "Theme", SettingsPage.THEME, "theme",
        "The colours of the whole app, shared with the desktop.",
        listOf("dark mode", "light mode", "night mode", "day mode", "colours", "colors", "appearance", "palette", "windows 98", "windows xp", "skin"),
        stores = listOf("theme", "customTheme"),
    ),
    PhoneSetting(
        SettingIds.ACCENT, "Accent colour", SettingsPage.THEME, "colour",
        "The colour of what is lit, chosen, or playing.",
        listOf("accent color", "highlight colour", "highlight color", "tint", "button colour"),
        stores = listOf("accent", "customAccent"),
    ),
    PhoneSetting(
        SettingIds.SURFACES, "Surfaces", SettingsPage.THEME, "colour",
        "Solid panels, or liquid glass that bends what scrolls beneath it.",
        listOf("glass", "liquid glass", "blur", "translucent", "transparency"),
        stores = listOf("surfaceStyle"),
    ),
    PhoneSetting(
        SettingIds.CORNERS, "Corners", SettingsPage.THEME, "colour",
        "How round the corners of cards and buttons are.",
        listOf("rounded corners", "square corners", "radius", "roundness"),
        stores = listOf("cornerStyle"),
    ),
    PhoneSetting(
        SettingIds.TEXT_SIZE, "Text size", SettingsPage.TEXT_AND_LAYOUT, "text",
        "Everything at once, on top of the phone's own text size.",
        listOf("font size", "bigger text", "larger text", "smaller text", "zoom", "accessibility", "large print"),
        stores = listOf("textSize"),
    ),
    PhoneSetting(
        SettingIds.TYPEFACE, "Typeface", SettingsPage.TEXT_AND_LAYOUT, "text",
        "The letters everything is set in.",
        listOf("font", "serif", "monospace", "typography", "lettering"),
        stores = listOf("font"),
    ),
    PhoneSetting(
        SettingIds.CARD_SIZE, "Card size", SettingsPage.TEXT_AND_LAYOUT, "text",
        "How large the covers on Home and in the library are.",
        listOf("cover size", "artwork size", "tiles", "thumbnails"),
        stores = listOf("cardSize"),
    ),
    PhoneSetting(
        SettingIds.SERVICE_BADGES, "Service badges", SettingsPage.TEXT_AND_LAYOUT, "text",
        "The little mark on a cover saying which service it is from.",
        listOf("provider icons", "logos", "labels", "badges"),
        stores = listOf("badgePolicy"),
    ),
    PhoneSetting(
        SettingIds.OPENS_ON, "Opens on", SettingsPage.TEXT_AND_LAYOUT, "text",
        "Which screen Noctorium starts on.",
        listOf("start page", "start screen", "launch screen", "first screen", "default tab"),
        stores = listOf("startPage"),
    ),
    PhoneSetting(
        SettingIds.ANIMATIONS, "Animations", SettingsPage.TEXT_AND_LAYOUT, "text",
        "Screens easing in, pages sliding, a new song's name rising into place.",
        listOf("motion", "reduce motion", "transitions", "effects"),
        stores = listOf("animations"),
    ),
    PhoneSetting(
        SettingIds.HOME_PARTS, "What Home shows", SettingsPage.HOME_LOOK, "home",
        "The greeting, Pinned, Jump back in and each service's rows, any of which can be put away.",
        listOf("home screen", "greeting", "pinned", "jump back in", "recently played", "shelves", "rows", "hide"),
        stores = listOf("hiddenHomeParts"),
    ),
    PhoneSetting(
        SettingIds.SEEK_BAR, "Seek bar", SettingsPage.PLAYER_BAR, "player-bar",
        "On the now playing screen, and as the thin line along the player bar.",
        listOf("progress bar", "scrubber", "timeline", "slider", "wave", "position"),
        stores = listOf("progressBarStyle"),
    ),
    PhoneSetting(
        SettingIds.PLAYER_BAR_POSITION, "Where the player bar sits", SettingsPage.PLAYER_BAR, "player-bar",
        "At the foot of the screen above the tabs, or at its head.",
        listOf("top", "bottom", "position", "mini player"),
        stores = listOf("playerBarPosition"),
    ),
    PhoneSetting(
        SettingIds.PLAYER_BAR_LAYOUT, "Player bar layout", SettingsPage.PLAYER_BAR, "player-bar",
        "Classic, Slim, Controls, Spotlight, Floating, Line, Record, Taskbar or Toolbar.",
        listOf("mini player", "toolbar", "slim", "floating", "taskbar", "controls", "now playing bar"),
        stores = listOf("phone.playerBarStyle"),
    ),
    PhoneSetting(
        SettingIds.TASKBAR_CLOCK, "Show the clock", SettingsPage.PLAYER_BAR, "player-bar",
        "The time in the taskbar's tray, on the Taskbar bar and under the Windows themes.",
        listOf("time", "taskbar clock", "tray"),
        stores = listOf("taskbarClock"),
    ),
    PhoneSetting(
        SettingIds.TIME_DISPLAY, "Time shows", SettingsPage.PLAYER_BAR, "player-bar",
        "The song's length, or the time it has left.",
        listOf("remaining time", "time left", "elapsed", "countdown", "duration"),
        stores = listOf("timeDisplay"),
    ),
    PhoneSetting(
        SettingIds.PLAYER_BUTTONS, "Player buttons", SettingsPage.PLAYER_BAR, "player-buttons",
        "Which buttons the player has: shuffle, repeat, like, lyrics, queue, sleep timer, Connect, volume, speed and stop.",
        listOf("hide buttons", "shuffle button", "repeat button", "like button", "stop button", "sleep timer button", "volume button"),
        stores = listOf("phone.hiddenPlayerButtons", "phone.shownPlayerButtons"),
    ),
    PhoneSetting(
        SettingIds.NOW_PLAYING_LAYOUT, "Now playing layout", SettingsPage.NOW_PLAYING, "now-playing",
        "Classic, Full cover, Record, Cover flow, Sing along, Big type or Playlist.",
        listOf("full screen player", "cover flow", "record", "vinyl", "big type", "sing along", "playlist", "table"),
        stores = listOf("phone.nowPlayingLayout"),
    ),
    PhoneSetting(
        SettingIds.COVER_SHAPE, "Cover shape", SettingsPage.NOW_PLAYING, "now-playing",
        "Square, rounded or round, on the now playing screen.",
        listOf("artwork shape", "album art", "round cover", "circle"),
        stores = listOf("phone.artworkShape"),
    ),
    PhoneSetting(
        SettingIds.AMBIENT_BACKDROP, "Ambient backdrop", SettingsPage.NOW_PLAYING, "now-playing",
        "Tints the now playing screen with the artwork behind it.",
        listOf("background blur", "artwork background", "glow"),
        stores = listOf("ambientBackdrop"),
    ),
    PhoneSetting(
        SettingIds.VISUALISER, "Visualiser", SettingsPage.NOW_PLAYING, "playlist-layout",
        "Bars or a level meter drawn from the sound itself, in the Playlist layout.",
        listOf("visualizer", "spectrum", "analyser", "analyzer", "level meter", "vu meter", "bars"),
        stores = listOf("visualiser"),
    ),
    PhoneSetting(
        SettingIds.QUEUE_COLUMNS, "Queue columns", SettingsPage.NOW_PLAYING, "playlist-layout",
        "Which columns the Playlist layout's queue has, and in what order.",
        listOf("table", "columns", "track number", "album column", "duration column"),
        stores = listOf("queueColumns"),
    ),
    PhoneSetting(
        SettingIds.STATUS_BAR, "Status bar", SettingsPage.NOW_PLAYING, "playlist-layout",
        "The stream's format and how far into the queue, along the foot of the Playlist layout.",
        listOf("format", "codec", "bitrate", "sample rate", "kbps", "stream info"),
        stores = listOf("statusBar"),
    ),
    PhoneSetting(
        SettingIds.ICON_SET, "Icon set", SettingsPage.ICONS, "icons",
        "The icons every button is drawn with: Standard, Rounded, Outlined, Sharp, Two-tone or Line.",
        listOf("icons", "icon theme", "icon style", "outlined icons", "line icons", "symbols"),
        stores = listOf("iconSet"),
    ),
    PhoneSetting(
        SettingIds.TABS, "Tabs along the bottom", SettingsPage.TABS, "tabs",
        "Which of Search, Link, Library and Downloads have a tab.",
        listOf("navigation bar", "bottom bar", "hide tabs", "search tab", "library tab", "downloads tab", "link tab"),
        stores = listOf("phone.hiddenDestinations"),
    ),
    PhoneSetting(
        SettingIds.TAB_LABELS, "Labels under the tabs", SettingsPage.TABS, "tabs",
        "Off leaves the icons alone, which buys a little height.",
        listOf("tab names", "navigation labels", "icons only"),
        stores = listOf("phone.navigationLabels"),
    ),
    // Listening
    PhoneSetting(
        SettingIds.SWIPE_BAR, "Swipe the player bar", SettingsPage.PLAYBACK, "playback",
        "Sideways to move through the queue.",
        listOf("gestures", "swipe to skip", "swipe to change song"),
        stores = listOf("phone.swipeToChangeTrack"),
    ),
    PhoneSetting(
        SettingIds.DOUBLE_TAP, "Double tap the cover jumps", SettingsPage.PLAYBACK, "playback",
        "Back or forward by five, ten or thirty seconds.",
        listOf("seek step", "skip forward", "rewind", "ten seconds", "gestures"),
        stores = listOf("phone.seekStepSeconds"),
    ),
    PhoneSetting(
        SettingIds.SPEED, "Speed", SettingsPage.PLAYBACK, "playback",
        "Faster or slower, keeping the pitch.",
        listOf("playback speed", "tempo", "faster", "slower", "podcast speed", "rate"),
        stores = listOf("playbackSpeed", "phone.playbackSpeed"),
    ),
    PhoneSetting(
        SettingIds.SKIP_SILENCE, "Skip silence", SettingsPage.PLAYBACK, "playback",
        "Shortens gaps and long quiet intros.",
        listOf("silence", "gaps", "trim silence"),
        stores = listOf("phone.skipSilence"),
    ),
    PhoneSetting(
        SettingIds.SKIP_NON_MUSIC, "Skip what is not the music", SettingsPage.PLAYBACK, "playback",
        "Intros, outros, sponsor reads and talking in YouTube videos, as SponsorBlock marks them.",
        listOf("sponsorblock", "sponsor", "intro", "outro", "skip ads", "non-music"),
        stores = listOf("skipNonMusic"),
    ),
    PhoneSetting(
        SettingIds.SLEEP_FADE, "Sleep timer fade", SettingsPage.PLAYBACK, "playback",
        "How long a sleep timer takes to fade the music out.",
        listOf("sleep timer", "fade out", "bedtime"),
        stores = listOf("sleepFadeSeconds", "sleepTimerMinutes", "phone.sleepTimerMinutes"),
    ),
    PhoneSetting(
        SettingIds.AUTOPLAY, "Keep playing when the queue ends", SettingsPage.AUTOPLAY, "autoplay",
        "Autoplay: songs like the last one, once the queue runs out.",
        listOf("autoplay", "radio", "endless", "continue playing", "infinite"),
        stores = listOf("autoplay"),
    ),
    PhoneSetting(
        SettingIds.AUTOPLAY_FROM, "Where autoplay's songs come from", SettingsPage.AUTOPLAY, "autoplay",
        "The same service as the last song, or YouTube Music's radio for all.",
        listOf("autoplay source", "radio source", "related songs"),
        stores = listOf("autoplayFrom"),
    ),
    PhoneSetting(
        SettingIds.AUTOPLAY_AVOID_RECENT, "Skip songs played lately", SettingsPage.AUTOPLAY, "autoplay",
        "Autoplay leaves out what you have heard lately.",
        listOf("no repeats", "variety", "fresh songs"),
        stores = listOf("autoplayAvoidRecent"),
    ),
    PhoneSetting(
        SettingIds.HOME_PLAY, "Playing a song from Home or a search", SettingsPage.AUTOPLAY, "autoplay",
        "Autoplay after it, as a radio started from it, or the rest of its shelf or results.",
        listOf("home", "search", "search results", "shelf", "start radio", "song radio", "what plays next"),
        stores = listOf("homePlay"),
    ),
    PhoneSetting(
        SettingIds.KEEP_QUEUE, "Keep the queue between launches", SettingsPage.AUTOPLAY, "autoplay",
        "Noctorium opens with the queue it closed with.",
        listOf("remember queue", "restore queue", "resume", "save queue"),
        stores = listOf("keepQueue"),
    ),
    PhoneSetting(
        SettingIds.VOLUME_BOOSTER, "Volume booster", SettingsPage.SOUND, "booster",
        "Louder than the phone's loudest, up to 1000%, behind a limiter.",
        listOf("louder", "boost", "amplify", "loudness", "volume", "gain", "too quiet"),
        stores = listOf("volumeBoostPercent", "lastVolumeBoostPercent"),
    ),
    PhoneSetting(
        SettingIds.EQUALISER, "Equaliser", SettingsPage.SOUND, "equaliser",
        "Ten bands, presets and a preamp, through the phone's own equaliser.",
        listOf("equalizer", "eq", "bass", "treble", "presets", "preamp", "tone"),
        stores = listOf("equalizer"),
    ),
    PhoneSetting(
        SettingIds.LYRICS_SOURCE, "Open lyrics on", SettingsPage.LYRICS, "lyrics-source",
        "Which of the eight lyric sources is read first.",
        listOf("lyrics provider", "lrclib", "musixmatch", "genius", "words", "karaoke"),
        stores = listOf("lyricsProvider"),
    ),
    PhoneSetting(
        SettingIds.LYRICS_SIZE, "Lyrics size", SettingsPage.LYRICS, "lyrics-look",
        "How large the lyrics are set.",
        listOf("lyrics text size", "bigger lyrics", "lyrics font"),
        stores = listOf("lyrics"),
    ),
    PhoneSetting(
        SettingIds.LYRICS_ALIGNMENT, "Lyrics lined up", SettingsPage.LYRICS, "lyrics-look",
        "Against the left, the right, or centred.",
        listOf("alignment", "centre", "center", "left", "right"),
        stores = listOf("lyrics"),
    ),
    PhoneSetting(
        SettingIds.LYRICS_DIM, "Dim the other lines", SettingsPage.LYRICS, "lyrics-look",
        "So the eye finds the line being sung.",
        listOf("highlight current line", "karaoke", "fade lines"),
        stores = listOf("lyrics"),
    ),
    PhoneSetting(
        SettingIds.LAST_FM, "Last.fm", SettingsPage.SCROBBLING, "lastfm",
        "Counts what you listen to, wherever you listen.",
        listOf("scrobble", "scrobbling", "lastfm", "last fm"),
        stores = listOf("lastFmUsername"),
    ),
    PhoneSetting(
        SettingIds.LISTENBRAINZ, "ListenBrainz", SettingsPage.SCROBBLING, "listenbrainz",
        "Scrobbling to ListenBrainz, with a user token.",
        listOf("scrobble", "scrobbling", "listen brainz", "token", "musicbrainz"),
        stores = listOf("listenBrainzUsername"),
    ),
    // Services
    PhoneSetting(
        SettingIds.YOUTUBE_SIGN_IN, "YouTube Music sign-in", SettingsPage.YOUTUBE, "youtube",
        "Your playlists and likes, through YouTube's own sign-in page.",
        listOf("youtube", "google account", "log in", "cookies", "channel", "brand account"),
        stores = listOf("youtubeCookies", "youtubePageId", "youtubeChannelName", "youtubeAuthUser", "youtubeChannelPhoto", "youtubeUserAgent", "youtubeBrowser"),
    ),
    PhoneSetting(
        SettingIds.YOUTUBE_HISTORY, "Add what I play to my YouTube Music history", SettingsPage.YOUTUBE, "youtube",
        "So YouTube Music's own recommendations learn from it too.",
        listOf("watch history", "recommendations", "youtube history"),
        stores = listOf("youtubeHistory"),
        whenShown = "Once signed in to YouTube Music.",
    ),
    PhoneSetting(
        SettingIds.SOUNDCLOUD_SIGN_IN, "SoundCloud sign-in", SettingsPage.SOUNDCLOUD, "soundcloud",
        "Your likes and playlists, through SoundCloud's own sign-in page.",
        listOf("soundcloud", "log in", "cookies"),
        stores = listOf("soundCloudCookies", "soundCloudBrowser"),
    ),
    PhoneSetting(
        SettingIds.SOUNDCLOUD_PROFILE, "SoundCloud profile name", SettingsPage.SOUNDCLOUD, "soundcloud",
        "SoundCloud finds your own playlists by it.",
        listOf("soundcloud username", "profile"),
        stores = listOf("soundCloudUsername"),
    ),
    PhoneSetting(
        SettingIds.SPOTIFY_SIGN_IN, "Spotify sign-in", SettingsPage.SPOTIFY, "spotify",
        "Your playlists, liked songs and search, with any account; Premium plays in Spotify's app.",
        listOf("spotify", "connect spotify", "premium", "log in"),
        stores = listOf("spotifyAccountName", "spotifyCanPlay"),
    ),
    PhoneSetting(
        SettingIds.SPOTIFY_PLAYBACK, "Play Spotify songs on Spotify", SettingsPage.SPOTIFY, "spotify",
        "In your Spotify app, on this phone, a computer or a speaker, rather than matched on YouTube Music.",
        listOf("spotify connect", "spotify app", "play on spotify", "spotify device"),
        stores = listOf("spotifyPlayback", "spotifyDevice"),
    ),
    PhoneSetting(
        SettingIds.SPOTIFY_OWN_APP, "Use your own Spotify app", SettingsPage.SPOTIFY, "spotify",
        "Sign in through a Spotify app of your own, with its Client ID.",
        listOf("client id", "developer", "dashboard", "redirect"),
        stores = listOf("spotifyClientId"),
    ),
    PhoneSetting(
        SettingIds.BANDCAMP_NAME, "Bandcamp name", SettingsPage.BANDCAMP, "bandcamp",
        "Your collection and wishlist in the library; no sign-in needed.",
        listOf("bandcamp", "collection", "wishlist", "fan"),
        stores = listOf("bandcampUsername"),
    ),
    PhoneSetting(
        SettingIds.BANDCAMP_GENRES, "Bandcamp genres on Home", SettingsPage.BANDCAMP, "bandcamp-genres",
        "A row on Home of what sells best on Bandcamp in each genre picked.",
        listOf("genres", "best sellers", "bandcamp rows"),
        stores = listOf("bandcampGenres"),
    ),
    PhoneSetting(
        SettingIds.VK_SIGN_IN, "VK Music sign-in", SettingsPage.VK, "vk",
        "Your music and playlists through a vk.ru session.",
        listOf("vk", "vkontakte", "russian", "cookies", "remixsid"),
        stores = listOf("vkAccountName"),
    ),
    PhoneSetting(
        SettingIds.HYBRID_SEARCH, "Hybrid search", SettingsPage.SEARCH, "hybrid",
        "The services the Hybrid chip on Search asks at once.",
        listOf("search all services", "which services", "search sources", "all services"),
        stores = listOf("hybridSearch"),
    ),
    // This phone
    PhoneSetting(
        SettingIds.KEEP_PLAYING, "Keep playing when the phone locks", SettingsPage.PHONE, "keep-playing",
        "Letting Noctorium run in the background, so the music carries on with the screen off.",
        listOf("background", "battery", "battery optimisation", "battery optimization", "stops when locked", "doze"),
        stores = listOf("phone.askedAboutBackground"),
    ),
    PhoneSetting(
        SettingIds.KEEP_SCREEN_ON, "Keep the screen on", SettingsPage.PHONE, "phone",
        "While something is playing.",
        listOf("screen timeout", "stay awake", "screen off", "wake"),
        stores = listOf("phone.keepScreenOn"),
    ),
    PhoneSetting(
        SettingIds.WIFI_ONLY, "Download on Wi-Fi only", SettingsPage.PHONE, "phone",
        "Refuses downloads on mobile data.",
        listOf("wifi", "mobile data", "metered", "cellular", "downloads"),
        stores = listOf("phone.downloadOnWifiOnly"),
    ),
    PhoneSetting(
        SettingIds.DATA_SAVER, "Data saver", SettingsPage.PHONE, "phone",
        "Lower-quality audio, to use less data.",
        listOf("audio quality", "streaming quality", "low quality", "mobile data", "bandwidth"),
        stores = listOf("phone.dataSaver"),
    ),
    PhoneSetting(
        SettingIds.HAPTICS, "Haptics", SettingsPage.PHONE, "phone",
        "A short tick when a control does something.",
        listOf("vibration", "vibrate", "feedback", "buzz"),
        stores = listOf("phone.haptics"),
    ),
    PhoneSetting(
        SettingIds.CONNECT_NAME, "Name this phone", SettingsPage.CONNECT, "connect",
        "What your other devices call this one.",
        listOf("device name", "phone name"),
        stores = listOf("connect.deviceName"),
    ),
    PhoneSetting(
        SettingIds.CONNECT_ENABLED, "Let my other devices find this one", SettingsPage.CONNECT, "connect",
        "Noctorium Connect: your devices handing the music to each other on the same wifi.",
        listOf("noctorium connect", "cast", "remote control", "other devices", "handoff"),
        stores = listOf("connect.enabled", "connect.deviceId"),
    ),
    PhoneSetting(
        SettingIds.EXPORT_FOLDER, "Folder for saved music", SettingsPage.SAVING, "saving",
        "Where songs saved to the phone go.",
        listOf("export", "save", "download folder", "mp3", "music folder", "storage"),
        stores = listOf("exportFolder"),
    ),
    // About
    PhoneSetting(
        SettingIds.CHECK_UPDATES, "Look for updates when Noctorium starts", SettingsPage.UPDATES, "updates",
        "One request to GitHub; nothing downloads without asking.",
        listOf("update", "new version", "upgrade", "github", "release"),
        stores = listOf("updates.checkOnLaunch", "updates.dismissedVersion"),
    ),
    PhoneSetting(
        SettingIds.DIAGNOSTICS, "Diagnostics", SettingsPage.DIAGNOSTICS, "diagnostics",
        "Check what this phone can reach and play.",
        listOf("troubleshooting", "test connection", "debug", "problems", "not working"),
    ),
)

/**
 * What the settings file keeps that is not a setting on the phone, and why: the desktop's own, and what is
 * remembered rather than chosen. With [PHONE_SETTINGS]' stores, every preference is accounted for -- a test
 * holds to that, so a setting added to Base is not quietly left out of the phone's search.
 */
internal val NOT_PHONE_SETTINGS: Map<String, String> = mapOf(
    "playerBarStyle" to "The desktop's player bar; the phone's is phone.playerBarStyle.",
    "hoverControls" to "Controls that appear under a pointer: a phone has no pointer.",
    "backgroundDepth" to "The desktop window's background.",
    "desktop" to "The desktop's own settings.",
    "discord" to "Discord's presence needs its desktop app.",
    "discordPresenceEnabled" to "Discord's presence needs its desktop app.",
    "searchMode" to "The Search page's chip, remembered as it was left.",
)

/** Settings matching [query], best first. */
internal fun searchPhoneSettings(query: String): List<Pair<PhoneSetting, SettingMatch>> {
    val byId = PHONE_SETTINGS.associateBy(PhoneSetting::id)
    return SettingsSearch.search(query, PHONE_SETTINGS.map(PhoneSetting::entry)).map { byId.getValue(it.entry.id) to it }
}
