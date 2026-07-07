package com.kolesnikovprod.ksetaorch.storage.chat.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kolesnikovprod.ksetaorch.storage.chat.data.local.entity.KsenaxChatEntity
import com.kolesnikovprod.ksetaorch.storage.chat.data.local.entity.KsenaxMessageEntity

/**
 * Room-база постоянной истории чатов Ksenax.
 *
 * База объединяет таблицы чатов и сообщений и предоставляет единую точку
 * доступа к [KsenaxChatDao]. Версия схемы изменяется только вместе с
 * добавлением соответствующей Room-миграции.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
@Database(
    entities = [
        KsenaxChatEntity::class,
        KsenaxMessageEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(KsenaxChatTypeConverters::class)
abstract class KsenaxChatDatabase : RoomDatabase() {

    /**
     * Возвращает сгенерированную Room реализацию SQL-интерфейса чатов.
     *
     * @since 0.2
     */
    abstract fun chatDao(): KsenaxChatDao

    /**
     * Создание экземпляра базы данных.
     *
     * @since 0.2
     * @author Stephan Kolesnikov
     */
    companion object {
        private const val DatabaseName = "ksenax_chats.db"

        /**
         * Создаёт базу в приватном хранилище приложения.
         *
         * Вызывающая сторона отвечает за переиспользование экземпляра в
         * пределах процесса приложения.
         *
         * @since 0.2
         */
        fun create(context: Context): KsenaxChatDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                KsenaxChatDatabase::class.java,
                DatabaseName,
            )
                .addMigrations(Migration1To2, Migration2To3)
                .build()
        }

        private val Migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE chat_messages
                    ADD COLUMN generation_duration_millis INTEGER
                    """.trimIndent(),
                )
            }
        }

        private val Migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE chats
                    ADD COLUMN workspace_tree_uri TEXT
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    ALTER TABLE chats
                    ADD COLUMN workspace_display_path TEXT
                    """.trimIndent(),
                )
            }
        }
    }
}
