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
import kotlin.math.cos
import kotlin.math.exp
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
    val industrialHumFrequency: Float = 55.0f,
    val audioStatusDescription: String = "IDLE"
)

/**
 * Low-Level Procedural Background Audio Looping Engine for Chemopunk Ambient Atmosphere.
 *
 * Implements real-time PCM audio streaming via `AudioTrack` in STREAM mode to generate
 * an evocative, continuous industrial soundscape tailored to the Chemopunk setting:
 *
 * 1. Sub-Harmonic Transformer & Turbine Hum: 55Hz fundamental with 2nd, 3rd, and 4th harmonics
 *    shaped with warm analog saturation for deep subterranean machinery resonance.
 * 2. Coolant Loop / Ventilation LFO: Slow cyclic 0.07Hz sweeping modulator simulating massive
 *    heavy ventilation shafts and chemical coolant circulation.
 * 3. Pressurized Pneumatic Valve & Steam Exhaust: Procedural pink-noise filtered steam bursts
 *    with exponential decay envelopes mimicking pneumatic actuators and chemical valves.
 * 4. Chemical Percolation & Reactor Cavitation: Low-frequency FM bubble synthesis reacting to
 *    toxic hazards and character toxicity.
 * 5. High-Voltage Fluorescent Ballast Buzz: High-frequency transformer buzz oscillating with
 *    direct photon lighting intensity.
 * 6. Danger Sub-Bass Rhythm: Heartbeat/alarm drone that accelerates when hostiles are near.
 */
