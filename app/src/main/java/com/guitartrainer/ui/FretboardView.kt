package com.guitartrainer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guitartrainer.engine.TouchProcessor
import com.guitartrainer.engine.fretCenterY
import com.guitartrainer.engine.fretDistance
import com.guitartrainer.model.Chord
import com.guitartrainer.model.EvaluatedTouch
import com.guitartrainer.model.FingerType
import com.guitartrainer.model.TouchPoint

private val MARKER_FRETS = setOf(3, 5, 7, 9, 15, 17, 19)
private val DOUBLE_MARKER_FRET = 12

private val COL_BOARD_DARK = Color(0xFF2E1A0E)
private val COL_BOARD_LIGHT = Color(0xFF5C3A1E)
private val COL_NUT = Color(0xFFF5F0E0)
private val COL_FRET = Color(0xFFB0B0B0)
private val COL_STRING_WOUND = Color(0xFFA08050)
private val COL_STRING_STEEL = Color(0xFFD0C8B8)
private val COL_TARGET = Color(0xFF4FC3F7)
private val COL_TARGET_RING = Color(0xFF0288D1)
private val COL_CORRECT = Color(0xFF66BB6A)
private val COL_WRONG = Color(0xFFEF5350)
private val COL_MUTE_OK = Color(0xFF9E9E9E)
private val COL_MUTE_BAD = Color(0xFFFF8A65)

private const val SCALE_LENGTH_INCHES = 25.5f
private const val TOTAL_FRETS = 20
private const val NUM_STRINGS = 6
private const val STRING_SPACING_MM = 7f
private const val NECK_MARGIN_MM = 4f

