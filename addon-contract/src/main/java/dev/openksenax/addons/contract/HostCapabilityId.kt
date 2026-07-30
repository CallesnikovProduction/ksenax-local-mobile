package dev.openksenax.addons.contract

/**
 * Типизированный идентификатор возможности, которую OpenKsenax может
 * предоставить установленному аддону.
 *
 * Capability описывает логическую функцию host-приложения, а не конкретную
 * реализацию, модель или runtime. Например, аддон может запросить возможность
 * `model.text-generation`, не зная, какой model provider будет использоваться
 * для её выполнения.
 *
 * Значение должно состоять из строчных латинских букв и цифр. Сегменты могут
 * разделяться символами `.`, `_` или `-`. Разделители не могут находиться
 * в начале, в конце или следовать друг за другом.
 *
 * Корректный идентификатор не означает, что возможность известна протоколу,
 * доступна на текущем устройстве или разрешена конкретному аддону. Эти проверки
 * выполняются host-приложением отдельно.
 *
 * Примеры допустимых значений:
 * - `model.text-generation`;
 * - `model.embeddings`;
 * - `speech.recognition`.
 *
 * @property value строковое представление идентификатора возможности.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
@JvmInline
value class HostCapabilityId(val value: String) {

    init {
        require(value.isNotBlank()) { "HostCapabilityId MUST NOT BE blank" }
        require(value == value.lowercase()) { "HostCapabilityId MUST BE lowercase: $value" }
        require(CAPABILITY_PATTERN.matches(value)) { "Invalid HostCapabilityId: $value" }
    }

    /**
     * Возвращает строковое представление идентификатора возможности.
     *
     * @since 0.3
     */
    override fun toString(): String = value

    private companion object {

        /**
         * Шаблон идентификатора: `<Домен>.<Конкретная-возможность>`
         *
         * В качестве разделителя может быть один из символов: `+`, `-`, `_`.
         **/
        val CAPABILITY_PATTERN = Regex("^[a-z0-9]+([._-][a-z0-9]+)*$")
    }
}