package dev.openksenax.addons.contract

/**
 * Содержит канонические идентификаторы стандартных возможностей,
 * определённых публичным addon-протоколом OpenKsenax.
 *
 * Объект формирует общий словарь между host-приложением и аддонами.
 * Использование готовых идентификаторов предотвращает появление
 * несовместимых строковых вариантов одной и той же возможности.
 *
 * Наличие capability в этом объекте означает, что её идентификатор известен
 * текущей версии протокола. Это не гарантирует, что возможность доступна
 * на конкретном устройстве, обеспечена активным provider-ом или разрешена
 * конкретному аддону.
 *
 * Строковые значения опубликованных capabilities являются частью стабильного
 * публичного контракта и не должны переименовываться. Новые возможности следует
 * добавлять под новыми идентификаторами.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
object HostCapabilities {

    /**
     * Генерация свободного текстового ответа.
     *
     * @since 0.3
     */
    val TextGeneration: HostCapabilityId = HostCapabilityId("model.text-generation")

    /**
     * Генерация результата в соответствии с заданной структурой или схемой.
     *
     * @since 0.3
     */
    val StructuredGeneration: HostCapabilityId = HostCapabilityId("model.structured-generation")

    /**
     * Выбор объявленных инструментов и формирование аргументов их вызова.
     *
     * @since 0.3
     */
    val FunctionCalling: HostCapabilityId = HostCapabilityId("model.function-calling")

    /**
     * Построение векторных представлений данных для семантического поиска,
     * локальной памяти и retrieval-сценариев.
     *
     * @since 0.3
     */
    val Embeddings: HostCapabilityId = HostCapabilityId("model.embeddings")

    /**
     * Преобразование входной речи или аудиозаписи в текст.
     *
     * @since 0.3
     */
    val SpeechRecognition: HostCapabilityId = HostCapabilityId("speech.recognition")

    /**
     * Синтез речи на основе входного текста.
     *
     * @since 0.3
     */
    val TextToSpeech: HostCapabilityId = HostCapabilityId("speech.text-to-speech")
}