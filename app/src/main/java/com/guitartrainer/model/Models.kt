package com.guitartrainer.model

enum class FingerType {
    THUMB, INDEX, MIDDLE, RING, PINKY
}

enum class NoteStatus {
    OPEN,      // String played open
    FRETTED,   // String pressed down at a fret
    MUTED      // String intentionally muted
}

data class FingeringPattern(
    val stringIndex: Int, // 0 is low E, 5 is high E
    val fret: Int,        // 0 is open, -1 is muted, 1+ is fretted
    val finger: FingerType? = null,
    val isMute: Boolean = false // If true, indicates a muted string (usually 'X' in chord diagrams)
)

data class Chord(
    val name: String,
    val patterns: List<FingeringPattern>
)

data class TouchPoint(
    val id: Long,
    val x: Float,
    val y: Float,
    val pressure: Float,
    val size: Float
)

data class EvaluatedTouch(
    val touchId: Long,
    val fret: Int,
    val stringIndex: Int,
    val isCorrect: Boolean,
    val isMute: Boolean
)
