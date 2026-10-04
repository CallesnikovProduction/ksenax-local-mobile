package com.kolesnikovprod.ksetaorch.communication.work.routing

/**
 * Консервативная защита от потери явной генерации и нескольких команд.
 * Не выбирает инструмент: отправляет весь запрос в планирование.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
internal object FastRequestConstraints {
    fun requiresPlanning(text: String): Boolean {
        val normalized = text.lowercase().replace('ё', 'е')
        return generation.containsMatchIn(normalized) || multipleActions.findAll(normalized).count() > 1 ||
            Regex("(?<![\\p{L}\\p{N}])(если|когда|затем)(?![\\p{L}\\p{N}])").containsMatchIn(normalized) ||
            Regex("^(как|почему|зачем)\\s").containsMatchIn(normalized)
    }

    fun isUnsafeStatement(text: String): Boolean =
        Regex("""(?<![\p{L}\p{N}])(не\s+(включ\p{L}*|выключ\p{L}*|став\p{L}*|постав\p{L}*|удал\p{L}*|очист\p{L}*|отключ\p{L}*|убер\p{L}*|созда\p{L}*|добав\p{L}*|разбуд\p{L}*|буди)|надо\s+быть|нужно\s+быть)(?![\p{L}\p{N}])""")
            .containsMatchIn(text.lowercase()) || text.contains("что-нибудь", ignoreCase = true)

    // Не используем \b: его Unicode-семантика различается между Android и JVM.
    private val generation = Regex("""(?<![\p{L}\p{N}])(замет\p{L}*|obsidian|конспект\p{L}*|анализ\p{L}*|проанализ\p{L}*|сформулир\p{L}*|состав\p{L}*|список|напиши|сочини|объясни|расскажи|переведи|сравни|посоветуй)(?![\p{L}\p{N}])""")
    private val multipleActions = Regex("""(?<![\p{L}\p{N}])(включ\p{L}*|выключ\p{L}*|зажги|погаси|постав\p{L}*|завед\p{L}*|разбуд\p{L}*|созда\p{L}*|добав\p{L}*|запиш\p{L}*|удал\p{L}*)(?![\p{L}\p{N}])""")
}
