package com.guitartrainer.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlin.math.*

class AudioEngine(context: android.content.Context) {

    private val openStringFreqs = floatArrayOf(
        82.41f,  // Low E
        110.00f, // A
        146.83f, // D
        196.00f, // G
        246.94f, // B
        329.63f  // High E
    )

    private val sampleRate = 22050
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun noteFrequency(stringIndex: Int, fret: Int): Float {
        return openStringFreqs[stringIndex] * 2f.pow(fret / 12f)
    }

    fun playNote(stringIndex: Int, fret: Int) {
        if (stringIndex !in openStringFreqs.indices || fret < 0) return
        val freq = noteFrequency(stringIndex, fret)
        scope.launch {
            val samples = generateNote(freq, 0.8f)
            playBuffer(samples)
        }
    }

    fun playChord(chordName: String) {
        val chord = com.guitartrainer.data.ChordRepository.getChordByName(chordName) ?: return
        val notes = chord.patterns
            .filter { it.fret >= 0 }
            .map { noteFrequency(it.stringIndex, it.fret) }
        if (notes.isEmpty()) return

        scope.launch {
            val samples = generateStrum(notes)
            playBuffer(samples)
        }
    }

    private fun generateNote(freq: Float, duration: Float): ShortArray {
        val totalSamples = (sampleRate * duration).toInt()
        val buffer = FloatArray(totalSamples)
        addNote(buffer, freq, 0, totalSamples)

        val peak = buffer.maxOf { abs(it) }.coerceAtLeast(0.001f)
        val scale = 0.8f / peak
        return ShortArray(totalSamples) {
            (buffer[it] * scale * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun generateStrum(frequencies: List<Float>): ShortArray {
        val duration = 1.5f
        val strumDelay = 0.02f
        val totalSamples = (sampleRate * (duration + strumDelay * frequencies.size)).toInt()
        val buffer = FloatArray(totalSamples)

        frequencies.forEachIndexed { i, freq ->
            val offset = (i * strumDelay * sampleRate).toInt()
            addNote(buffer, freq, offset, (duration * sampleRate).toInt())
        }

        val peak = buffer.maxOf { abs(it) }.coerceAtLeast(0.001f)
        val scale = 0.8f / peak
        return ShortArray(totalSamples) {
            (buffer[it] * scale * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun addNote(buffer: FloatArray, freq: Float, offset: Int, length: Int) {
        val harmonics = floatArrayOf(1.0f, 0.5f, 0.25f, 0.12f, 0.06f)
        val end = min(offset + length, buffer.size)

        for (s in offset until end) {
            val t = (s - offset).toFloat() / sampleRate
            val env = envelope(t, length.toFloat() / sampleRate)
            var sample = 0f
            for ((h, amp) in harmonics.withIndex()) {
                val hFreq = freq * (h + 1)
                if (hFreq > sampleRate / 2f) break
                sample += amp * sin(2.0 * PI * hFreq * t).toFloat()
            }
            buffer[s] += sample * env
        }
    }

    private fun envelope(t: Float, duration: Float): Float {
        val attack = 0.005f
        val a = if (t < attack) t / attack else 1f
        return a * exp(-3f * t / duration)
    }

    private fun playBuffer(samples: ShortArray) {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSize = (samples.size * 2).coerceAtLeast(minBuf)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(samples, 0, samples.size)
        track.play()

        val durationMs = (samples.size * 1000L) / sampleRate
        scope.launch {
            delay(durationMs + 200)
            track.release()
        }
    }

    fun release() {
        scope.cancel()
    }
}
