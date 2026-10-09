/*
 * Ported from RawS-Music (https://github.com/QFDY-GZC/RawS-Music), licensed under Apache-2.0.
 * A Kotlin reimplementation of its BiQuad / parametric-EQ DSP core for Halcyon's Media3 pipeline.
 * See THIRD_PARTY_LICENSES.md.
 */
package com.ella.music.dsp

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Data class representing a single EQ band.
 */
data class EqBand(
    val frequency: Float,
    val q: Float = DEFAULT_Q
) {
    companion object {
        const val DEFAULT_Q = 1.414f
    }
}

/**
 * Parametric equalizer: runs a configurable number of peaking filters over
 * interleaved floating-point PCM.
 *
 * Adapted from the ParametricEQ class in RawS-Music's dsp_engine.cpp.
 */
class ParametricEqualizer(
    sampleRate: Int,
    private val channels: Int,
    bands: List<EqBand>
) {

    private val sampleRateFloat = sampleRate.toFloat()
    private val bandSpecs: List<EqBand>
    private val filters: Array<BiQuadFilter>
    private val bandGainsDb: FloatArray
    private val enabled = AtomicBoolean(false)

    init {
        require(channels in 1..2) { "Only mono or stereo is supported" }
        bandSpecs = bands.toList()
        filters = Array(bands.size) { BiQuadFilter() }
        bandGainsDb = FloatArray(bands.size) { 0f }
        bands.forEachIndexed { index, band ->
            filters[index].setPeakEQ(sampleRateFloat, band.frequency, band.q, 0f)
        }
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled.set(enabled)
    }

    fun isEnabled(): Boolean = enabled.get()

    /**
     * Set the gain for a band in dB.
     *
     * @param bandIndex band index
     * @param gainDb gain in dB, typically in [-15, 15]
     */
    fun setBandGain(bandIndex: Int, gainDb: Float) {
        if (bandIndex !in bandGainsDb.indices) return
        bandGainsDb[bandIndex] = gainDb
        val band = bandSpecs[bandIndex]
        filters[bandIndex].setPeakEQ(sampleRateFloat, band.frequency, currentQ, gainDb)
    }

    fun getBandGain(bandIndex: Int): Float = bandGainsDb.getOrNull(bandIndex) ?: 0f

    private var currentQ = EqBand.DEFAULT_Q

    /**
     * Set a shared Q (bandwidth) for every band, reconfiguring the peaking filters while keeping
     * each band's frequency and gain. Lower Q = wider/gentler bands, higher Q = narrower/sharper.
     */
    fun setGlobalQ(q: Float) {
        val safeQ = q.coerceIn(0.3f, 10f)
        if (safeQ == currentQ) return
        currentQ = safeQ
        filters.indices.forEach { i ->
            filters[i].setPeakEQ(sampleRateFloat, bandSpecs[i].frequency, safeQ, bandGainsDb[i])
        }
    }

    /**
     * Reset all filter states. Called on seeks or format changes to avoid
     * carrying history across discontinuities.
     */
    fun reset() {
        filters.forEach { it.reset() }
    }

    /**
     * Process interleaved floating-point samples in place.
     *
     * @param samples interleaved samples, length = frames * channels
     * @param frames number of frames
     */
    fun process(samples: FloatArray, frames: Int) {
        if (!enabled.get()) return
        for (i in 0 until frames) {
            for (ch in 0 until channels) {
                val index = i * channels + ch
                var sample = samples[index]
                for (f in filters.indices) {
                    sample = filters[f].processSample(sample, ch)
                }
                samples[index] = sample
            }
        }
    }
}

/**
 * Fixed 10-band graphic equalizer.
 *
 * Center frequencies match the standard ISO-octave bands used by the UI:
 * 31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000 Hz.
 */
class TenBandEqualizer(sampleRate: Int, channels: Int) {

    private val equalizer = ParametricEqualizer(
        sampleRate,
        channels,
        CENTER_FREQUENCIES.map { EqBand(it.toFloat()) }
    )

    fun setEnabled(enabled: Boolean) {
        equalizer.setEnabled(enabled)
    }

    fun isEnabled(): Boolean = equalizer.isEnabled()

    fun setBandGain(bandIndex: Int, gainDb: Float) {
        equalizer.setBandGain(bandIndex, gainDb)
    }

    fun setGlobalQ(q: Float) {
        equalizer.setGlobalQ(q)
    }

    fun getBandGain(bandIndex: Int): Float = equalizer.getBandGain(bandIndex)

    fun reset() {
        equalizer.reset()
    }

    fun process(samples: FloatArray, frames: Int) {
        equalizer.process(samples, frames)
    }

    companion object {
        val CENTER_FREQUENCIES = listOf(
            31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000
        )
        const val BAND_COUNT = 10
    }
}
