package dev.openksenax.addons.contract

/**
 * Стабильный типизированный идентификатор аддона в экосистеме OpenKsenax.
 *
 * Идентификатор используется для адресации аддона в manifest, discovery,
 * registry, management-командах и runtime-состояниях. Он описывает логическую
 * идентичность продукта и не является Android package name или application ID.
 *
 * Значение должно состоять из строчных латинских букв и цифр. Отдельные
 * сегменты могут разделяться символами `.`, `_` или `-`. Разделители
 * не могут находиться в начале, в конце или следовать друг за другом.
 *
 * Идентификатор должен оставаться неизменным между версиями аддона.
 * Изменение значения приведёт к тому, что host воспримет новую версию
 * как отдельный продукт.
 *
 * Пример допустимого значения:
 * `dev.openksenax.addon.no-radar`:
 * ```kotlin
 * AddonId("dev.openksenax.addon.no-radar")
 * ```
 *
 * @property value строковое представление идентификатора аддона.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
@JvmInline
value class AddonId(val value: String) {

    init {
        require(value.isNotBlank()) { "AddonId MUST NOT BE blank" }
        require(value == value.lowercase()) { "AddonId MUST BE lowercase: $value" }
        require(ADDON_ID_PATTERN.matches(value)) { "Invalid AddonId: $value" }
    }

    /**
     * Возвращает строковое представление идентификатора.
     *
     * @since 0.3
     */
    override fun toString(): String = value

    private companion object {
        /**
         * Шаблон идентификатора: последовательность сегментов:
         *
         * Сегмент + Разделитель + Сегмент + Разделитель + Сегмент + ...
         *
         * В качестве разделителя может быть один из символов: `+`, `-`, `_`.
         *
         * Примеры удачных:
         * ```
         * no-radar
         * openksenax.no-radar
         * dev.openksenax.addon.no-radar
         * addon_42
         * dev1.addon2
         * ```
         *
         * Примеры неудачных:
         * ```
         * .no-radar           // stars with divider
         * no-radar.           // ends with divider
         * no..radar           // double dividers
         * no radar            // space
         * NoRadar             // uppercased letters
         * dev/openksenax      // slash
         * dev:openksenax      // double-dot sign
         * ```
         */
        val ADDON_ID_PATTERN = Regex("^[a-z0-9]+([._-][a-z0-9]+)*$")
    }
}