@Composable
fun FretboardView(
    targetChord: Chord,
    onChordCompleted: () -> Unit,
    onNotePlay: (stringIndex: Int, fret: Int) -> Unit
) {
    val context = LocalContext.current
    val dm = context.resources.displayMetrics
    val xPpi = dm.xdpi
    val yPpi = dm.ydpi
    val textMeasurer = rememberTextMeasurer()

    val stringSpacingInches = STRING_SPACING_MM / 25.4f
    val totalStringWidth = (NUM_STRINGS - 1) * stringSpacingInches * xPpi
    val marginPx = (NECK_MARGIN_MM / 25.4f) * xPpi
    val fretNumSpace = 18.dp
    val neckWidthDp = with(androidx.compose.ui.platform.LocalDensity.current) {
        ((totalStringWidth + marginPx * 2) / density).dp + fretNumSpace
    }

    var currentTouches by remember { mutableStateOf<List<TouchPoint>>(emptyList()) }
    var evaluatedTouches by remember { mutableStateOf<List<EvaluatedTouch>>(emptyList()) }
    var viewportOffsetY by remember { mutableStateOf(0f) }
    var soundedNotes by remember { mutableStateOf<Set<Pair<Int, Int>>>(emptySet()) }

    LaunchedEffect(targetChord) {
        currentTouches = emptyList()
        evaluatedTouches = emptyList()
        soundedNotes = emptySet()
    }

    Canvas(
        modifier = Modifier
            .fillMaxHeight()
            .width(neckWidthDp)
            .pointerInput(targetChord) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    soundedNotes = emptySet()
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val newTouches = event.changes.filter { it.pressed }.map {
                            TouchPoint(
                                id = it.id.value,
                                x = it.position.x,
                                y = it.position.y - viewportOffsetY,
                                pressure = it.pressure,
                                size = 0f
                            )
                        }
                        currentTouches = newTouches
                    } while (event.changes.any { it.pressed })
                    currentTouches = emptyList()
                    evaluatedTouches = emptyList()
                    soundedNotes = emptySet()
                }
            }
    ) {
        val fretNumPx = fretNumSpace.toPx()
        // Strings start from left margin, fret numbers on the right
        val startX = marginPx
        val boardWidth = totalStringWidth + marginPx * 2
        val fretNumStartX = boardWidth

        // Auto-pan vertically
        val requiredFrets = targetChord.patterns.filter { it.fret > 0 }.map { it.fret }
        if (requiredFrets.isNotEmpty()) {
            val minFret = requiredFrets.min()
            val maxFret = requiredFrets.max()
            val yMin = fretDistance(SCALE_LENGTH_INCHES, minFret - 1) * yPpi
            val yMax = fretDistance(SCALE_LENGTH_INCHES, maxFret) * yPpi
            val centerY = (yMin + yMax) / 2f
            val desired = (size.height / 2f) - centerY
            val minOffset = -(SCALE_LENGTH_INCHES * yPpi - size.height)
            viewportOffsetY = desired.coerceIn(minOffset, 0f)
        } else {
            viewportOffsetY = 0f
        }

        val processor = TouchProcessor(
            xPpi = xPpi,
            yPpi = yPpi,
            stringSpacingInches = stringSpacingInches,
            scaleLengthInches = SCALE_LENGTH_INCHES,
            totalFrets = TOTAL_FRETS,
            startX = startX,
            numStrings = NUM_STRINGS
        )

        if (currentTouches.isNotEmpty()) {
            evaluatedTouches = processor.processTouches(currentTouches, targetChord)

            val newSounded = mutableSetOf<Pair<Int, Int>>()
            evaluatedTouches.forEach { eval ->
                if (!eval.isMute) {
                    val key = eval.stringIndex to eval.fret
                    newSounded.add(key)
                    if (key !in soundedNotes) {
                        onNotePlay(eval.stringIndex, eval.fret)
                    }
                }
            }
            soundedNotes = newSounded

            val okFretted = evaluatedTouches.count { it.isCorrect && !it.isMute }
            val okMuted = evaluatedTouches.count { it.isCorrect && it.isMute }
            val needFretted = targetChord.patterns.count { it.fret > 0 }
            val needMuted = targetChord.patterns.count { it.isMute }
            if (okFretted == needFretted && okMuted == needMuted && evaluatedTouches.all { it.isCorrect }) {
                onChordCompleted()
            }
        }

        // --- DRAWING ---
        withTransform({ translate(left = 0f, top = viewportOffsetY) }) {
            // Fretboard background
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(COL_BOARD_DARK, COL_BOARD_LIGHT, COL_BOARD_DARK),
                    startX = 0f, endX = boardWidth
                ),
                topLeft = Offset(0f, -viewportOffsetY),
                size = Size(boardWidth, size.height + kotlin.math.abs(viewportOffsetY) * 2f)
            )

            // Fret number strip (right edge)
            drawRect(
                color = Color(0xFF1A1A1A),
                topLeft = Offset(fretNumStartX, -viewportOffsetY),
                size = Size(fretNumPx, size.height + kotlin.math.abs(viewportOffsetY) * 2f)
            )

            // Fret markers + numbers
            val boardCx = boardWidth / 2f
            for (fret in 1..TOTAL_FRETS) {
                val cy = fretCenterY(SCALE_LENGTH_INCHES, fret, yPpi)

                if (fret in MARKER_FRETS) {
                    drawCircle(Color(0x44FFFFFF), 8.dp.toPx(), Offset(boardCx, cy))
                } else if (fret == DOUBLE_MARKER_FRET) {
                    val spacing = totalStringWidth / 4f
                    drawCircle(Color(0x44FFFFFF), 6.dp.toPx(), Offset(boardCx - spacing, cy))
                    drawCircle(Color(0x44FFFFFF), 6.dp.toPx(), Offset(boardCx + spacing, cy))
                }

                // Fret number on right strip
                val numText = textMeasurer.measure(
                    fret.toString(),
                    TextStyle(fontSize = 9.sp, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center)
                )
                drawText(numText, topLeft = Offset(fretNumStartX + (fretNumPx - numText.size.width) / 2f, cy - numText.size.height / 2f))
            }

            // Nut
            drawRect(COL_NUT, Offset(0f, -2.dp.toPx()), Size(boardWidth, 4.dp.toPx()))

            // Frets
            for (i in 1..TOTAL_FRETS) {
                val yPos = fretDistance(SCALE_LENGTH_INCHES, i) * yPpi
                val w = if (i <= 5) 2.5f.dp.toPx() else 1.5f.dp.toPx()
                drawLine(COL_FRET, Offset(0f, yPos), Offset(boardWidth, yPos), w)
            }

            // Strings (left = Low E thick, right = High E thin)
            for (i in 0 until NUM_STRINGS) {
                val xPos = startX + (i * stringSpacingInches * xPpi)
                val isWound = i <= 2
                val strokeW = (NUM_STRINGS - i) * 0.7f + 1f
                drawLine(
                    if (isWound) COL_STRING_WOUND else COL_STRING_STEEL,
                    Offset(xPos, 0f), Offset(xPos, SCALE_LENGTH_INCHES * yPpi), strokeW
                )
            }

            // Target indicators
            targetChord.patterns.forEach { pattern ->
                val xPos = startX + (pattern.stringIndex * stringSpacingInches * xPpi)
                if (pattern.fret > 0) {
                    val yPos = fretCenterY(SCALE_LENGTH_INCHES, pattern.fret, yPpi)
                    val r = 12.dp.toPx()
                    drawCircle(COL_TARGET.copy(alpha = 0.3f), r + 3.dp.toPx(), Offset(xPos, yPos))
                    drawCircle(COL_TARGET, r, Offset(xPos, yPos))
                    drawCircle(COL_TARGET_RING, r, Offset(xPos, yPos), style = Stroke(1.5f.dp.toPx()))
                    pattern.finger?.let { finger ->
                        val label = when (finger) {
                            FingerType.INDEX -> "1"; FingerType.MIDDLE -> "2"
                            FingerType.RING -> "3"; FingerType.PINKY -> "4"; FingerType.THUMB -> "T"
                        }
                        val m = textMeasurer.measure(label, TextStyle(fontSize = 10.sp, color = Color.White, textAlign = TextAlign.Center))
                        drawText(m, topLeft = Offset(xPos - m.size.width / 2f, yPos - m.size.height / 2f))
                    }
                } else if (pattern.isMute) {
                    val yTop = -12.dp.toPx()
                    val s = 6.dp.toPx()
                    drawLine(Color.Red, Offset(xPos - s, yTop - s), Offset(xPos + s, yTop + s), 2.dp.toPx())
                    drawLine(Color.Red, Offset(xPos + s, yTop - s), Offset(xPos - s, yTop + s), 2.dp.toPx())
                } else if (pattern.fret == 0) {
                    val yTop = -12.dp.toPx()
                    drawCircle(Color.White, 6.dp.toPx(), Offset(xPos, yTop), style = Stroke(1.5f.dp.toPx()))
                }
            }

            // Touch feedback
            currentTouches.forEach { touch ->
                val eval = evaluatedTouches.find { it.touchId == touch.id }
                val color = when {
                    eval == null -> Color.Gray
                    eval.isCorrect && eval.isMute -> COL_MUTE_OK
                    eval.isCorrect -> COL_CORRECT
                    eval.isMute -> COL_MUTE_BAD
                    else -> COL_WRONG
                }
                val r = if (eval?.isMute == true) 16.dp.toPx() else 12.dp.toPx()
                drawCircle(color.copy(alpha = 0.3f), r + 4.dp.toPx(), Offset(touch.x, touch.y))
                drawCircle(color, r, Offset(touch.x, touch.y))
            }
        }
    }
}
