package dev.openksenax.addons.contract.management;

/**
 * Parcelable-конверт асинхронного ответа management API.
 *
 * Используется единым callback-интерфейсом для передачи результата,
 * связанного с конкретным requestId, из addon process в OpenKsenax.
 * Состав полей и правила сериализации определяются одноимённым
 * Parcelable-классом.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
parcelable AddonManagementResponse;
