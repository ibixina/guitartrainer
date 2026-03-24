package com.guitartrainer.engine

import com.guitartrainer.model.Chord
import com.guitartrainer.model.EvaluatedTouch
import com.guitartrainer.model.StringFeedback
import com.guitartrainer.model.StringState
import com.guitartrainer.model.TouchPoint
import kotlin.math.abs
import kotlin.math.pow

class TouchProcessor(
    private val xPpi: Float,
    private val yPpi: Float,
    private val stringSpacingInches: Float,
    private val scaleLengthInches: Float = 25.5f,
    private val totalFrets: Int = 20,
    private val startX: Float,
    private val numStrings: Int = 6
) {
    private val spacingPx get() = stringSpacingInches * xPpi
    // A narrower radius so it only hits multiple strings if you press flat/between them
    private val touchRadiusPx get() = spacingPx * 0.65f

    fun processTouches(touches: List<TouchPoint>, targetChord: Chord): List<EvaluatedTouch> {
        val rawHits = mutableListOf<Triple<TouchPoint, Int, Int>>()
        for (touch in touches) {
            val coveredStrings = getStringsInRange(touch.x)
            val fret = mapYToFret(touch.y)
            for (si in coveredStrings) {
                rawHits.add(Triple(touch, si, fret))
            }
        }

        // Group by string — on a real guitar the highest fret (closest to body) sounds
        val perString = rawHits.groupBy { it.second }
        val effectiveHits = perString.map { (stringIndex, hits) ->
            hits.maxBy { it.third }
        }

        return effectiveHits.map { (touch, stringIndex, fret) ->
            val isCorrect = validateHit(stringIndex, fret, targetChord)
            EvaluatedTouch(
                touchId = touch.id,
                fret = fret,
                stringIndex = stringIndex,
                isCorrect = isCorrect,
                isMute = false
            )
        }
    }

    fun buildStringFeedback(
        touches: List<TouchPoint>,
        targetChord: Chord
    ): Map<Int, StringFeedback> {
        val rawHits = mutableListOf<Triple<TouchPoint, Int, Int>>()
        for (touch in touches) {
            val coveredStrings = getStringsInRange(touch.x)
            val fret = mapYToFret(touch.y)
            for (si in coveredStrings) {
                rawHits.add(Triple(touch, si, fret))
            }
        }

        // Group by string, keep highest fret (closest to body)
        val perString = rawHits.groupBy { it.second }

        val feedback = mutableMapOf<Int, StringFeedback>()
        for ((stringIndex, hits) in perString) {
            val effectiveFret = hits.maxOf { it.third }
            val pattern = targetChord.patterns.find { it.stringIndex == stringIndex }

            val state = when {
                pattern == null -> StringState.WRONG
                pattern.isMute -> StringState.WRONG // touching a muted string
                pattern.fret == 0 -> StringState.WRONG // touching an open string
                pattern.fret > 0 && effectiveFret == pattern.fret -> StringState.CORRECT // strict exact fret match
                else -> StringState.WRONG
            }
            feedback[stringIndex] = StringFeedback(stringIndex, effectiveFret, state)
        }
        return feedback
    }

    private fun validateHit(stringIndex: Int, fret: Int, chord: Chord): Boolean {
        val pattern = chord.patterns.find { it.stringIndex == stringIndex } ?: return false
        if (pattern.isMute) return false
        if (pattern.fret == 0) return false // open strings shouldn't be pressed
        // Strict exact fret match required
        return pattern.fret == fret
    }

    private fun getStringsInRange(x: Float): List<Int> {
        val result = mutableListOf<Int>()
        for (i in 0 until numStrings) {
            val stringX = startX + (i * spacingPx)
            if (abs(x - stringX) <= touchRadiusPx) {
                result.add(i)
            }
        }
        // If somehow nothing matched, snap to closest
        if (result.isEmpty()) {
            result.add(mapXToClosestString(x))
        }
        return result
    }

    private fun mapXToClosestString(x: Float): Int {
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
}

fun fretDistance(scaleLength: Float, fret: Int): Float {
    if (fret <= 0) return 0f
    return (scaleLength - (scaleLength / 2.0.pow(fret / 12.0))).toFloat()
}

fun fretCenterY(scaleLength: Float, fret: Int, yPpi: Float): Float {
    val prev = fretDistance(scaleLength, fret - 1)
    val curr = fretDistance(scaleLength, fret)
    return (prev + (curr - prev) * 0.75f) * yPpi
}
