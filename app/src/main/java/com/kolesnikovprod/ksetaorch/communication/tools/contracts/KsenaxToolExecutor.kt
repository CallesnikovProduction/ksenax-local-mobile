package com.kolesnikovprod.ksetaorch.communication.tools.contracts

/**
 * Исполнитель конкретного tool-а.
 *
 * Executor получает уже разобранный [KsenaxToolCall]. Он не парсит ответ
 * модели, не собирает prompt и не решает общую policy.
 * **К моменту вызова слой оркестрации уже должен понять, какой tool выбран и можно ли его
 * запускать.**
 *
 * Реализация executor-а может обращаться к Android API, файловой системе или
 * другой платформенной зависимости, если именно этот tool отвечает за такое
 * действие.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
interface KsenaxToolExecutor {

    /**
     * Выполняет один tool call и возвращает результат.
     *
     * Ожидаемые доменные отказы лучше возвращать как
     * [KsenaxToolResult.Failure]: недоступное разрешение, неверный аргумент
     * или невозможность выполнить действие.
     *
     * @param call вызов tool-а с именем, id и JSON-аргументами.
     * @return результат выполнения tool-а.
     *
     * @since 0.2
     */
    suspend fun execute(
        call: KsenaxToolCall,
    ): KsenaxToolResult
}
