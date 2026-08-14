package com.example.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural Audio synthesis parameters based on the wasteland environment.
 */
data class AmbientAudioProfile(
    val lightingIntensity: Float = 0.5f,
    val dangerLevel: Float = 0.0f,     // 0.0 (Safe / Patrol) to 1.0 (High Danger / Combat / Aggro)
    val toxicityRatio: Float = 0.0f,   // 0.0 to 1.0
    val isMuted: Boolean = false,
    val masterVolume: Float = 0.65f,
    val audioStatusDescription: String = "IDLE"
)

/**
 * Lightweight Procedural Audio Synthesis Engine for 2.5D Wasteland Atmosphere.
 *
 * Uses low-overhead, real-time PCM audio synthesis (`AudioTrack` in STREAM mode)
 * to generate dynamic, seamless ambient soundscapes without external audio asset dependencies:
 *
 * 1. Deep Sub-Bass Drone: Modulated by dark lighting conditions (darker vaults = ominous deep sub-bass).
 * 2. High-Voltage Dynamo Hum & Light Resonance: Oscillating higher harmonics reacting to direct point-light photon intensity.
 * 3. Danger / Proximity Pulse (Heartbeat & Alarm Drone): Rises when aggressive NPCs enter detection perimeter or when in melee range.
 * 4. Geiger Counter Static / Radiation Clicks: Procedural Poisson distribution crackle reacting to character toxicity level.
 */
