package com.olbyine.music

import android.app.Application
import com.olbyine.music.data.AppleMusicClient
import com.olbyine.music.data.MusicLibraryRepository
import com.olbyine.music.data.OnlineLyricsRepository
import com.olbyine.music.data.OnlineMetadataRepository
import com.olbyine.music.data.SettingsRepository
import com.olbyine.music.data.SpotifyRepository
import com.olbyine.music.playback.PlaybackConnection

class OlbyineApplication : Application() {
    lateinit var musicLibrary: MusicLibraryRepository
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var appleMusic: AppleMusicClient
        private set
    lateinit var onlineLyrics: OnlineLyricsRepository
        private set
    lateinit var playback: PlaybackConnection
        private set
    lateinit var onlineMetadata: OnlineMetadataRepository
        private set
    lateinit var netease: com.olbyine.music.data.NeteaseClient
        private set
    lateinit var lyricsHub: com.olbyine.music.data.LyricsHub
        private set
    val soundCloud: com.olbyine.music.data.SoundCloudClient by lazy { com.olbyine.music.data.SoundCloudClient(this) }
    val navidrome: com.olbyine.music.data.NavidromeClient by lazy { com.olbyine.music.data.NavidromeClient(this) }
    lateinit var webDav: com.olbyine.music.data.WebDavRepository
        private set
    val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
    val spotify: SpotifyRepository by lazy { SpotifyRepository(this) }

    @get:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    val motionCache: androidx.media3.datasource.cache.SimpleCache by lazy {
        androidx.media3.datasource.cache.SimpleCache(
            java.io.File(cacheDir, "motion"),
            androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor(200L * 1024 * 1024),
            androidx.media3.database.StandaloneDatabaseProvider(this),
        )
    }

    override fun onCreate() {
        super.onCreate()
        musicLibrary = MusicLibraryRepository(this)
        settings = SettingsRepository(this)
        appleMusic = AppleMusicClient(this)
        onlineLyrics = OnlineLyricsRepository(appleMusic)
        playback = PlaybackConnection(this)
        onlineMetadata = OnlineMetadataRepository(this, appleMusic)
        netease = com.olbyine.music.data.NeteaseClient(this)
        lyricsHub = com.olbyine.music.data.LyricsHub(musicLibrary, netease, appScope)
        webDav = com.olbyine.music.data.WebDavRepository(this)
        musicLibrary.remoteHeaders = webDav::headersFor
        com.olbyine.music.playback.usb.UsbAudioDriver.init(this)
    }
}
