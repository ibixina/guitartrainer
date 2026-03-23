package com.guitartrainer.engine

import com.guitartrainer.model.Chord
import com.guitartrainer.model.EvaluatedTouch
import com.guitartrainer.model.TouchPoint
import kotlin.math.abs
import kotlin.math.pow

class TouchProcessor(
    private val xPpi: Float,       // horizontal pixels per inch
    private val yPpi: Float,       // vertical pixels per inch
    private val stringSpacingInches: Float,
    private val scaleLengthInches: Float = 25.5f,
    private val totalFrets: Int = 20,
    private val startX: Float,     // leftmost string X coordinate
    private val numStrings: Int = 6
) {
    private val mutePressureThreshold = 0.15f

    fun processTouches(touches: List<TouchPoint>, targetChord: Chord): List<EvaluatedTouch> {
        return touches.map { evaluateTouch(it, targetChord) }
    }

    private fun evaluateTouch(touch: TouchPoint, targetChord: Chord): EvaluatedTouch {
        val stringIndex = mapXToString(touch.x)
        val fret = mapYToFret(touch.y)
        val isMute = touch.pressure < mutePressureThreshold

        return EvaluatedTouch(
            touchId = touch.id,
            fret = fret,
            stringIndex = stringIndex,
            isCorrect = validateAgainstChord(stringIndex, fret, isMute, targetChord),
            isMute = isMute
        )
    }

    private fun mapXToString(x: Float): Int {
        val spacingPx = stringSpacingInches * xPpi
        var closest = 0
        var minDist = Float.MAX_VALUE
        for (i in 0 until numStrings) {
            val stringX = startX + (i * spacingPx)
            val dist = abs(x - stringX)
            if (dist < minDist) {
                minDist = dist
                closest = i
            }
        }
        return closest
    }

    private fun mapYToFret(y: Float): Int {
        if (y <= 0) return 0
        var prevY = 0f
        for (i in 1..totalFrets) {
            val fretY = fretDistance(scaleLengthInches, i) * yPpi
            if (y > prevY && y <= fretY) return i
            prevY = fretY
        }
        return totalFrets
    }

    private fun validateAgainstChord(stringIndex: Int, fret: Int, isMute: Boolean, chord: Chord): Boolean {
        val pattern = chord.patterns.find { it.stringIndex == stringIndex } ?: return false
        if (pattern.isMute) return isMute
        if (pattern.fret > 0) return pattern.fret == fret && !isMute
        if (pattern.fret == 0) return false
        return false
    }
}

fun fretDistance(scaleLength: Float, fret: Int): Float {
    if (fret <= 0) return 0f
    return (scaleLength - (scaleLength / 2.0.pow(fret / 12.0))).toFloat()
}

// Position just behind the fret wire (75% toward the fret)
fun fretCenterY(scaleLength: Float, fret: Int, yPpi: Float): Float {
    val prev = fretDistance(scaleLength, fret - 1)
    val curr = fretDistance(scaleLength, fret)
    return (prev + (curr - prev) * 0.75f) * yPpi
}