class ProceduralAudioManager(
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    companion object {
        private const val TAG = "ProceduralAudioManager"
        const val SAMPLE_RATE = 22050 // Lightweight 22.05 kHz sample rate for minimal CPU/Memory usage
        const val BUFFER_CHUNK_SIZE = 1024 // Small chunk buffer for low-latency streaming
        const val BASE_HUM_FREQ = 55.0 // Subterranean 55 Hz mains/transformer hum fundamental
    }

    private var audioTrack: AudioTrack? = null
    private var synthesisJob: Job? = null

    private val _audioProfile = MutableStateFlow(AmbientAudioProfile())
    val audioProfile: StateFlow<AmbientAudioProfile> = _audioProfile.asStateFlow()

    @Volatile
    private var isPlaying = false

    @Volatile
    private var isPaused = false

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
            isAudioMuted || isPaused -> "MUTED / PAUSED"
            targetDanger > 0.6f -> "DANGER PULSE (AGGRO)"
            targetDanger > 0.2f -> "CAUTION (PROXIMITY)"
            targetToxicity > 0.3f -> "CHEM-VENT CAVITATION"
            targetLighting < 0.25f -> "SUB-VOID TURBINE"
            targetLighting > 0.8f -> "HIGH-VOLTAGE RESONANCE"
            else -> "INDUSTRIAL HUM"
        }

        _audioProfile.value = AmbientAudioProfile(
            lightingIntensity = targetLighting,
            dangerLevel = targetDanger,
            toxicityRatio = targetToxicity,
            isMuted = isAudioMuted,
            masterVolume = masterVolume,
            industrialHumFrequency = BASE_HUM_FREQ.toFloat(),
            audioStatusDescription = status
        )
    }

    fun setMasterVolume(volume: Float) {
        masterVolume = volume.coerceIn(0.0f, 1.0f)
        updateAtmosphere(targetLighting, targetDanger, (targetToxicity * 100).toInt())
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

    fun pause() {
        isPaused = true
    }

    fun resume() {
        isPaused = false
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

                // Synthesis Oscillators & Phase Accumulators
                var phaseSubHum = 0.0
                var phaseHarmonic2 = 0.0
                var phaseHarmonic3 = 0.0
                var phaseHarmonic4 = 0.0
                var phaseLfo = 0.0
                var phaseChemBubble = 0.0
                var phaseDangerPulse = 0.0

                // Filter & Envelope State
                var noiseFilterState = 0.0f
                var steamTimerSamples = 0
                var steamEnvelope = 0.0f
                val steamIntervalSamples = (SAMPLE_RATE * 9.5).toInt() // Steam release every ~9.5s

                // Parameter Slewing for Click-Free Interpolation
                var smoothedLighting = 0.5f
                var smoothedDanger = 0.0f
                var smoothedTox = 0.0f

                val random = Random(1337)

                while (isActive && isPlaying) {
                    if (isAudioMuted || isPaused) {
                        pcmBuffer.fill(0)
                        audioTrack?.write(pcmBuffer, 0, pcmBuffer.size)
                        kotlinx.coroutines.delay(40)
                        continue
                    }

                    // Smooth parameter interpolation to prevent discontinuities/clicks
                    smoothedLighting += (targetLighting - smoothedLighting) * 0.04f
                    smoothedDanger += (targetDanger - smoothedDanger) * 0.06f
                    smoothedTox += (targetToxicity - smoothedTox) * 0.04f

                    // 1. Base Chemopunk Industrial Hum Frequencies
                    // Subterranean hum with subtle pitch modulation from ventilation LFO
                    val lfoMod = sin(phaseLfo) * 1.8 // +/- 1.8 Hz sweep
                    val currentHumFreq = BASE_HUM_FREQ + lfoMod
                    val subHumInc = (2.0 * PI * currentHumFreq) / SAMPLE_RATE
                    val harm2Inc = (2.0 * PI * (currentHumFreq * 2.0)) / SAMPLE_RATE
                    val harm3Inc = (2.0 * PI * (currentHumFreq * 3.0 + 0.4)) / SAMPLE_RATE // Slight detuning for rich chorusing
                    val harm4Inc = (2.0 * PI * (currentHumFreq * 4.0)) / SAMPLE_RATE
                    val lfoInc = (2.0 * PI * 0.075) / SAMPLE_RATE // 0.075 Hz slow cycle

                    // 2. High-Voltage / Lighting transformer buzz (120Hz - 180Hz)
                    val fluorescentHumFreq = 120.0 + (smoothedLighting * 50.0)
                    val fluorescentHumInc = (2.0 * PI * fluorescentHumFreq) / SAMPLE_RATE

                    // 3. Danger Pulse Frequency (Heartbeat / Alarm pace)
                    val dangerPulseFreq = 0.85 + (smoothedDanger * 2.6)
                    val dangerPulseInc = (2.0 * PI * dangerPulseFreq) / SAMPLE_RATE

                    // 4. Chemical Cavitation Bubble Frequency
                    val bubbleModFreq = 18.0 + (smoothedTox * 45.0)
                    val bubbleInc = (2.0 * PI * bubbleModFreq) / SAMPLE_RATE

                    // Dynamic Mixing Volumes
                    val industrialHumGain = 0.38f + (1.0f - smoothedLighting.coerceIn(0f, 1f)) * 0.18f
                    val highVoltageGain = (smoothedLighting * 0.16f).coerceIn(0.02f, 0.24f)
                    val dangerGain = smoothedDanger * 0.42f
                    val toxicityGain = (smoothedTox * 0.35f).coerceIn(0.0f, 0.40f)

                    for (i in 0 until BUFFER_CHUNK_SIZE) {
                        // --- A. Multi-Harmonic Industrial Hum & Transformer Drone ---
                        val rawSub = sin(phaseSubHum)
                        // Soft analog saturation polynomial: tanh(x) ~ x - (x^3 / 3)
                        val saturatedSub = (rawSub - (rawSub * rawSub * rawSub * 0.22)).toFloat()

                        val harm2 = (sin(phaseHarmonic2) * 0.35).toFloat()
                        val harm3 = (sin(phaseHarmonic3) * 0.18).toFloat()
                        val harm4 = (sin(phaseHarmonic4) * 0.08).toFloat()

                        val industrialHumSample = (saturatedSub + harm2 + harm3 + harm4) * industrialHumGain

                        // --- B. High-Voltage Fluorescent / Transformer Ballast Buzz ---
                        val fluorescentSample = (sin(phaseHarmonic2 * 1.5) * 0.6 + sin(phaseHarmonic4 * 1.2) * 0.4).toFloat() * highVoltageGain

                        // --- C. Pressurized Steam Release / Valve Exhaust ---
                        steamTimerSamples++
                        if (steamTimerSamples >= steamIntervalSamples) {
                            steamTimerSamples = 0
                            steamEnvelope = 0.45f // Trigger steam burst
                        }
                        if (steamEnvelope > 0.001f) {
                            steamEnvelope *= 0.9996f // Exponential decay
                        }

                        // Generate pink-ish noise via single-pole low-pass filter
                        val whiteNoise = (random.nextFloat() * 2f - 1f)
                        noiseFilterState = noiseFilterState * 0.88f + whiteNoise * 0.12f
                        val steamSample = noiseFilterState * steamEnvelope

                        // --- D. Chemopunk Chemical Bubble Cavitation & Geiger Clicks ---
                        val bubbleCarrier = sin(phaseChemBubble)
                        val bubbleMod = sin(phaseChemBubble * 3.5) * 0.5 + 0.5
                        val chemBubbleSample = (bubbleCarrier * bubbleMod * toxicityGain).toFloat()

                        // Geiger Poisson Clicks
                        val geigerThreshold = 0.993f - (smoothedTox * 0.06f)
                        val geigerClick = if (smoothedTox > 0.04f && random.nextFloat() > geigerThreshold) {
                            (random.nextFloat() * 2f - 1f) * (0.25f + smoothedTox * 0.45f)
                        } else {
                            0f
                        }

                        // --- E. Danger Tension Pulse / Subterranean Alarm ---
                        val pulseEnvelope = (sin(phaseDangerPulse) * 0.5 + 0.5).toFloat()
                        val dangerSample = (sin(phaseSubHum * 2.4) * pulseEnvelope * dangerGain).toFloat()

                        // --- Master Soundstage Composite ---
                        val rawMix = (industrialHumSample + fluorescentSample + steamSample + chemBubbleSample + geigerClick + dangerSample) * masterVolume
                        val clampedMix = rawMix.coerceIn(-1.0f, 1.0f)

                        pcmBuffer[i] = (clampedMix * 32767.0f).toInt().toShort()

                        // Advance Phase Accumulators
                        phaseSubHum += subHumInc
                        if (phaseSubHum > 2.0 * PI) phaseSubHum -= 2.0 * PI

                        phaseHarmonic2 += harm2Inc
                        if (phaseHarmonic2 > 2.0 * PI) phaseHarmonic2 -= 2.0 * PI

                        phaseHarmonic3 += harm3Inc
                        if (phaseHarmonic3 > 2.0 * PI) phaseHarmonic3 -= 2.0 * PI

                        phaseHarmonic4 += harm4Inc
                        if (phaseHarmonic4 > 2.0 * PI) phaseHarmonic4 -= 2.0 * PI

                        phaseLfo += lfoInc
                        if (phaseLfo > 2.0 * PI) phaseLfo -= 2.0 * PI

                        phaseChemBubble += bubbleInc
                        if (phaseChemBubble > 2.0 * PI) phaseChemBubble -= 2.0 * PI

                        phaseDangerPulse += dangerPulseInc
                        if (phaseDangerPulse > 2.0 * PI) phaseDangerPulse -= 2.0 * PI
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
