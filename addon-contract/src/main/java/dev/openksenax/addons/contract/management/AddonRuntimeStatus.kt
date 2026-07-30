package dev.openksenax.addons.contract.management

import android.os.Parcel
import android.os.Parcelable

/**
 * Parcelable-снимок текущего runtime-состояния автономного addon APK.
 *
 * Объект описывает только фактическое состояние исполнения уже установленного
 * аддона. Он не содержит сведений об установке APK, совместимости протокола,
 * доступности host capabilities или состоянии Binder-соединения.
 *
 * Поля [state] и [enabledByUser] намеренно разделены. Пользовательское решение
 * оставить runtime включённым не гарантирует, что runtime фактически работает:
 * например, при [AddonRuntimeState.FAILED] значение [enabledByUser] всё ещё
 * может оставаться `true`.
 *
 * [detail] используется для дополнительного человекочитаемого описания
 * текущего состояния, причины деградации или ошибки. Прикладная логика не
 * должна зависеть от текста этого поля.
 *
 * [updatedAtEpochMillis] указывает момент формирования или последнего
 * обновления снимка в миллисекундах Unix epoch и позволяет host-приложению
 * определять устаревшие ответы.
 *
 * Объект передаётся между процессами аддона и OpenKsenax через Binder/AIDL.
 * Порядок записи полей в [Parcel] является частью IPC-контракта и должен
 * совпадать с порядком чтения в [CREATOR].
 *
 * @property state фактическое состояние runtime на момент формирования снимка.
 * @property enabledByUser намерение пользователя держать runtime включённым.
 * @property detail необязательное диагностическое описание состояния.
 * @property updatedAtEpochMillis время обновления снимка в миллисекундах
 * Unix epoch.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
data class AddonRuntimeStatus(
    val state:                AddonRuntimeState,
    val enabledByUser:        Boolean,
    val detail:               String? = null,
    val updatedAtEpochMillis: Long,
) : Parcelable {

    /**
     * Записывает runtime-состояние в [Parcel] для передачи через Binder.
     *
     * @since 0.3
     */
    override fun writeToParcel(
        parcel: Parcel,
        flags:  Int,
    ) {
        parcel.writeString(state.name)
        parcel.writeInt(if (enabledByUser) 1 else 0)
        parcel.writeString(detail)
        parcel.writeLong(updatedAtEpochMillis)
    }

    /**
     * Сообщает, что Parcelable не содержит специальных файловых
     * дескрипторов или других особых объектов.
     *
     * @since 0.3
     */
    override fun describeContents(): Int = 0

    companion object {

        /**
         * Восстанавливает [AddonRuntimeStatus] из [Parcel].
         *
         * @since 0.3
         */
        @JvmField
        val CREATOR: Parcelable.Creator<AddonRuntimeStatus> =
            object : Parcelable.Creator<AddonRuntimeStatus> {

                override fun createFromParcel(
                    parcel: Parcel,
                ): AddonRuntimeStatus {
                    return AddonRuntimeStatus(
                        state = AddonRuntimeState.fromWireValue(
                            parcel.readString(),
                        ),
                        enabledByUser = parcel.readInt() != 0,
                        detail = parcel.readString(),
                        updatedAtEpochMillis = parcel.readLong(),
                    )
                }

                override fun newArray(
                    size: Int,
                ): Array<AddonRuntimeStatus?> =
                    arrayOfNulls(size)
            }
    }
}

/**
 * Фактическое состояние runtime автономного addon APK.
 *
 * Значения сериализуются в Binder Parcel через имена enum-констант и являются
 * частью стабильного management wire-протокола. После публикации их нельзя
 * переименовывать без изменения версии management API.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
enum class AddonRuntimeState {

    /**
     * Runtime полностью остановлен и не выполняет фоновую работу.
     *
     * @since 0.3
     */
    STOPPED,

    /**
     * Runtime находится в процессе запуска и ещё не готов выполнять
     * основную бизнес-логику.
     *
     * @since 0.3
     */
    STARTING,

    /**
     * Runtime успешно запущен и работает в штатном режиме.
     *
     * @since 0.3
     */
    RUNNING,

    /**
     * Runtime продолжает работать, но часть функций недоступна или работает
     * с ограничениями.
     *
     * Дополнительная причина может быть указана в
     * [AddonRuntimeStatus.detail].
     *
     * @since 0.3
     */
    DEGRADED,

    /**
     * Runtime не может продолжать штатную работу из-за внутренней
     * или платформенной ошибки.
     *
     * Это состояние не обязательно означает, что пользователь отключил аддон:
     * [AddonRuntimeStatus.enabledByUser] может оставаться равным `true`.
     *
     * @since 0.3
     */
    FAILED,

    /**
     * Полученное wire-значение состояния неизвестно текущей версии
     * management API.
     *
     * Используется для безопасной обработки ответов более новой версии
     * аддона или повреждённых данных без исключения при десериализации.
     *
     * @since 0.3
     */
    UNKNOWN,
    ;

    internal companion object {

        fun fromWireValue(
            value: String?,
        ): AddonRuntimeState {
            return entries.firstOrNull { state ->
                state.name == value
            } ?: UNKNOWN
        }
    }
}