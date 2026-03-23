package com.guitartrainer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guitartrainer.audio.AudioEngine
import com.guitartrainer.data.ChordRepository
import com.guitartrainer.ui.FretboardView

class MainActivity : ComponentActivity() {
    private lateinit var audioEngine: AudioEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioEngine = AudioEngine(this)

        setContent {
            MaterialTheme {
                var chordIndex by remember { mutableIntStateOf(0) }
                val chords = remember { ChordRepository.basicChords }
                val targetChord by remember { derivedStateOf { chords[chordIndex] } }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1A1A1A))
                ) {
                    // Left side: info panel
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Chord name
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "CHORD",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 12.sp,
                                letterSpacing = 2.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                targetChord.name,
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }

                        // Fingering info
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val stringNames = listOf("E", "A", "D", "G", "B", "e")
                            targetChord.patterns.forEach { p ->
                                val fretLabel = when {
                                    p.isMute -> "✕"
                                    p.fret == 0 -> "○"
                                    else -> "${p.fret}"
                                }
                                Text(
                                    "${stringNames[p.stringIndex]}  $fretLabel",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 14.sp
                                )
                            }
                        }

                        // Navigation
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            NavButton("◀") {
                                chordIndex = (chordIndex - 1 + chords.size) % chords.size
                            }
                            NavButton("▶") {
                                chordIndex = (chordIndex + 1) % chords.size
                            }
                        }
                    }

                    // Right side: fretboard
                    FretboardView(
                        targetChord = targetChord,
                        onChordCompleted = {
                            audioEngine.playChord(targetChord.name)
                        },
                        onNotePlay = { stringIndex, fret ->
                            audioEngine.playNote(stringIndex, fret)
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::audioEngine.isInitialized) audioEngine.release()
    }
}

@Composable
private fun NavButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
