package com.olbyine.music

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.olbyine.music.data.SpotifyRepository
import com.olbyine.music.ui.OlbyineApp
import com.olbyine.music.ui.OlbyineTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleRedirect(intent)
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val lyrics by viewModel.parsedLyrics.collectAsStateWithLifecycle()
            OlbyineTheme(
                themeMode = state.settings.themeMode,
                artworkColors = state.artworkColors.takeIf { state.currentTrack != null },
                useWallpaper = !state.settings.dynamicAccentEnabled,
                style = state.settings.uiStyle,
            ) {
                OlbyineApp(state = state, lyrics = lyrics, viewModel = viewModel)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (com.olbyine.music.playback.usb.UsbAudioDriver.isReady) {
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                    com.olbyine.music.playback.usb.UsbAudioDriver.adjustVolume(4)
                    return true
                }
                android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    com.olbyine.music.playback.usb.UsbAudioDriver.adjustVolume(-4)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        com.olbyine.music.playback.FloatingLyrics.appInFront = true
        com.olbyine.music.playback.FloatingLyrics.active?.refresh()
    }

    override fun onPause() {
        super.onPause()
        com.olbyine.music.playback.FloatingLyrics.appInFront = false
        com.olbyine.music.playback.FloatingLyrics.active?.refresh()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleRedirect(intent)
    }

    private fun handleRedirect(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_PLAYER) {
            viewModel.navigateTo(AppDestination.NOW_PLAYING)
            intent.action = Intent.ACTION_MAIN
            return
        }
        val data = intent?.data ?: return
        if (data.toString().startsWith(SpotifyRepository.REDIRECT_URI)) {
            viewModel.completeSpotifyLogin(data)
            intent.data = null
        }
    }

    companion object {
        const val ACTION_OPEN_PLAYER = "com.olbyine.music.OPEN_PLAYER"

        fun openPlayerIntent(context: android.content.Context): android.app.PendingIntent = android.app.PendingIntent.getActivity(
            context,
            0x4C59,
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_PLAYER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
