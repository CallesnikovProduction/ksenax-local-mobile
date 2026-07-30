package dev.openksenax.addons.contract.management;

/**
 * Parcelable-снимок текущего runtime-состояния автономного аддона.
 *
 * Используется в асинхронном management-ответе на запрос состояния.
 * Поля, допустимые состояния и правила сериализации определяются
 * одноимённым Parcelable-классом.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
parcelable AddonRuntimeStatus;
