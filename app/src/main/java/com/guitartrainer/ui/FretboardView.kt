package com.guitartrainer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
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
import com.guitartrainer.model.StringFeedback
import com.guitartrainer.model.StringState
import com.guitartrainer.model.TouchPoint

private val MARKER_FRETS = setOf(3, 5, 7, 9, 15, 17, 19)
private val DOUBLE_MARKER_FRET = 12

// Minimalist Theme Colors
private val COL_BOARD_BG = Color(0xFF0A0A0A)
private val COL_SIDE_STRIP = Color.Black
private val COL_NUT = Color.White
private val COL_FRET = Color(0xFF222222)
private val COL_MARKER = Color(0xFF141414)
private val COL_STRING = Color(0xFF444444)
private val COL_TARGET_BG = Color.White
private val COL_TARGET_TEXT = Color.Black
private val COL_TARGET_MUTE = Color(0xFF333333)
private val COL_TARGET_RING = Color(0xFF222222)
private val COL_CORRECT = Color(0xFF00E676)
private val COL_WRONG = Color(0xFFFF1744)

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
    var stringFeedback by remember { mutableStateOf<Map<Int, StringFeedback>>(emptyMap()) }
    var viewportOffsetY by remember { mutableStateOf(0f) }
    var soundedNotes by remember { mutableStateOf<Set<Pair<Int, Int>>>(emptySet()) }

    val processor = remember(xPpi, yPpi) {
        TouchProcessor(
            xPpi = xPpi,
            yPpi = yPpi,
            stringSpacingInches = stringSpacingInches,
            scaleLengthInches = SCALE_LENGTH_INCHES,
            totalFrets = TOTAL_FRETS,
            startX = marginPx,
            numStrings = NUM_STRINGS
        )
    }

    LaunchedEffect(targetChord) {
        currentTouches = emptyList()
        evaluatedTouches = emptyList()
        stringFeedback = emptyMap()
        soundedNotes = emptySet()
    }

    LaunchedEffect(currentTouches, targetChord) {
        if (currentTouches.isNotEmpty()) {
            val evaluated = processor.processTouches(currentTouches, targetChord)
            val feedback = processor.buildStringFeedback(currentTouches, targetChord)
            evaluatedTouches = evaluated
            stringFeedback = feedback

            val newSounded = mutableSetOf<Pair<Int, Int>>()
            evaluated.forEach { eval ->
                val key = eval.stringIndex to eval.fret
                newSounded.add(key)
                if (key !in soundedNotes) {
                    onNotePlay(eval.stringIndex, eval.fret)
                }
            }
            soundedNotes = newSounded

            val correctStrings = feedback.filter { it.value.state == StringState.CORRECT }
                .map { it.key }.toSet()
            val needFretted = targetChord.patterns.filter { it.fret > 0 }
                .map { it.stringIndex }.toSet()
            val anyWrong = feedback.any { it.value.state == StringState.WRONG }
            if (correctStrings.containsAll(needFretted) && !anyWrong) {
                onChordCompleted()
            }
        } else {
            evaluatedTouches = emptyList()
            stringFeedback = emptyMap()
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxHeight()
            .width(neckWidthDp)
            .pointerInput(targetChord) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Press, PointerEventType.Move -> {
                                val newTouches = event.changes.filter { it.pressed }.map {
                                    TouchPoint(
                                        id = it.id.value,
                                        x = it.position.x,
                                        y = it.position.y - viewportOffsetY,
                                        pressure = it.pressure,
                                        size = 0f
                                    )
                                }
                                if (newTouches.isNotEmpty()) {
                                    currentTouches = newTouches
                                }
                                event.changes.forEach { it.consume() }
                            }
                            PointerEventType.Release -> {
                                val remaining = event.changes.filter { it.pressed }
                                if (remaining.isEmpty()) {
                                    currentTouches = emptyList()
                                    soundedNotes = emptySet()
                                } else {
                                    currentTouches = remaining.map {
                                        TouchPoint(
                                            id = it.id.value,
                                            x = it.position.x,
                                            y = it.position.y - viewportOffsetY,
                                            pressure = it.pressure,
                                            size = 0f
                                        )
                                    }
                                }
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
            }
    ) {
        val fretNumPx = fretNumSpace.toPx()
        val startX = marginPx
        val boardWidth = totalStringWidth + marginPx * 2
        val fretNumStartX = boardWidth

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

        withTransform({ translate(left = 0f, top = viewportOffsetY) }) {
            // Fretboard background
            drawRect(
                color = COL_BOARD_BG,
                topLeft = Offset(0f, -viewportOffsetY),
                size = Size(boardWidth, size.height + kotlin.math.abs(viewportOffsetY) * 2f)
            )

            // Fret number strip
            drawRect(
                color = COL_SIDE_STRIP,
                topLeft = Offset(fretNumStartX, -viewportOffsetY),
                size = Size(fretNumPx, size.height + kotlin.math.abs(viewportOffsetY) * 2f)
            )

            // Fret markers + numbers
            val boardCx = boardWidth / 2f
            for (fret in 1..TOTAL_FRETS) {
                val cy = fretCenterY(SCALE_LENGTH_INCHES, fret, yPpi)

                if (fret in MARKER_FRETS) {
                    drawCircle(COL_MARKER, 8.dp.toPx(), Offset(boardCx, cy))
                } else if (fret == DOUBLE_MARKER_FRET) {
                    val spacing = totalStringWidth / 4f
                    drawCircle(COL_MARKER, 6.dp.toPx(), Offset(boardCx - spacing, cy))
                    drawCircle(COL_MARKER, 6.dp.toPx(), Offset(boardCx + spacing, cy))
                }

                val numText = textMeasurer.measure(
                    fret.toString(),
                    TextStyle(fontSize = 9.sp, color = Color.White.copy(alpha = 0.3f), textAlign = TextAlign.Center)
                )
                drawText(numText, topLeft = Offset(fretNumStartX + (fretNumPx - numText.size.width) / 2f, cy - numText.size.height / 2f))
            }

            // Nut
            drawRect(COL_NUT, Offset(0f, -2.dp.toPx()), Size(boardWidth, 4.dp.toPx()))

            // Frets
            for (i in 1..TOTAL_FRETS) {
                val yPos = fretDistance(SCALE_LENGTH_INCHES, i) * yPpi
                val w = if (i <= 5) 1.5f.dp.toPx() else 1f.dp.toPx()
                drawLine(COL_FRET, Offset(0f, yPos), Offset(boardWidth, yPos), w)
            }

            // Strings with glow feedback
            val scaleEndY = SCALE_LENGTH_INCHES * yPpi
            for (i in 0 until NUM_STRINGS) {
                val xPos = startX + (i * stringSpacingInches * xPpi)
                val baseStrokeW = (NUM_STRINGS - i) * 0.5f + 1.5f

                val fb = stringFeedback[i]
                if (fb != null) {
                    val glowColor = if (fb.state == StringState.CORRECT) COL_CORRECT else COL_WRONG
                    drawLine(
                        glowColor.copy(alpha = 0.15f),
                        Offset(xPos, 0f), Offset(xPos, scaleEndY),
                        strokeWidth = baseStrokeW + 16.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        glowColor.copy(alpha = 0.3f),
                        Offset(xPos, 0f), Offset(xPos, scaleEndY),
                        strokeWidth = baseStrokeW + 8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        glowColor.copy(alpha = 0.6f),
                        Offset(xPos, 0f), Offset(xPos, scaleEndY),
                        strokeWidth = baseStrokeW + 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        glowColor, Offset(xPos, 0f), Offset(xPos, scaleEndY),
                        strokeWidth = baseStrokeW
                    )
                } else {
                    drawLine(COL_STRING, Offset(xPos, 0f), Offset(xPos, scaleEndY), baseStrokeW)
                }
            }

            // Target indicators
            targetChord.patterns.forEach { pattern ->
                val xPos = startX + (pattern.stringIndex * stringSpacingInches * xPpi)
                if (pattern.fret > 0) {
                    val yPos = fretCenterY(SCALE_LENGTH_INCHES, pattern.fret, yPpi)
                    val r = 10.dp.toPx()
                    drawCircle(COL_TARGET_BG, r, Offset(xPos, yPos))
                    pattern.finger?.let { finger ->
                        val label = when (finger) {
                            FingerType.INDEX -> "1"; FingerType.MIDDLE -> "2"
                            FingerType.RING -> "3"; FingerType.PINKY -> "4"; FingerType.THUMB -> "T"
                        }
                        val m = textMeasurer.measure(label, TextStyle(fontSize = 11.sp, color = COL_TARGET_TEXT, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
                        drawText(m, topLeft = Offset(xPos - m.size.width / 2f, yPos - m.size.height / 2f))
                    }
                } else if (pattern.isMute) {
                    val yTop = -14.dp.toPx()
                    val s = 5.dp.toPx()
                    drawLine(COL_TARGET_MUTE, Offset(xPos - s, yTop - s), Offset(xPos + s, yTop + s), 1.5f.dp.toPx())
                    drawLine(COL_TARGET_MUTE, Offset(xPos + s, yTop - s), Offset(xPos - s, yTop + s), 1.5f.dp.toPx())
                } else if (pattern.fret == 0) {
                    val yTop = -14.dp.toPx()
                    drawCircle(COL_TARGET_BG, 5.dp.toPx(), Offset(xPos, yTop), style = Stroke(1.5f.dp.toPx()))
                }
            }
        }
    }
}
