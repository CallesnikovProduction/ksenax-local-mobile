package com.kolesnikovprod.ksetaorch.ui.main.launch

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.kolesnikovprod.ksetaorch.ui.main.launch.common.*
import com.kolesnikovprod.ksetaorch.ui.main.launch.common.glitch.glitchMotion
import com.kolesnikovprod.ksetaorch.ui.main.launch.common.glitch.glitchOffset
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

/**
 * Отображает полноэкранную стартовую boot-анимацию приложения.
 *
 * Компонент измеряет доступную высоту экрана, рассчитывает количество строк
 * boot-лога, которое можно комфортно разместить, и передаёт это значение во
 * внутренний контент анимации. После завершения сценария вызывает [onFinished],
 * чтобы родительский экран мог перейти к основному интерфейсу приложения.
 *
 * @since 0.2
 */
@Composable fun KsenaxLaunchAnimation(
    onFinished: () -> Unit,
    modifier:   Modifier = Modifier,
) {
    // Для получения РЕАЛЬНОГО параметра выделенного экрана (maxHeight)
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Чтобы переводить в px из dp/sp
        val density = LocalDensity.current
        val targetBootLineCount = with(density) {
            val availableHeightPx = maxHeight.toPx() - BOOT_VERTICAL_PADDING.toPx() * 2f
            val lineHeightPx      = BOOT_LINE_HEIGHT.toPx()

            (availableHeightPx / lineHeightPx)
                .toInt()
                .coerceIn(MIN_BOOT_LINES, MAX_BOOT_LINES)
        }

        KsenaxLaunchAnimationContent(
            targetBootLineCount = targetBootLineCount,    // сколько строк надо подготовить
            onFinished          = onFinished,             // что вызвать после конца анимации
            modifier            = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Управляет внутренним сценарием стартовой boot-анимации.
 *
 * Компонент хранит состояние отображаемых boot-строк, текущей печатаемой строки,
 * glitch-импульсов, затемнения, ускорения по нажатию и финального collapse-эффекта.
 * Сам сценарий проигрывается через coroutine-эффекты, а визуальная часть ниже
 * собирается из нескольких слоёв boot-лога и canvas-оверлеев.
 *
 * @since 0.2
 */
@Composable private fun KsenaxLaunchAnimationContent(
    targetBootLineCount: Int,
    onFinished:          () -> Unit,
    modifier:            Modifier = Modifier,
) {
    /**
     * Список строк, которые будут проигрываться в boot-логе.
     */
    val bootLines = remember(targetBootLineCount) {
        buildBootLines(targetBootLineCount)
    }

    /**
     * Список строк, которые уже полностью напечатаны и закреплены в логе
     */
    var completeLines          by remember { mutableStateOf(emptyList<BootLine>()) }

    /**
     * Строка, которая сейчас находится в процессе вывода
     */
    var currentLine            by remember { mutableStateOf<BootLine?>(null) }

    /**
     * Видимая часть текущей строки
     */
    var currentText            by remember { mutableStateOf("") }

    /**
     * Флаг о том, показывать ли статус текущей строки.
     *
     * Нужен, потому что строка состоит из двух частей:
     * ```
     * Loading Tokenizer...  [WARNING]
     * ```
     */
    var isCurrentStatusVisible by remember { mutableStateOf(false) }

    /**
     * Флаг ранней порчи boot-анимации
     */
    var isEarlyCorruption      by remember { mutableStateOf(false) }

    /**
     * Флаг короткого ambient glitch-импульса.
     *
     * Типичные характеристики:
     * - экран чуть раздвоился
     * - появились полосы
     * - потом отпустило
     */
    var isGlitchPulseActive    by remember { mutableStateOf(false) }

    /**
     * В зависимости от пульса экран будет глючить с силой, соответствующей этому статусу.
     */
    var statusPulse            by remember { mutableStateOf<BootStatus?>(null) }

    /**
     * Нужен ниже для [LaunchedEffect], номер события глюка
     */
    var statusPulseId          by remember { mutableStateOf(0) }

    /**
     * Уровень порчи от `0f` до `1f`.
     */
    var corruptionLevel        by remember { mutableStateOf(0f) }

    /**
     * Сид для псевдослучайных глитч-эффектов.
     */
    var glitchSeed             by remember { mutableStateOf(0) }

    /**
     * Флаг финального collapse-режима.
     */
    var isFinalCollapse        by remember { mutableStateOf(false) }

    /**
     * Флаг чёрного экрана.
     */
    var isBlackout             by remember { mutableStateOf(false) }

    /**
     * Флаг финального исчезновения всей boot-анимации.
     */
    var isFadingOut            by remember { mutableStateOf(false) }

    /**
     * Множитель скорости анимации.
     *
     * Если пользователь тапает или держит экран, он увеличивается:
     * - `2f`: быстрее
     * - `3f`: ещё быстрее
     * - и так далее
     */
    var speedMultiplier        by remember { mutableStateOf(1f) }

    /**
     * Флаг удержания пальца на экране.
     * @see speedMultiplier
     */
    var isSpeedPressing        by remember { mutableStateOf(false) }

    /**
     * Состояние текущего пробега пиксельных треугольников.
     */
    var triangleRun            by remember { mutableStateOf<BootTriangleRun?>(null) }

    /**
     * Анимированная прозрачность всего overlay.
     */
    val overlayAlpha by animateFloatAsState(
        targetValue   = if (isFadingOut) 0f else 1f,
        animationSpec = tween(durationMillis = BOOT_FADE_MILLIS, easing = LinearEasing),
        label         = "bootOverlayAlpha",
    )

    /**
     * Отдельная прозрачность именно boot-лога.
     */
    val logAlpha by animateFloatAsState(
        targetValue   = if (isFadingOut) 0f else 1f,
        animationSpec = tween(durationMillis = 120, easing = LinearEasing),
        label         = "bootLogAlpha",
    )

    /**
     *
     */
    val blackoutAlpha by animateFloatAsState(
        targetValue   = if (isBlackout) 1f else 0f,
        animationSpec = tween(durationMillis = BOOT_BLACKOUT_MILLIS, easing = LinearEasing),
        label         = "bootBlackoutAlpha",
    )

    fun triggerStatusPulse(status: BootStatus) {
        if (status == BootStatus.UpToDate) {
            return // это нормальный статус
        }

        statusPulse    = status
        statusPulseId += 1
        glitchSeed    += 1
    }

    // Держит glitch-импульс активным короткое время
    LaunchedEffect(statusPulseId) {
        if (statusPulseId == 0) {
            return@LaunchedEffect
        }

        val status = statusPulse ?: return@LaunchedEffect
        bootDelay(status.glitchBurstMillis) { speedMultiplier }
        statusPulse = null
    }

    // эффект отвечает за ускорение boot-анимации при удержании пальца.
    LaunchedEffect(isSpeedPressing) {
        if (!isSpeedPressing) {
            return@LaunchedEffect
        }

        delay(BOOT_HOLD_ACCELERATION_DELAY_MILLIS.milliseconds)

        while (isSpeedPressing && speedMultiplier < BOOT_MAX_SPEED_MULTIPLIER) {
            speedMultiplier = (speedMultiplier + BOOT_HOLD_SPEED_STEP)
                .coerceAtMost(BOOT_MAX_SPEED_MULTIPLIER)
            delay(BOOT_HOLD_ACCELERATION_TICK_MILLIS.milliseconds)
        }
    }

    LaunchedEffect(bootLines) {
        val renderedLines = mutableListOf<BootLine>()
        val glitchStartIndex = (targetBootLineCount - BOOT_GLITCH_TAIL_LINES).coerceAtLeast(0)
        val failureStartIndex = bootLines.indexOfFirst { line -> line.isFailureTail }
            .takeIf { index -> index >= 0 }
            ?: bootLines.size
        var bootLineIndex = 0

        bootDelay(BOOT_STARTUP_PAUSE_MILLIS) { speedMultiplier }

        while (bootLineIndex < bootLines.size) {
            val renderedCount = renderedLines.size
            val isGlitchZone = renderedCount >= glitchStartIndex
            val isFailureTailZone = bootLineIndex >= failureStartIndex
            val remainingBeforeGlitch = (glitchStartIndex - renderedCount).coerceAtLeast(0)
            val remainingBeforeFailureTail = (failureStartIndex - bootLineIndex).coerceAtLeast(0)
            val remainingLines = bootLines.size - bootLineIndex
            val canBurst = remainingLines > 1 && (
                    remainingBeforeGlitch > 1 || isGlitchZone
                    ) && !isFailureTailZone
                    && remainingBeforeFailureTail > 1
            val shouldBurst = canBurst && Random.nextFloat() < 0.86f

            if (shouldBurst) {
                val burstSize = Random.nextInt(2, 7)
                    .coerceAtMost(remainingBeforeGlitch.takeIf { it > 0 } ?: remainingLines)
                    .coerceAtMost(remainingBeforeFailureTail)
                    .coerceAtMost(remainingLines)

                if (burstSize <= 0) {
                    continue
                }

                currentLine = null
                currentText = ""
                isCurrentStatusVisible = false

                repeat(burstSize) {
                    renderedLines += bootLines[bootLineIndex]
                    bootLineIndex += 1
                }

                completeLines = renderedLines.toList()
                corruptionLevel = renderedLines.corruptionLevel(targetBootLineCount)

                triggerStatusPulse(renderedLines.takeLast(burstSize).dominantStatus())
                bootDelay(BOOT_BURST_PAUSE_MILLIS) { speedMultiplier }
                continue
            }

            val bootLine = bootLines[bootLineIndex]
            currentLine = bootLine
            currentText = ""
            isCurrentStatusVisible = false
            val shouldTypeLine = bootLine.isFailureTail || Random.nextFloat() < 0.16f

            if (shouldTypeLine) {
                val chunkSize = if (bootLine.isFailureTail) {
                    BOOT_FAILURE_TYPE_CHUNK_SIZE
                } else {
                    BOOT_TYPE_CHUNK_SIZE
                }

                var visibleChars = 0
                while (visibleChars < bootLine.message.length) {
                    visibleChars = min(
                        visibleChars + chunkSize,
                        bootLine.message.length,
                    )
                    currentText = bootLine.message.take(visibleChars)
                    bootDelay(BOOT_TYPE_DELAY_MILLIS) { speedMultiplier }
                }
            } else {
                currentText = bootLine.message
            }

            isCurrentStatusVisible = true
            triggerStatusPulse(bootLine.status)
            bootDelay(BOOT_STATUS_BANG_MILLIS) { speedMultiplier }
            renderedLines += bootLine
            completeLines = renderedLines.toList()
            corruptionLevel = renderedLines.corruptionLevel(targetBootLineCount)
            bootLineIndex += 1

            currentLine = null
            currentText = ""
            isCurrentStatusVisible = false

            if (bootLine.message == BOOT_FINAL_FAILURE_MESSAGE) {
                bootDelay(260L) { speedMultiplier }
                isFinalCollapse = true

                repeat(7) { index ->
                    statusPulse = if (index % 2 == 0) BootStatus.Error else BootStatus.Severe
                    statusPulseId += 1
                    glitchSeed += 1
                    bootDelay(Random.nextLong(18L, 39L)) { speedMultiplier }
                }

                statusPulse = null
                isBlackout = true
                bootDelay(BOOT_BLACKOUT_MILLIS.toLong()) { speedMultiplier }
                delay(BOOT_FINAL_SILENCE_MILLIS.milliseconds)
                isFadingOut = true
                delay(BOOT_FADE_MILLIS.toLong().milliseconds)
                onFinished()
                return@LaunchedEffect
            }

            bootDelay(if (bootLine.isFailureTail) 1L else BOOT_LINE_PAUSE_MILLIS) {
                speedMultiplier
            }
        }

        bootDelay(BOOT_HOLD_AFTER_LINES_MILLIS) { speedMultiplier }
        isFadingOut = true
        delay(BOOT_FADE_MILLIS.toLong().milliseconds)
        onFinished()
    }

    LaunchedEffect(Unit) {
        bootDelay(
            Random.nextLong(
                BOOT_EARLY_CORRUPTION_DELAY_MILLIS,
                BOOT_EARLY_CORRUPTION_DELAY_MILLIS + 180L,
            ),
        ) {
            speedMultiplier
        }
        isEarlyCorruption = true

        while (!isFadingOut && !isBlackout) {
            repeat(Random.nextInt(2, 6)) {
                isGlitchPulseActive = true
                glitchSeed += 1
                bootDelay(Random.nextLong(16L, 37L)) { speedMultiplier }
                isGlitchPulseActive = false
                bootDelay(Random.nextLong(8L, 25L)) { speedMultiplier }
            }
            bootDelay(Random.nextLong(700L, 2_601L)) { speedMultiplier }
        }

        isGlitchPulseActive = false
    }

    LaunchedEffect(Unit) {
        while (!isFadingOut && !isBlackout) {
            bootDelay(
                Random.nextLong(
                    BOOT_TRIANGLE_RUN_MIN_DELAY_MILLIS,
                    BOOT_TRIANGLE_RUN_MAX_DELAY_MILLIS + 1L,
                ),
            ) { speedMultiplier }
            if (isFadingOut || isBlackout) {
                break
            }

            val stepCount = Random.nextInt(34, 49)
            val runSeed = Random.nextInt()

            for (step in 0 until stepCount) {
                if (isFadingOut || isBlackout) {
                    break
                }
                triangleRun = BootTriangleRun(
                    seed = runSeed,
                    step = step,
                )
                delay(
                    Random.nextLong(
                        BOOT_TRIANGLE_STEP_MIN_MILLIS,
                        BOOT_TRIANGLE_STEP_MAX_MILLIS,
                    ).milliseconds,
                )
            }

            triangleRun = null
        }

        triangleRun = null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer(alpha = overlayAlpha)
            .background(BOOT_BACKGROUND)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        speedMultiplier = (speedMultiplier + BOOT_TAP_SPEED_STEP)
                            .coerceAtMost(BOOT_MAX_SPEED_MULTIPLIER)
                    },
                    onLongPress = {
                        // Long press acceleration is handled by onPress so it can grow while held.
                    },
                    onPress = {
                        isSpeedPressing = true
                        try {
                            tryAwaitRelease()
                        } finally {
                            isSpeedPressing = false
                        }
                    }
                )
            }
            .clipToBounds(),
    ) {
        val activeStatusPulse = statusPulse
        val statusPulseStrength = activeStatusPulse?.effectStrength ?: 0f
        val ambientPulseStrength = if (isGlitchPulseActive) 0.22f + corruptionLevel * 0.18f else 0f
        val finalPulseStrength = if (isFinalCollapse && !isBlackout) 1f else 0f
        val pulseStrength = maxOf(statusPulseStrength, ambientPulseStrength, finalPulseStrength)
        val isCorrupted = pulseStrength > 0f
        val isRgbPulse = activeStatusPulse == BootStatus.Error || isFinalCollapse
        val isRefusalPulse = activeStatusPulse == BootStatus.Refusal
        val isSeverePulse = activeStatusPulse == BootStatus.Severe || isFinalCollapse
        val shakeStrength = pulseStrength.coerceAtLeast(if (isCorrupted) 0.10f else 0f)
        val primaryGlitchMotion = glitchMotion(glitchSeed, 701)
        val secondaryGlitchMotion = glitchMotion(glitchSeed, 733)
        val baseGlitchMotion = glitchMotion(glitchSeed, 761)
        val layerShift = 8f + pulseStrength * 12f
        val baseLogAlpha = if (activeStatusPulse == BootStatus.Warning && glitchSeed % 2 == 0) {
            logAlpha * 0.78f
        } else {
            logAlpha
        }

        BootScanlines(
            modifier = Modifier.fillMaxSize(),
        )

        if (isCorrupted && (isRgbPulse || isSeverePulse)) {
            BootLog(
                lines = completeLines,
                currentLine = currentLine,
                currentText = currentText,
                isCurrentStatusVisible = isCurrentStatusVisible,
                alpha = logAlpha * (0.12f + pulseStrength * 0.30f),
                glitchTint = BOOT_CYAN,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = BOOT_HORIZONTAL_PADDING,
                        vertical = BOOT_VERTICAL_PADDING,
                    )
                    .graphicsLayer(
                        translationX = glitchOffset(glitchSeed, 1) * shakeStrength +
                            primaryGlitchMotion.x * layerShift,
                        translationY = glitchOffset(glitchSeed, 2) * 0.35f * shakeStrength +
                            primaryGlitchMotion.y * layerShift * 0.72f,
                    ),
            )
        }

        if (isCorrupted && (isRgbPulse || isRefusalPulse || isSeverePulse)) {
            BootLog(
                lines = completeLines,
                currentLine = currentLine,
                currentText = currentText,
                isCurrentStatusVisible = isCurrentStatusVisible,
                alpha = logAlpha * (if (isRefusalPulse) 0.44f else 0.12f + pulseStrength * 0.24f),
                glitchTint = BOOT_MAGENTA,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = BOOT_HORIZONTAL_PADDING,
                        vertical = BOOT_VERTICAL_PADDING,
                    )
                    .graphicsLayer(
                        translationX = glitchOffset(glitchSeed, 3) * shakeStrength -
                            secondaryGlitchMotion.x * layerShift,
                        translationY = glitchOffset(glitchSeed, 4) * 0.28f * shakeStrength -
                            secondaryGlitchMotion.y * layerShift * 0.68f,
                    ),
            )
        }

        BootLog(
            lines = completeLines,
            currentLine = currentLine,
            currentText = currentText,
            isCurrentStatusVisible = isCurrentStatusVisible,
            alpha = baseLogAlpha,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = BOOT_HORIZONTAL_PADDING,
                    vertical = BOOT_VERTICAL_PADDING,
                )
                .graphicsLayer(
                    translationX = if (isCorrupted) {
                        glitchOffset(glitchSeed, 5) * shakeStrength +
                            baseGlitchMotion.x * layerShift * 0.24f
                    } else {
                        0f
                    },
                    translationY = if (isCorrupted) {
                        glitchOffset(glitchSeed, 6) * 0.18f * shakeStrength +
                            baseGlitchMotion.y * layerShift * 0.18f
                    } else {
                        0f
                    },
                    scaleX = if (isCorrupted && glitchSeed % 2 == 0) 1f + 0.006f * pulseStrength else 1f,
                ),
        )

        if (isCorrupted) {
            BootGlitchOverlay(
                seed = glitchSeed,
                alpha = logAlpha * pulseStrength,
                intensity = pulseStrength,
                modifier = Modifier.fillMaxSize(),
            )
        }

        triangleRun?.let { run ->
            BootRisingPixelTriangles(
                run = run,
                alpha = logAlpha,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (isFinalCollapse && !isBlackout) {
            BootCollapseCursorOverlay(
                seed = glitchSeed,
                alpha = logAlpha,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (blackoutAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = blackoutAlpha)),
            )
        }
    }
}

private suspend fun bootDelay(
    millis:          Long,
    speedMultiplier: () -> Float,
) {
    val scaledMillis = (millis / speedMultiplier()
        .coerceIn(1f, BOOT_MAX_SPEED_MULTIPLIER))
        .roundToLong()
        .coerceAtLeast(1L)
    delay(scaledMillis.milliseconds)
}