class ProceduralAudioManager(
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    companion object {
        private const val TAG = "ProceduralAudioManager"
        private const val SAMPLE_RATE = 22050 // Lightweight 22.05 kHz sample rate for minimal CPU/Memory usage
        private const val BUFFER_CHUNK_SIZE = 1024 // Small chunk buffer for low latency streaming
    }

    private var audioTrack: AudioTrack? = null
    private var synthesisJob: Job? = null

    private val _audioProfile = MutableStateFlow(AmbientAudioProfile())
    val audioProfile: StateFlow<AmbientAudioProfile> = _audioProfile.asStateFlow()

    @Volatile
    private var isPlaying = false

    @Volatile
    private var targetLighting = 0.5f

    @Volatile
    private var targetDanger = 0.0f

    @Volatile
    private var targetToxicity = 0.0f

    @Volatile
    private var isAudioMuted = false

    @Volatile
    private var masterVolume = 0.65f

    init {
        startAudioSynthesis()
    }

    /**
     * Updates the environmental parameters driving the ambient sound synthesizer.
     *
     * @param lightingIntensity Current light scalar at character's tile (0.0 = pitch dark, 1.5+ = bright torch/flare).
     * @param dangerLevel Proximity to aggressive NPCs or combat status (0.0 = peaceful patrol, 1.0 = adjacent enemy aggro).
     * @param toxicity Current radiation/poison percentage of the player (0..100).
     */
    fun updateAtmosphere(
        lightingIntensity: Float,
        dangerLevel: Float,
        toxicity: Int
    ) {
        targetLighting = lightingIntensity.coerceIn(0.0f, 2.0f)
        targetDanger = dangerLevel.coerceIn(0.0f, 1.0f)
        targetToxicity = (toxicity / 100f).coerceIn(0.0f, 1.0f)

        val status = when {
            isAudioMuted -> "MUTED"
            targetDanger > 0.6f -> "DANGER PULSE (AGGRO)"
            targetDanger > 0.2f -> "CAUTION (PROXIMITY)"
            targetLighting < 0.25f -> "SUB-VOID DRONE"
            targetLighting > 0.8f -> "GENERATOR RESONANCE"
            else -> "SECTOR AMBIENCE"
        }

        _audioProfile.value = AmbientAudioProfile(
            lightingIntensity = targetLighting,
            dangerLevel = targetDanger,
            toxicityRatio = targetToxicity,
            isMuted = isAudioMuted,
            masterVolume = masterVolume,
            audioStatusDescription = status
        )
    }

    /**
     * Toggles procedural sound on/off.
     */
    fun toggleMute() {
        isAudioMuted = !isAudioMuted
        updateAtmosphere(targetLighting, targetDanger, (targetToxicity * 100).toInt())
    }

    fun setMuted(muted: Boolean) {
        isAudioMuted = muted
        updateAtmosphere(targetLighting, targetDanger, (targetToxicity * 100).toInt())
    }

    /**
     * Starts the background synthesis worker thread.
     */
    fun startAudioSynthesis() {
        if (isPlaying) return
        isPlaying = true

        synthesisJob = coroutineScope.launch(Dispatchers.Default) {
            try {
                val minBufferSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = maxOf(minBufferSize, BUFFER_CHUNK_SIZE * 4)

                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                val audioFormat = AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()

                audioTrack = AudioTrack(
                    audioAttributes,
                    audioFormat,
                    bufferSize,
                    AudioTrack.MODE_STREAM,
                    android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
                )

                audioTrack?.play()

                val pcmBuffer = ShortArray(BUFFER_CHUNK_SIZE)
                var phaseDrone = 0.0
                var phaseSub = 0.0
                var phasePulse = 0.0
                var phaseHum = 0.0
                var smoothedLighting = 0.5f
                var smoothedDanger = 0.0f
                var smoothedTox = 0.0f

                val random = Random(42)

                while (isActive && isPlaying) {
                    if (isAudioMuted) {
                        pcmBuffer.fill(0)
                        audioTrack?.write(pcmBuffer, 0, pcmBuffer.size)
                        kotlinx.coroutines.delay(40)
                        continue
                    }

                    // Smooth parameter interpolation to prevent pops/clicks
                    smoothedLighting += (targetLighting - smoothedLighting) * 0.05f
                    smoothedDanger += (targetDanger - smoothedDanger) * 0.08f
                    smoothedTox += (targetToxicity - smoothedTox) * 0.05f

                    // Base frequency calculations
                    // Dark areas produce deeper, heavier bass drone (45Hz - 60Hz)
                    val baseSubFreq = 42.0 + (1.0f - smoothedLighting.coerceIn(0f, 1f)) * 24.0
                    // Direct lights create high-voltage 120Hz/240Hz fluorescent/transformer hum
                    val humFreq = 110.0 + (smoothedLighting * 60.0)
                    // Danger accelerates an ominous 1.2Hz - 3.8Hz sub-pulse rhythm
                    val dangerPulseFreq = 0.8 + (smoothedDanger * 2.8)

                    val subIncrement = (2.0 * PI * baseSubFreq) / SAMPLE_RATE
                    val droneIncrement = (2.0 * PI * (baseSubFreq * 1.5)) / SAMPLE_RATE
                    val humIncrement = (2.0 * PI * humFreq) / SAMPLE_RATE
                    val pulseIncrement = (2.0 * PI * dangerPulseFreq) / SAMPLE_RATE

                    val dangerVolume = smoothedDanger * 0.45f
                    val darknessVolume = (1.0f - (smoothedLighting * 0.7f)).coerceIn(0.15f, 0.55f)
                    val lightHumVolume = (smoothedLighting * 0.28f).coerceIn(0.02f, 0.35f)
                    val geigerThreshold = 0.992f - (smoothedTox * 0.07f) // Lower threshold = more frequent random static crackles

                    for (i in 0 until BUFFER_CHUNK_SIZE) {
                        // 1. Heavy Sub-Bass Sine Drone (Industrial atmosphere)
                        val subSample = sin(phaseSub) * darknessVolume

                        // 2. Secondary minor harmonic drone with slight chorusing
                        val droneSample = sin(phaseDrone) * (darknessVolume * 0.5f)

                        // 3. Electrical/Generator Hum (Oscillating with slight noise)
                        val humSample = sin(phaseHum) * lightHumVolume

                        // 4. Danger Pulse / Tension Beat
                        val pulseModulator = (sin(phasePulse) * 0.5 + 0.5).toFloat()
                        val dangerAlarm = (sin(phaseDrone * 2.2) * pulseModulator) * dangerVolume

                        // 5. Radiation / Geiger Counter crackle
                        val isGeigerClick = if (smoothedTox > 0.05f && random.nextFloat() > geigerThreshold) {
                            (random.nextFloat() * 2f - 1f) * (0.3f + smoothedTox * 0.5f)
                        } else {
                            0f
                        }

                        // Combine all procedural stems
                        val rawMix = (subSample + droneSample + humSample + dangerAlarm + isGeigerClick) * masterVolume
                        val clampedMix = rawMix.coerceIn(-1.0f, 1.0f)

                        pcmBuffer[i] = (clampedMix * 32767).toInt().toShort()

                        // Advance wave phases
                        phaseSub += subIncrement
                        if (phaseSub > 2.0 * PI) phaseSub -= 2.0 * PI

                        phaseDrone += droneIncrement
                        if (phaseDrone > 2.0 * PI) phaseDrone -= 2.0 * PI

                        phaseHum += humIncrement
                        if (phaseHum > 2.0 * PI) phaseHum -= 2.0 * PI

                        phasePulse += pulseIncrement
                        if (phasePulse > 2.0 * PI) phasePulse -= 2.0 * PI
                    }

                    audioTrack?.write(pcmBuffer, 0, pcmBuffer.size)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Procedural audio synthesis error: ${e.message}", e)
            } finally {
                cleanUpTrack()
            }
        }
    }

    private fun cleanUpTrack() {
        try {
            audioTrack?.apply {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                    stop()
                }
                release()
            }
            audioTrack = null
        } catch (e: Exception) {
            Log.w(TAG, "AudioTrack cleanup note: ${e.message}")
        }
    }

    /**
     * Stops the synthesis engine and releases system audio resources.
     */
    fun release() {
        isPlaying = false
        synthesisJob?.cancel()
        cleanUpTrack()
    }
}
