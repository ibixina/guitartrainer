package com.guitartrainer.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.SoundPool
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

class AudioEngine(private val context: Context) {

    private val openStringFreqs = floatArrayOf(
        82.41f, 110.00f, 146.83f, 196.00f, 246.94f, 329.63f
    )

    private val sampleRate = 22050
    private val soundPool: SoundPool
    // soundId cache: key = "string_fret", value = soundPool soundId
    private val noteCache = mutableMapOf<String, Int>()

    private val wavetableSize = 2048
    private val wavetable = FloatArray(wavetableSize).also { table ->
        val harmonics = floatArrayOf(1.0f, 0.5f, 0.25f, 0.12f, 0.06f)
        for (i in table.indices) {
            val phase = i.toFloat() / wavetableSize
            for ((h, amp) in harmonics.withIndex()) {
                table[i] += amp * sin(2.0 * PI * (h + 1) * phase).toFloat()
            }
        }
        val peak = table.maxOf { abs(it) }.coerceAtLeast(0.001f)
        for (i in table.indices) table[i] /= peak
    }

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(12)
            .setAudioAttributes(attrs)
            .build()

        // Pre-generate common notes (frets 0-5 on all strings covers most chords)
        preloadNotes()
    }

    private fun preloadNotes() {
        for (string in 0 until 6) {
            for (fret in 0..5) {
                getOrCreateNote(string, fret)
            }
        }
    }

    fun noteFrequency(stringIndex: Int, fret: Int): Float {
        return openStringFreqs[stringIndex] * 2f.pow(fret / 12f)
    }

    fun playNote(stringIndex: Int, fret: Int) {
        if (stringIndex !in openStringFreqs.indices || fret < 0) return
        val soundId = getOrCreateNote(stringIndex, fret)
        soundPool.play(soundId, 0.8f, 0.8f, 1, 0, 1f)
    }

    fun playChord(chordName: String) {
        val chord = com.guitartrainer.data.ChordRepository.getChordByName(chordName) ?: return
        val notes = chord.patterns.filter { it.fret >= 0 }
        if (notes.isEmpty()) return

        // Strum effect: play each note with slight delay
        notes.forEachIndexed { i, pattern ->
            val soundId = getOrCreateNote(pattern.stringIndex, pattern.fret)
            // Volume decreases slightly per string for natural strum feel
            val vol = 0.8f - (i * 0.02f)
            soundPool.play(soundId, vol, vol, 1, 0, 1f)
        }
    }

    private fun getOrCreateNote(stringIndex: Int, fret: Int): Int {
        val key = "${stringIndex}_${fret}"
        noteCache[key]?.let { return it }

        val freq = noteFrequency(stringIndex, fret)
        val samples = generateNoteFast(freq, 0.5f)
        val soundId = loadSamplesToPool(samples)
        noteCache[key] = soundId
        return soundId
    }

    private fun generateNoteFast(freq: Float, duration: Float): ShortArray {
        val totalSamples = (sampleRate * duration).toInt()
        val buffer = ShortArray(totalSamples)
        val phaseIncrement = freq * wavetableSize / sampleRate
        var phase = 0f
        val attackSamples = (0.003f * sampleRate).toInt()
        val decayRate = 4f / totalSamples

        for (i in 0 until totalSamples) {
            val idx = phase.toInt() % wavetableSize
            val env = (if (i < attackSamples) i.toFloat() / attackSamples else 1f) *
                    exp(-decayRate * i)
            val sample = wavetable[idx] * env * 0.7f
            buffer[i] = (sample * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            phase += phaseIncrement
            if (phase >= wavetableSize) phase -= wavetableSize
        }
        return buffer
    }

    private fun loadSamplesToPool(samples: ShortArray): Int {
        // Write WAV to temp file, load into SoundPool
        val tempFile = File.createTempFile("note_", ".wav", context.cacheDir)
        try {
            FileOutputStream(tempFile).use { fos ->
                val dataSize = samples.size * 2
                val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                header.put("RIFF".toByteArray())
                header.putInt(36 + dataSize)
                header.put("WAVE".toByteArray())
                header.put("fmt ".toByteArray())
                header.putInt(16) // chunk size
                header.putShort(1) // PCM
                header.putShort(1) // mono
                header.putInt(sampleRate)
                header.putInt(sampleRate * 2) // byte rate
                header.putShort(2) // block align
                header.putShort(16) // bits per sample
                header.put("data".toByteArray())
                header.putInt(dataSize)
                fos.write(header.array())

                val dataBuffer = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
                for (s in samples) dataBuffer.putShort(s)
                fos.write(dataBuffer.array())
            }
            return soundPool.load(tempFile.absolutePath, 1)
        } finally {
            tempFile.deleteOnExit()
        }
    }

    fun release() {
        soundPool.release()
        noteCache.clear()
    }
}
