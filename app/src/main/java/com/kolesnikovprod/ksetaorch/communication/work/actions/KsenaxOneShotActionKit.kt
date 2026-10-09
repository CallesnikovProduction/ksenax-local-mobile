package com.kolesnikovprod.ksetaorch.communication.work.actions

import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolExecutor
import com.kolesnikovprod.ksetaorch.communication.tools.contracts.KsenaxToolCall
import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotToolProtocol
import com.kolesnikovprod.ksetaorch.communication.work.planning.KsenaxWorkPlanStep
import java.time.ZonedDateTime

/**
 * Один набор маленьких FG-actions.
 *
 * Kit не знает про UI, Room и конкретный LiteRT runtime. Он описывает actions
 * для G4 planner-а, умеет собрать FG prompt и имеет Android executor для
 * распарсенного function-call.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
interface KsenaxOneShotActionKit {

    val id: String

    val namespace: String
        get() = "system"

    val actionSpecs: List<KsenaxWorkActionSpec>

    /**
     * Исторические подсказки Keywords. Семантический runtime их не использует;
     * доступность быстрых функций задаёт [supportsFastPath].
     */
    val directRoute: KsenaxDirectActionRoute?
        get() = null

    val exposePlannerInputToFunctionGemma: Boolean
        get() = true

    /**
     * Объявления kit доступны семантическому быстрому маршрутизатору FG.
     * @author Stephan Kolesnikov
     * @since 0.4
     */
    val supportsFastPath: Boolean
        get() = false

    val protocol: KsenaxOneShotToolProtocol

    val executor: KsenaxToolExecutor

    fun matchesDirectRoute(userMessage: String): Boolean =
        directRoute?.keywords?.matches(userMessage) == true

    fun buildDirectActionDraft(userMessage: String): KsenaxActionInputDraft? = null

    /**
     * Закрепляет явные параметры UP после выбора функции моделью.
     * @author Stephan Kolesnikov
     * @since 0.4
     */
    fun buildFastActionDraft(userMessage: String, actionName: String, now: ZonedDateTime): KsenaxActionInputDraft? = null

    /**
     * Проверяет основание действия в исходном UP, в том числе для шагов G4.
     * Обязательна для каждого kit: не выбирает функцию и не заменяет проверку аргументов.
     * @author Stephan Kolesnikov
     * @since 0.4
     */
    fun validateSourceRequest(userMessage: String)

    /**
     * Проверяет разрешение именно выбранного действия, а не только его kit-а.
     * Одинаково применяется к быстрому вызову и шагу плана до исполнения.
     * @author Stephan Kolesnikov
     * @since 0.4
     */
    fun validateSourceCall(userMessage: String, actionName: String) = validateSourceRequest(userMessage)

    /**
     * Проверяет обязательные исходные значения до загрузки G4, не выбирая функцию.
     * Сгенерированный текст не требуется; kit без таких значений проверяет основание.
     * @author Stephan Kolesnikov
     * @since 0.4
     */
    fun validatePlanningSource(userMessage: String, requestTime: ZonedDateTime) = validateSourceRequest(userMessage)

    /**
     * Сверяет весь набор planned-входов kit-а с исходными ограничениями до исполнения.
     * Не заменяет модельный выбор и не проверяет сгенерированный текст на совпадение.
     * @author Stephan Kolesnikov
     * @since 0.4
     */
    fun validatePlannedInputs(userMessage: String, requestTime: ZonedDateTime, calls: List<KsenaxToolCall>) = Unit

    /**
     * Проверяет предметные ограничения перед policy и executor.
     * @author Stephan Kolesnikov
     * @since 0.4
     */
    fun validateExecutableCall(call: KsenaxToolCall, now: ZonedDateTime) = Unit

    fun supportsAction(actionName: String): Boolean =
        actionSpecs.any { spec -> spec.name == actionName }

    fun buildFallbackPlannerInputJson(
        userMessage: String,
        step: KsenaxWorkPlanStep,
    ): String? = null

    /**
     * Последний seam перед Android executor-ом.
     *
     * По умолчанию executor получает аргументы FG. Планируемые действия вроде
     * заметок могут заменить их данными G4, чтобы FunctionGemma не переносила
     * большие тексты.
     */
    fun resolveExecutableCall(
        userMessage: String,
        step: KsenaxWorkPlanStep,
        compiledCall: KsenaxToolCall,
    ): KsenaxToolCall = compiledCall
}
