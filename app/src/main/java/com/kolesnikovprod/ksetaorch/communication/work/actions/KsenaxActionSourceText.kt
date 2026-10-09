package com.kolesnikovprod.ksetaorch.communication.work.actions

import com.kolesnikovprod.ksetaorch.communication.work.routing.FastRequestConstraints

/**
 * Текст для проверки основания действия, не для выбора инструмента.
 * Цитаты, названия заметок и обсуждение действий не дают права на их исполнение.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
object KsenaxActionSourceText {
    fun executableClauses(text: String): String = executableParts(text).joinToString(" ")

    /** Потребность относится к объекту kit-а; допустимый числовой модификатор проверяет kit.
     * @author Stephan Kolesnikov
     * @since 0.4
     */
    fun hasExplicitNeed(text: String, objectNames: Regex, allowsQualifier: (String) -> Boolean = { false }): Boolean = need.findAll(text).any { match ->
        val tail = text.substring(match.range.last + 1).trimStart()
        val subject = determiner.replaceFirst(tail, "")
        val objectStart = objectNames.find(subject)?.range?.first ?: return@any false
        val qualifier = subject.substring(0, objectStart).trim()
        qualifier.isEmpty() || allowsQualifier(qualifier)
    }

    /**
     * Части без цитат; назначение каждой части проверяет её kit.
     * @author Stephan Kolesnikov
     * @since 0.4
     */
    fun unquotedParts(text: String): List<String> = quoted.replace(text, " ")
        .split(clauses)
        .map(String::trim)
        .filter(String::isNotEmpty)

    /**
     * Сохраняет границы аппаратных команд, исключая обсуждение и отрицание.
     * @author Stephan Kolesnikov
     * @since 0.4
     */
    fun executableParts(text: String): List<String> = unquotedParts(text)
        .filterNot { discussion.containsMatchIn(it) || FastRequestConstraints.isUnsafeStatement(it) }

    private val quoted = Regex("\"[^\"]*\"|«[^»]*»|“[^”]*”|'[^']*'")
    private val need = Regex("(?<![\\p{L}\\p{N}])(?:мне|нам)\\s+(?:нужен|нужна|нужно|нужны)(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
    private val determiner = Regex("^(?:этот|эта|это|эти|мой|моя|мо[её]|мои)\\s+", RegexOption.IGNORE_CASE)
    // Союз в теме «про будильник и календарь» не начинает новую команду.
    private val clauses = Regex(
        "(?<![\\p{N}])\\.|\\.(?![\\p{N}])|[!?;]|(?:,\\s*|\\s+(?:и|а|затем|потом)(?:\\s+ещ[её])?\\s+)(?=(?:пожалуйста\\s+)?(?:включ|выключ|заж|погас|вруб|выруб|постав|завед|разбуд|буди|созда|добав|запиш|занес|напиш|сдела|сохран|отключ|удал|очист|убер|снес|проанализируй|дополни|допиши))",
        RegexOption.IGNORE_CASE,
    )
    private val discussion = Regex(
        "(?<![\\p{L}\\p{N}])(?:замет\\p{L}*|мысл\\p{L}*|запис\\p{L}*|текст\\p{L}*|obsidian|конспект\\p{L}*|анализ\\p{L}*|проанализ\\p{L}*|расскаж\\p{L}*|объясн\\p{L}*|инструкц\\p{L}*|почему|как|можно\\s+ли|стоит\\s+ли)(?![\\p{L}\\p{N}])",
        RegexOption.IGNORE_CASE,
    )
}
