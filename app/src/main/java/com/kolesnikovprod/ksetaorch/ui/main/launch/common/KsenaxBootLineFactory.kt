package com.kolesnikovprod.ksetaorch.ui.main.launch.common

import kotlin.random.Random

internal fun buildBootLines(): List<BootLine> {
    val mixedLines = RuntimeBootLines
        .shuffled(Random.Default)
        .toMutableList()
    val ksenaxQueue = KsenaxBootLines
        .shuffled(Random.Default)
        .toMutableList()

    while (ksenaxQueue.isNotEmpty()) {
        val insertIndex = Random.nextInt(0, mixedLines.size + 1)
        mixedLines.add(insertIndex, ksenaxQueue.removeAt(0))
    }

    return mixedLines.map { message ->
        BootLine(
            message = message,
            status = BootStatusPool.random(Random.Default),
        )
    }
}

internal fun buildBootLines(targetLineCount: Int): List<BootLine> {
    val tailSize = FailureTailBootLines.size
    val prefixSize = (targetLineCount - tailSize).coerceAtLeast(0)

    return buildBootLines()
        .take(prefixSize) + FailureTailBootLines
        .takeLast(targetLineCount)
        .map { line -> line.copy(isFailureTail = true) }
}

internal fun List<BootLine>.corruptionLevel(
    targetBootLineCount: Int,
): Float {
    if (targetBootLineCount <= 0) {
        return 0f
    }

    return (size / targetBootLineCount.toFloat())
        .coerceIn(0f, 1f)
}

internal fun List<BootLine>.dominantStatus(): BootStatus {
    return maxByOrNull { line -> line.status.effectStrength }
        ?.status
        ?: BootStatus.UpToDate
}