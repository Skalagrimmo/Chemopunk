package com.example

import com.example.engine.AmbientAudioProfile
import com.example.engine.ProceduralAudioManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class ProceduralAudioEngineTest {

    @Test
    fun testDefaultAmbientAudioProfile() {
        val profile = AmbientAudioProfile()
        assertEquals(0.5f, profile.lightingIntensity, 0.001f)
        assertEquals(0.0f, profile.dangerLevel, 0.001f)
        assertEquals(0.0f, profile.toxicityRatio, 0.001f)
        assertFalse(profile.isMuted)
        assertEquals(0.65f, profile.masterVolume, 0.001f)
        assertEquals(55.0f, profile.industrialHumFrequency, 0.001f)
    }

    @Test
    fun testAtmosphericParameterClampingAndStatus() {
        val manager = ProceduralAudioManager()
        try {
            // 1. Safe sector with normal lighting
            manager.updateAtmosphere(lightingIntensity = 0.6f, dangerLevel = 0.0f, toxicity = 0)
            assertEquals("INDUSTRIAL HUM", manager.audioProfile.value.audioStatusDescription)
            assertEquals(0.6f, manager.audioProfile.value.lightingIntensity, 0.001f)

            // 2. Sub-void dark vault
            manager.updateAtmosphere(lightingIntensity = 0.1f, dangerLevel = 0.0f, toxicity = 0)
            assertEquals("SUB-VOID TURBINE", manager.audioProfile.value.audioStatusDescription)

            // 3. High radiation chem-hazard zone
            manager.updateAtmosphere(lightingIntensity = 0.5f, dangerLevel = 0.1f, toxicity = 55)
            assertEquals("CHEM-VENT CAVITATION", manager.audioProfile.value.audioStatusDescription)

            // 4. Proximity danger & aggro
            manager.updateAtmosphere(lightingIntensity = 0.5f, dangerLevel = 0.85f, toxicity = 10)
            assertEquals("DANGER PULSE (AGGRO)", manager.audioProfile.value.audioStatusDescription)

            // 5. Mute toggle
            manager.toggleMute()
            assertTrue(manager.audioProfile.value.isMuted)
            assertEquals("MUTED / PAUSED", manager.audioProfile.value.audioStatusDescription)
        } finally {
            manager.release()
        }
    }

    @Test
    fun testIndustrialHumWaveformSynthesisMath() {
        val baseFreq = 55.0
        val sampleRate = 22050
        val subHumInc = (2.0 * PI * baseFreq) / sampleRate
        val harm2Inc = (2.0 * PI * (baseFreq * 2.0)) / sampleRate
        val harm3Inc = (2.0 * PI * (baseFreq * 3.0)) / sampleRate

        var phase1 = 0.0
        var phase2 = 0.0
        var phase3 = 0.0

        for (i in 0 until 128) {
            val rawSub = sin(phase1)
            val saturatedSub = (rawSub - (rawSub * rawSub * rawSub * 0.22)).toFloat()
            val harm2 = (sin(phase2) * 0.35).toFloat()
            val harm3 = (sin(phase3) * 0.18).toFloat()

            val composite = (saturatedSub + harm2 + harm3) * 0.45f
            assertTrue("Composite wave must stay within unit dynamic range", composite in -1.0f..1.0f)

            phase1 += subHumInc
            phase2 += harm2Inc
            phase3 += harm3Inc
        }
    }

    @Test
    fun testSteamReleaseNoiseDecayEnvelope() {
        var envelope = 0.45f
        val decayRate = 0.9996f

        // Simulate 5000 samples of decay
        for (i in 0 until 5000) {
            envelope *= decayRate
        }

        assertTrue("Envelope should exponentially decay", envelope < 0.10f)
        assertTrue("Envelope should remain positive", envelope > 0.0f)
    }
}
