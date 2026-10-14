package com.example.surroundsplit

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.common.audio.AudioProcessor
import com.example.surroundsplit.ChannelRouteProcessor.Role

// To enable FFmpeg decoders: add the NextLib dependency (see app/build.gradle.kts) and change the
// base class to NextRenderersFactory (io.github.anilbeesetti.nextlib.media3ext.ffdecoder).
@UnstableApi
class SplitRenderersFactory(context: Context) : DefaultRenderersFactory(context) {

    init {
        setExtensionRendererMode(EXTENSION_RENDERER_MODE_PREFER)
        setEnableDecoderFallback(true)
    }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink {
        fun sink(role: Role, delay: () -> Int) = DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf<AudioProcessor>(ChannelRouteProcessor(role, delay)))
            .setEnableFloatOutput(false)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .build()

        val front = sink(Role.FRONT) { SplitConfig.frontDelayMs }
        val rear = sink(Role.REAR) { SplitConfig.rearDelayMs }
        front.setPreferredDevice(SplitConfig.frontDevice)   // null = system default
        rear.setPreferredDevice(SplitConfig.rearDevice)
        return SplitAudioSink(front, rear)
    }
}
