package com.kolesnikovprod.ksetaorch.communication.work.oneshot

/**
 * Быстрый входной фильтр для FG one-shot action kit-а.
 *
 * В новом G4->FG pipeline keywords не заменяют planning. Они нужны для
 * быстрых локальных shortcuts, диагностики и выбора релевантного маленького
 * набора actions, если верхний уровень уже знает намерение.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
fun interface KsenaxOneShotKeywords {
    fun matches(userMessage: String): Boolean
}
