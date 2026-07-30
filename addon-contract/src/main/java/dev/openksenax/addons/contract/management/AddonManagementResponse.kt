package dev.openksenax.addons.contract.management

import android.app.PendingIntent
import android.os.Parcel
import android.os.Parcelable

/**
 * Типизированный Parcelable-конверт ответа асинхронного management API.
 *
 * Передаётся из автономного addon APK обратно в OpenKsenax через
 * [IAddonManagementCallback]. Поля [requestId] и [operation] позволяют
 * host-приложению сопоставить callback с исходным запросом и проверить,
 * что ответ относится к ожидаемому типу операции.
 *
 * Payload зависит от [operation]:
 *
 * - [AddonManagementOperation.RUNTIME_STATUS] должен содержать
 *   [runtimeStatus];
 * - [AddonManagementOperation.SET_ENABLED] при успешном выполнении должен
 *   содержать [commandResult] и итоговый [runtimeStatus];
 * - [AddonManagementOperation.UI_ENTRY_POINT] должен содержать
 *   [uiEntryPoint].
 *
 * При ошибке ответ может содержать [failureCode] и дополнительное
 * человекочитаемое описание в [failureMessage].
 *
 * Класс не гарантирует семантическую корректность комбинации полей.
 * Проверка обязательного payload, взаимоисключающих значений и соответствия
 * операции выполняется принимающей стороной.
 *
 * Порядок записи полей в [Parcel] является частью IPC-контракта и должен
 * полностью совпадать с порядком чтения в [CREATOR].
 *
 * @property requestId идентификатор исходного асинхронного запроса.
 * @property operation операция, к которой относится ответ.
 * @property commandResult результат команды изменения runtime-состояния.
 * @property runtimeStatus актуальный снимок runtime-состояния аддона.
 * @property uiEntryPoint безопасная точка входа в пользовательский интерфейс
 * аддона.
 * @property failureCode стабильная машинно-читаемая категория ошибки.
 * @property failureMessage необязательное диагностическое описание ошибки.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
data class AddonManagementResponse(
    val requestId:      String,
    val operation:      AddonManagementOperation,
    val commandResult:  AddonCommandResult?         = null,
    val runtimeStatus:  AddonRuntimeStatus?         = null,
    val uiEntryPoint:   PendingIntent?              = null,
    val failureCode:    AddonManagementFailureCode? = null,
    val failureMessage: String?                     = null,
) : Parcelable {

    /**
     * Записывает management-ответ в Parcel для передачи через Binder.
     *
     * Nullable Parcelable-поля предваряются маркером присутствия.
     *
     * @since 0.3
     */
    override fun writeToParcel(
        parcel: Parcel,
        flags:  Int,
    ) {
        parcel.writeString(requestId)
        parcel.writeString(operation.name)
        parcel.writeOptional(commandResult, flags)
        parcel.writeOptional(runtimeStatus, flags)
        parcel.writeOptional(uiEntryPoint, flags)
        parcel.writeString(failureCode?.name)
        parcel.writeString(failureMessage)
    }

    /**
     * Сообщает, что сам объект не объявляет специальных файловых
     * дескрипторов.
     *
     * @since 0.3
     */
    override fun describeContents(): Int = 0

    companion object {

        /**
         * Восстанавливает [AddonManagementResponse] из Parcel.
         *
         * @since 0.3
         */
        @JvmField
        val CREATOR: Parcelable.Creator<AddonManagementResponse> =
            object : Parcelable.Creator<AddonManagementResponse> {

                override fun createFromParcel(
                    parcel: Parcel,
                ): AddonManagementResponse {
                    return AddonManagementResponse(
                        requestId = parcel.readString().orEmpty(),
                        operation = AddonManagementOperation.fromWireValue(
                            parcel.readString(),
                        ),
                        commandResult = parcel.readOptional(
                            AddonCommandResult.CREATOR,
                        ),
                        runtimeStatus = parcel.readOptional(
                            AddonRuntimeStatus.CREATOR,
                        ),
                        uiEntryPoint = parcel.readOptional(
                            PendingIntent.CREATOR,
                        ),
                        failureCode = parcel.readString()?.let(
                            AddonManagementFailureCode::fromWireValue,
                        ),
                        failureMessage = parcel.readString(),
                    )
                }

                override fun newArray(size: Int): Array<AddonManagementResponse?> = arrayOfNulls(size)
            }
    }
}

/**
 * Тип management-операции, результат которой передаётся через асинхронный
 * callback.
 *
 * Имена значений сериализуются в Binder Parcel и являются частью стабильного
 * wire-протокола. Опубликованные значения нельзя переименовывать без изменения
 * версии management API.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
enum class AddonManagementOperation {

    /**
     * Получение актуального runtime-состояния аддона.
     *
     * Успешный ответ должен содержать
     * [AddonManagementResponse.runtimeStatus].
     *
     * @since 0.3
     */
    RUNTIME_STATUS,

    /**
     * Включение или выключение runtime аддона.
     *
     * Успешный ответ должен содержать
     * [AddonManagementResponse.commandResult] и итоговый
     * [AddonManagementResponse.runtimeStatus].
     *
     * @since 0.3
     */
    SET_ENABLED,

    /**
     * Получение [PendingIntent] для открытия UI аддона.
     *
     * Успешный ответ должен содержать
     * [AddonManagementResponse.uiEntryPoint].
     *
     * @since 0.3
     */
    UI_ENTRY_POINT,

    /**
     * Полученное wire-значение операции неизвестно текущей версии API.
     *
     * @since 0.3
     */
    UNKNOWN,
    ;

    internal companion object {
        fun fromWireValue(value: String?): AddonManagementOperation {
            return entries.firstOrNull { operation -> operation.name == value } ?: UNKNOWN
        }
    }
}

/**
 * Стабильная машинно-читаемая категория ошибки management-запроса.
 *
 * Код описывает общий класс отказа и предназначен для программной обработки
 * на стороне host. Дополнительные детали могут передаваться через
 * [AddonManagementResponse.failureMessage].
 *
 * Имена значений сериализуются в Binder Parcel и являются частью стабильного
 * wire-протокола.
 *
 * @since 0.3
 * @author Stephan Kolesnikov
 */
enum class AddonManagementFailureCode {

    /**
     * Аддон понял запрос, но сознательно отказался выполнять его.
     *
     * @since 0.3
     */
    REJECTED,

    /**
     * Операция или необходимый внутренний ресурс временно недоступны.
     *
     * @since 0.3
     */
    UNAVAILABLE,

    /**
     * Запрошенная операция не поддерживается текущей реализацией аддона
     * или версией API.
     *
     * @since 0.3
     */
    UNSUPPORTED,

    /**
     * Во время обработки запроса произошла внутренняя ошибка аддона.
     *
     * @since 0.3
     */
    INTERNAL_ERROR,

    /**
     * Полученное wire-значение ошибки неизвестно текущей версии API.
     *
     * @since 0.3
     */
    UNKNOWN,
    ;

    internal companion object {
        fun fromWireValue(value: String): AddonManagementFailureCode {
            return entries.firstOrNull { code -> code.name == value } ?: UNKNOWN
        }
    }
}

private fun Parcel.writeOptional(
    value: Parcelable?,
    flags: Int,
) {
    writeInt(if (value == null) 0 else 1)
    value?.writeToParcel(this, flags)
}

private fun <T : Parcelable> Parcel.readOptional(
    creator: Parcelable.Creator<T>,
): T? {
    return if (readInt() == 0) null else creator.createFromParcel(this)
}
