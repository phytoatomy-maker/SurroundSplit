package com.example.surroundsplit

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Takes 5.1 / 7.1 PCM (Android order: FL FR FC LFE BL BR [SL SR]) and outputs a STEREO 16-bit
 * stream containing either the FRONT mix or the REAR pair, with an optional delay line
 * so the two physical outputs can be time-aligned.
 */
@UnstableApi
class ChannelRouteProcessor(
    private val role: Role,
    private val delayMs: () -> Int
) : BaseAudioProcessor() {

    enum class Role { FRONT, REAR }

    private var inCh = 0
    private var isFloat = false
    private var sampleRate = 0
    private var ringL = FloatArray(0)
    private var ringR = FloatArray(0)
    private var idx = 0
    private val f = FloatArray(8)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        inCh = inputAudioFormat.channelCount
        if (inCh > 8) throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        isFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        sampleRate = inputAudioFormat.sampleRate
        resizeRing(0)
        return AudioProcessor.AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT)
    }

    private fun resizeRing(n: Int) {
        ringL = FloatArray(n); ringR = FloatArray(n); idx = 0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bps = if (isFloat) 4 else 2
        val frameBytes = bps * inCh
        val frames = inputBuffer.remaining() / frameBytes
        if (frames == 0) { inputBuffer.position(inputBuffer.limit()); return }

        val wantFrames = (sampleRate.toLong() * delayMs() / 1000).toInt()
        if (wantFrames != ringL.size) resizeRing(wantFrames)   // live delay change

        val out = replaceOutputBuffer(frames * 4)
        val oldOrder = inputBuffer.order()
        inputBuffer.order(ByteOrder.nativeOrder())
        var pos = inputBuffer.position()
        val n = ringL.size

        repeat(frames) {
            for (c in 0 until inCh) {
                f[c] = if (isFloat) inputBuffer.getFloat(pos) else inputBuffer.getShort(pos) / 32768f
                pos += bps
            }
            var l: Float
            var r: Float
            if (inCh >= 6) {
                if (role == Role.FRONT) {
                    // FL/FR + centre + LFE folded to the front pair (rear/aux has no sub)
                    l = (f[0] + 0.707f * f[2] + 0.5f * f[3]) * 0.8f
                    r = (f[1] + 0.707f * f[2] + 0.5f * f[3]) * 0.8f
                } else if (inCh >= 8) {           // 7.1: BL BR SL SR
                    l = (f[4] + 0.707f * f[6]) * 0.75f
                    r = (f[5] + 0.707f * f[7]) * 0.75f
                } else {                          // 5.1: BL BR
                    l = f[4]; r = f[5]
                }
            } else if (role == Role.FRONT) {      // stereo / mono source: front only
                l = f[0]; r = if (inCh >= 2) f[1] else f[0]
            } else { l = 0f; r = 0f }

            if (n > 0) {
                val ol = ringL[idx]; val orr = ringR[idx]
                ringL[idx] = l; ringR[idx] = r
                idx = if (idx + 1 == n) 0 else idx + 1
                l = ol; r = orr
            }
            out.putShort((l.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
            out.putShort((r.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        }
        inputBuffer.order(oldOrder)
        inputBuffer.position(inputBuffer.limit())
        out.flip()
    }

    override fun onFlush() { resizeRing(ringL.size) }   // zero the delay line (e.g. on seek)
    override fun onReset() { inCh = 0; resizeRing(0) }
}
