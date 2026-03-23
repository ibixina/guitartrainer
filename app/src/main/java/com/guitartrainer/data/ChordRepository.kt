package com.guitartrainer.data

import com.guitartrainer.model.Chord
import com.guitartrainer.model.FingerType.*
import com.guitartrainer.model.FingeringPattern

object ChordRepository {
    // Strings: 0 = Low E, 1 = A, 2 = D, 3 = G, 4 = B, 5 = High E
    // Fret: 0 = Open, -1 = Muted, 1+ = Fretted
    
    val basicChords = listOf(
        Chord("C Major", listOf(
            FingeringPattern(0, -1, isMute = true),
            FingeringPattern(1, 3, RING),
            FingeringPattern(2, 2, MIDDLE),
            FingeringPattern(3, 0),
            FingeringPattern(4, 1, INDEX),
            FingeringPattern(5, 0)
        )),
        Chord("G Major", listOf(
            FingeringPattern(0, 3, MIDDLE),
            FingeringPattern(1, 2, INDEX),
            FingeringPattern(2, 0),
            FingeringPattern(3, 0),
            FingeringPattern(4, 0),
            FingeringPattern(5, 3, RING)
        )),
        Chord("D Major", listOf(
            FingeringPattern(0, -1, isMute = true),
            FingeringPattern(1, -1, isMute = true),
            FingeringPattern(2, 0),
            FingeringPattern(3, 2, INDEX),
            FingeringPattern(4, 3, RING),
            FingeringPattern(5, 2, MIDDLE)
        )),
        Chord("A Major", listOf(
            FingeringPattern(0, -1, isMute = true),
            FingeringPattern(1, 0),
            FingeringPattern(2, 2, INDEX),
            FingeringPattern(3, 2, MIDDLE),
            FingeringPattern(4, 2, RING),
            FingeringPattern(5, 0)
        )),
        Chord("E Major", listOf(
            FingeringPattern(0, 0),
            FingeringPattern(1, 2, MIDDLE),
            FingeringPattern(2, 2, RING),
            FingeringPattern(3, 1, INDEX),
            FingeringPattern(4, 0),
            FingeringPattern(5, 0)
        )),
        Chord("A Minor", listOf(
            FingeringPattern(0, -1, isMute = true),
            FingeringPattern(1, 0),
            FingeringPattern(2, 2, MIDDLE),
            FingeringPattern(3, 2, RING),
            FingeringPattern(4, 1, INDEX),
            FingeringPattern(5, 0)
        )),
        Chord("E Minor", listOf(
            FingeringPattern(0, 0),
            FingeringPattern(1, 2, MIDDLE),
            FingeringPattern(2, 2, RING),
            FingeringPattern(3, 0),
            FingeringPattern(4, 0),
            FingeringPattern(5, 0)
        )),
        Chord("D Minor", listOf(
            FingeringPattern(0, -1, isMute = true),
            FingeringPattern(1, -1, isMute = true),
            FingeringPattern(2, 0),
            FingeringPattern(3, 2, MIDDLE),
            FingeringPattern(4, 3, RING),
            FingeringPattern(5, 1, INDEX)
        )),
        Chord("F Major", listOf(
            FingeringPattern(0, -1, isMute = true),
            FingeringPattern(1, -1, isMute = true),
            FingeringPattern(2, 3, RING),
            FingeringPattern(3, 2, MIDDLE),
            FingeringPattern(4, 1, INDEX),
            FingeringPattern(5, 1, INDEX)
        ))
    )

    fun getChordByName(name: String): Chord? = basicChords.find { it.name == name }
}
