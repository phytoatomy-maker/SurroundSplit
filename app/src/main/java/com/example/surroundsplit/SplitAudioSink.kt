package com.example.surroundsplit

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer

/**
 * Drives TWO AudioTracks from one decoded 6-channel PCM stream:
 *   front sink -> FL/FR(+C+LFE) stereo -> front device (TV / HDMI / speaker)
 *   rear  sink -> BL/BR stereo         -> rear device  (USB / 3.5mm aux / Bluetooth)
 * The front sink is the "master" clock for ExoPlayer; the rear one follows.
 */
@UnstableApi
class SplitAudioSink(
    private val front: DefaultAudioSink,
    private val rear: DefaultAudioSink
) : ForwardingAudioSink(front) {

    private var current: ByteBuffer? = null
    private var frontView: ByteBuffer? = null
    private var rearView: ByteBuffer? = null
    private var frontDone = false
    private var rearDone = false
    private var rearBroken = false

    // Never pass compressed audio (AC3/DTS...) through to HDMI: we must decode to PCM to split it.
    override fun supportsFormat(format: Format) = getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED
    override fun getFormatSupport(format: Format): Int =
        if (format.sampleMimeType == MimeTypes.AUDIO_RAW && Util.isEncodingLinearPcm(format.pcmEncoding))
            AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
        else AudioSink.SINK_FORMAT_UNSUPPORTED

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        front.configure(inputFormat, specifiedBufferSize, outputChannels)
        rear.configure(inputFormat, specifiedBufferSize, outputChannels)
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        if (current !== buffer) {
            current = buffer
            frontView = buffer.duplicate().order(buffer.order())
            rearView = buffer.duplicate().order(buffer.order())
            frontDone = false
            rearDone = rearBroken
        }
        if (!frontDone) frontDone = front.handleBuffer(frontView!!, presentationTimeUs, encodedAccessUnitCount)
        if (!rearDone) {
            rearDone = try {
                rear.handleBuffer(rearView!!, presentationTimeUs, encodedAccessUnitCount)
            } catch (e: AudioSink.InitializationException) {
                rearBroken = true   // e.g. aux device unplugged: keep playing front only
                true
            }
        }
        if (frontDone && rearDone) {
            buffer.position(buffer.limit())
            current = null
            return true
        }
        return false
    }

    private fun resetPending() { current = null; frontView = null; rearView = null }

    override fun play() { front.play(); rear.play() }
    override fun pause() { front.pause(); rear.pause() }
    override fun handleDiscontinuity() { front.handleDiscontinuity(); rear.handleDiscontinuity() }
    override fun playToEndOfStream() { front.playToEndOfStream(); if (!rearBroken) rear.playToEndOfStream() }
    override fun isEnded() = front.isEnded && (rearBroken || rear.isEnded)
    override fun hasPendingData() = front.hasPendingData() || (!rearBroken && rear.hasPendingData())
    override fun flush() { resetPending(); front.flush(); rear.flush() }
    override fun reset() { resetPending(); rearBroken = false; front.reset(); rear.reset() }
    override fun setVolume(volume: Float) { front.setVolume(volume); rear.setVolume(volume) }
    override fun setAudioAttributes(a: AudioAttributes) { front.setAudioAttributes(a); rear.setAudioAttributes(a) }
    override fun setOutputStreamOffsetUs(o: Long) { front.setOutputStreamOffsetUs(o); rear.setOutputStreamOffsetUs(o) }
    override fun setPlaybackParameters(p: PlaybackParameters) { front.playbackParameters = p; rear.playbackParameters = p }
}
