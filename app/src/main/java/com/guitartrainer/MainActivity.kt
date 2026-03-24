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
import androidx.compose.ui.text.font.FontFamily
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
                        .background(Color.Black)
                ) {
                    // Left side: info panel
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(vertical = 32.dp, horizontal = 12.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Chord name
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "TARGET\nCHORD",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                letterSpacing = 2.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                targetChord.name.replace(" ", "\n"), // Stack the name nicely
                                color = Color.White,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Light,
                                textAlign = TextAlign.Center,
                                lineHeight = 36.sp
                            )
                        }

                        // Fingering info
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF111111))
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val stringNames = listOf("E", "A", "D", "G", "B", "e")
                            targetChord.patterns.forEach { p ->
                                val fretLabel = when {
                                    p.isMute -> "✕"
                                    p.fret == 0 -> "○"
                                    else -> "${p.fret}"
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        stringNames[p.stringIndex],
                                        color = Color.Gray,
                                        fontSize = 15.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        fretLabel,
                                        color = if (p.isMute) Color(0xFF666666) else Color.White,
                                        fontSize = 15.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = if (p.fret > 0) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }

                        // Navigation stacked vertically so it doesn't get cut off
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            NavButton("PREV") {
                                chordIndex = (chordIndex - 1 + chords.size) % chords.size
                            }
                            NavButton("NEXT") {
                                chordIndex = (chordIndex + 1) % chords.size
                            }
                        }
                    }

                    // Right side: fretboard
                    Box(modifier = Modifier.fillMaxHeight()) {
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
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF1A1A1A))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
    }
}
