package com.kolesnikovprod.ksetaorch.storage.chat.data.local

import androidx.room.TypeConverter
import com.kolesnikovprod.ksetaorch.storage.chat.domain.model.KsenaxMessageRole
import com.kolesnikovprod.ksetaorch.storage.chat.domain.model.KsenaxStoredChatMode

/**
 * Преобразует enum-значения chat domain в строки SQLite и обратно.
 *
 * Room вызывает эти методы при чтении и записи полей, тип которых SQLite не
 * умеет хранить напрямую.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
class KsenaxChatTypeConverters {

    /**
     * Сохраняет режим чата по имени enum-константы.
     *
     * @since 0.2
     */
    @TypeConverter
    fun chatModeToString(mode: KsenaxStoredChatMode): String = mode.name

    /**
     * Восстанавливает режим чата из сохранённого имени enum-константы.
     *
     * @since 0.2
     */
    @TypeConverter
    fun stringToChatMode(value: String): KsenaxStoredChatMode {
        return enumValueOf(value)
    }

    /**
     * Сохраняет роль сообщения по имени enum-константы.
     *
     * @since 0.2
     */
    @TypeConverter
    fun messageRoleToString(role: KsenaxMessageRole): String = role.name

    /**
     * Восстанавливает роль сообщения из сохранённого имени enum-константы.
     *
     * @since 0.2
     */
    @TypeConverter
    fun stringToMessageRole(value: String): KsenaxMessageRole {
        return enumValueOf(value)
    }
}
