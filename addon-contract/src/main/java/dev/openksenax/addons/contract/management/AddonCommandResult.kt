package dev.openksenax.addons.contract.management

import android.os.Parcel
import android.os.Parcelable

/**
 * Parcelable-результат выполнения одной management-команды,
 * отправленной автономному addon APK.
 *
 * Объект передаётся между процессами аддона и OpenKsenax через Binder/AIDL.
 * Он содержит устойчивый машинно-читаемый статус и необязательное
 * человекочитаемое пояснение.
 *
 * Результат относится к конкретной выполненной команде и не представляет
 * полное текущее состояние runtime аддона.
 *
 * Порядок записи полей в [Parcel] является частью IPC-контракта и должен
 * совпадать с порядком чтения в [CREATOR].
 *
 * @property status итог выполнения management-команды.
 * @property message необязательное диагностическое описание результата.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
data class AddonCommandResult(
    val status:  AddonCommandStatus,
    val message: String? = null,
) : Parcelable {

    /**
     * Метод сериализации объекта (записывает результат команды в Parcel для передачи через Binder).
     *
     * @since 0.3
     */
    override fun writeToParcel(
        parcel: Parcel,
        flags: Int,
    ) {
        parcel.writeString(status.name)
        parcel.writeString(message)
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
         * Восстанавливает [AddonCommandResult] из Parcel.
         *
         * @since 0.3
         */
        @JvmField val CREATOR: Parcelable.Creator<AddonCommandResult> =
            object : Parcelable.Creator<AddonCommandResult> {

                /**
                 * Обратная операция к [AddonCommandResult.writeToParcel]
                 */
                override fun createFromParcel(parcel: Parcel): AddonCommandResult {
                    return AddonCommandResult(
                        status  = AddonCommandStatus.fromWireValue(parcel.readString()),
                        message = parcel.readString(),
                    )
                }

                override fun newArray(size: Int): Array<AddonCommandResult?> = arrayOfNulls(size)
            }
    }
}

/**
 * Определяет итог выполнения одной management-команды.
 *
 * Имена значений сериализуются в Binder Parcel и являются частью
 * стабильного management wire-протокола. После публикации их нельзя
 * переименовывать без изменения версии management API.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
enum class AddonCommandStatus {

    /**
     * Команда успешно выполнена.
     *
     * @since 0.3
     */
    SUCCESS,

    /**
     * Аддон уже находился в состоянии, запрошенном host-приложением.
     *
     * Результат является успешным идемпотентным no-op: дополнительное
     * изменение runtime-состояния не потребовалось.
     *
     * @since 0.3
     */
    ALREADY_IN_REQUESTED_STATE,

    /**
     * Аддон распознал команду, но сознательно отказался выполнять её
     * из-за политики, состояния или невыполненной предпосылки.
     *
     * @since 0.3
     */
    REJECTED,

    /**
     * Команда была принята к исполнению, но завершилась внутренней
     * или платформенной ошибкой.
     *
     * @since 0.3
     */
    FAILED,

    /**
     * Полученное wire-значение неизвестно текущей версии management API.
     *
     * Позволяет безопасно обработать ответ более новой версии аддона
     * или повреждённое значение без исключения при десериализации.
     *
     * @since 0.3
     */
    UNKNOWN,
    ;

    internal companion object {

        /**
         * Метод преобразует строковое значение обратно в enum.
         */
        fun fromWireValue(value: String?): AddonCommandStatus {
            return entries.firstOrNull { status -> status.name == value } ?: UNKNOWN
        }
    }
}
