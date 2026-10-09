/*
 * Freeverb-style algorithmic reverb for Halcyon's software DSP pipeline.
 *
 * RawS-Music's DSP engine (https://github.com/QFDY-GZC/RawS-Music, Apache-2.0) ships a graphic /
 * parametric EQ, compressor, bass/treble shelves and a stereo/surround widener, but no reverb, so
 * this is an original addition written in the same spirit as the other effects in [DspEffects].
 * It uses the classic Schroeder/Moorer topology popularised by Jezar's public-domain Freeverb:
 * a bank of parallel damped comb filters feeding a series of allpass diffusers.
 */
package com.ella.music.dsp

import kotlin.math.roundToInt

/** A single damped feedback comb filter (the "reverberant tail" generator). */
private class CombFilter(size: Int) {
    private val buffer = FloatArray(size.coerceAtLeast(1))
    private var index = 0
    private var filterStore = 0f
    var feedback = 0f
    private var damp1 = 0f
    private var damp2 = 1f

    fun setDamp(value: Float) {
        damp1 = value
        damp2 = 1f - value
    }

    fun process(input: Float): Float {
        val output = buffer[index]
        filterStore = output * damp2 + filterStore * damp1
        buffer[index] = input + filterStore * feedback
        if (++index >= buffer.size) index = 0
        return output
    }

    fun mute() {
        buffer.fill(0f)
        filterStore = 0f
    }
}

/** A Schroeder allpass filter used to diffuse the comb output and smear echoes. */
private class AllpassFilter(size: Int) {
    private val buffer = FloatArray(size.coerceAtLeast(1))
    private var index = 0
    var feedback = 0.5f

    fun process(input: Float): Float {
        val bufOut = buffer[index]
        val output = -input + bufOut
        buffer[index] = input + bufOut * feedback
        if (++index >= buffer.size) index = 0
        return output
    }

    fun mute() {
        buffer.fill(0f)
    }
}

/**
 * Stereo (or mono) Freeverb. Driven by a small set of presets (room / hall / church / plate …)
 * mapped onto the underlying room-size / damping / wet-mix parameters.
 */
class Reverb(sampleRate: Int, private val channels: Int) {

    private val scale = (sampleRate / 44100f).coerceAtLeast(0.25f)

    private val combLeft = COMB_TUNINGS.map { CombFilter((it * scale).roundToInt()) }
    private val combRight = COMB_TUNINGS.map { CombFilter(((it + STEREO_SPREAD) * scale).roundToInt()) }
    private val allpassLeft = ALLPASS_TUNINGS.map { AllpassFilter((it * scale).roundToInt()) }
    private val allpassRight = ALLPASS_TUNINGS.map { AllpassFilter(((it + STEREO_SPREAD) * scale).roundToInt()) }

    private var enabled = false
    private var wet1 = 0f
    private var wet2 = 0f
    private var dry = 1f

    init {
        allpassLeft.forEach { it.feedback = 0.5f }
        allpassRight.forEach { it.feedback = 0.5f }
    }

    fun setPreset(preset: Int) {
        val params = REVERB_PARAMS[preset]
        if (params == null || params.wet <= 0f) {
            if (enabled) mute()
            enabled = false
            return
        }
        enabled = true
        val roomSize = params.roomSize * ROOM_SCALE + ROOM_OFFSET
        val damp = params.damping * DAMP_SCALE
        combLeft.forEach { it.feedback = roomSize; it.setDamp(damp) }
        combRight.forEach { it.feedback = roomSize; it.setDamp(damp) }
        // width == 1.0 => fully separated stereo tail (wet2 == 0).
        wet1 = params.wet * (WIDTH / 2f + 0.5f)
        wet2 = params.wet * ((1f - WIDTH) / 2f)
        dry = 1f - params.wet * 0.5f
    }

    fun isActive(): Boolean = enabled

    fun reset() {
        mute()
    }

    private fun mute() {
        combLeft.forEach { it.mute() }
        combRight.forEach { it.mute() }
        allpassLeft.forEach { it.mute() }
        allpassRight.forEach { it.mute() }
    }

    fun process(samples: FloatArray, frames: Int) {
        if (!enabled) return
        if (channels == 2) processStereo(samples, frames) else processMono(samples, frames)
    }

    private fun processStereo(samples: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            val li = i * 2
            val ri = li + 1
            val inputL = samples[li]
            val inputR = samples[ri]
            val input = (inputL + inputR) * FIXED_GAIN

            var outL = 0f
            var outR = 0f
            for (c in combLeft.indices) {
                outL += combLeft[c].process(input)
                outR += combRight[c].process(input)
            }
            for (a in allpassLeft.indices) {
                outL = allpassLeft[a].process(outL)
                outR = allpassRight[a].process(outR)
            }
            samples[li] = outL * wet1 + outR * wet2 + inputL * dry
            samples[ri] = outR * wet1 + outL * wet2 + inputR * dry
        }
    }

    private fun processMono(samples: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            val inputSample = samples[i]
            val input = inputSample * 2f * FIXED_GAIN
            var out = 0f
            for (c in combLeft.indices) {
                out += combLeft[c].process(input)
            }
            for (a in allpassLeft.indices) {
                out = allpassLeft[a].process(out)
            }
            samples[i] = out * wet1 + inputSample * dry
        }
    }

    private data class ReverbParams(val roomSize: Float, val damping: Float, val wet: Float)

    companion object {
        // Comb / allpass delay lengths in samples at 44.1 kHz (Jezar's Freeverb defaults).
        private val COMB_TUNINGS = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        private val ALLPASS_TUNINGS = intArrayOf(556, 441, 341, 225)
        private const val STEREO_SPREAD = 23
        private const val FIXED_GAIN = 0.015f
        private const val ROOM_SCALE = 0.28f
        private const val ROOM_OFFSET = 0.7f
        private const val DAMP_SCALE = 0.4f
        private const val WIDTH = 1f

        // Keyed by AudioEffectSettings.REVERB_PRESET_* so PlaybackService can pass the raw preset id.
        private val REVERB_PARAMS: Map<Int, ReverbParams> = mapOf(
            10 to ReverbParams(roomSize = 0.30f, damping = 0.65f, wet = 0.12f), // Studio
            1 to ReverbParams(roomSize = 0.45f, damping = 0.55f, wet = 0.18f),  // Small room
            2 to ReverbParams(roomSize = 0.62f, damping = 0.48f, wet = 0.22f),  // Medium room
            3 to ReverbParams(roomSize = 0.74f, damping = 0.42f, wet = 0.26f),  // Large room
            4 to ReverbParams(roomSize = 0.86f, damping = 0.30f, wet = 0.30f),  // Hall
            5 to ReverbParams(roomSize = 0.92f, damping = 0.24f, wet = 0.34f),  // Church
            6 to ReverbParams(roomSize = 0.70f, damping = 0.12f, wet = 0.28f)   // Plate (bright)
        )
    }
}
