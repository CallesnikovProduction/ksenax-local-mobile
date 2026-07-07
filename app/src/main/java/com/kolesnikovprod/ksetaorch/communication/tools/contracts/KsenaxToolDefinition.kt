package com.kolesnikovprod.ksetaorch.communication.tools.contracts

/**
 * **DTO-описание инструмента (tool), которое можно показать модели в качестве
 * агентного действия.**
 *
 * Класс не исполняет действие. Он объясняет модели:
 * - как называется tool,
 * - что он делает,
 * - какие аргументы принимает,
 * - какой риск несет
 * - нужно ли обычно просить подтверждение пользователя.
 *
 * Prompt-layer использует эти данные при сборке tool schema.
 *
 * @property name стабильное имя tool-а в `snake_case`-формате.
 * Parser и registry используют его для поиска executor-а.
 * @property description короткое описание действия для модели (титульник).
 * @property arguments JSON-schema аргументов, которые модель должна вернуть
 *           в поле `arguments` выбранного tool call-а.
 * @property riskLevel базовый уровень риска tool-а.
 * @property requiresConfirmationByDefault требует ли tool подтверждение без
 *           дополнительных policy-условий.
 * @property namespace логическая группа tool-а. Для имени `notes.create`
 *           namespace будет `notes`.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
data class KsenaxToolDefinition(
    val name:                          String,
    val description:                   String,
    val arguments:                     KsenaxToolArgumentsObject,
    val riskLevel:                     KsenaxToolRiskLevel,
    val requiresConfirmationByDefault: Boolean,
    val namespace:                     String = name.substringBefore("."),
)
