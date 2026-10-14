package com.example.surroundsplit

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView

@UnstableApi
class PlayerActivity : Activity() {
    private var player: ExoPlayer? = null
    private var offsetMs = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val uri = intent.data ?: run { finish(); return }

        offsetMs = Prefs.offset(this)
        SplitConfig.frontDevice = findDevice(this, Prefs.frontKey(this))
        SplitConfig.rearDevice = findDevice(this, Prefs.rearKey(this))
        SplitConfig.setSyncOffsetMs(offsetMs)
        if (SplitConfig.rearDevice == null)
            toast("Rear output device not found - rear channels will use the default output")

        val selector = DefaultTrackSelector(this)
        selector.setParameters(
            selector.buildUponParameters()
                .setConstrainAudioChannelCountToDeviceCapabilities(false) // keep 5.1, don't prefer stereo
                .setMaxAudioChannelCount(8)
        )

        val exo = ExoPlayer.Builder(this, SplitRenderersFactory(this))
            .setTrackSelector(selector)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(), true
            )
            .build()
        player = exo

        exo.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                for (g in tracks.groups) if (g.type == C.TRACK_TYPE_AUDIO)
                    for (i in 0 until g.length) if (g.isTrackSelected(i)) {
                        val f = g.getTrackFormat(i)
                        toast("Audio: ${f.sampleMimeType}, ${f.channelCount} ch")
                    }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                toast("Playback error: ${error.errorCodeName}")
            }
        })

        val view = PlayerView(this)
        view.player = exo
        setContentView(view)

        exo.setMediaItem(MediaItem.fromUri(uri))
        exo.prepare()
        exo.playWhenReady = true
    }

    /** CH+/CH- (or PgUp/PgDn): +-10 ms sync. Positive delays rear, negative delays front. */
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.action == KeyEvent.ACTION_DOWN) {
            val d = when (e.keyCode) {
                KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> 10
                KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> -10
                else -> 0
            }
            if (d != 0) {
                offsetMs = (offsetMs + d).coerceIn(-500, 500)
                SplitConfig.setSyncOffsetMs(offsetMs)
                Prefs.setOffset(this, offsetMs)
                toast("Sync offset: $offsetMs ms  (+ delays rear, - delays front)")
                return true
            }
        }
        return super.dispatchKeyEvent(e)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onStop() { super.onStop(); player?.pause() }
    override fun onDestroy() { super.onDestroy(); player?.release(); player = null }
}
