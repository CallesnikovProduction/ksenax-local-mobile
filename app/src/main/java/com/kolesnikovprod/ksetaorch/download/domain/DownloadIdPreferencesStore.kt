package com.kolesnikovprod.ksetaorch.download.domain

import android.content.Context
import androidx.core.content.edit
import com.kolesnikovprod.ksetaorch.download.domain.data.KsenaxInstallTarget
import com.kolesnikovprod.ksetaorch.download.domain.data.NO_DOWNLOAD_ID

/**
 * Маленькое хранилище активного `downloadId` для конкретного install target.
 *
 * Use case не должен руками собирать preference-key: иначе Gemma/Vosk начнут
 * копировать одну и ту же механику и однажды разъедутся.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
internal class DownloadIdPreferencesStore(
    context:       Context,
    installTarget: KsenaxInstallTarget,
    private val legacyDownloadIdKey: String? = null,
) {

    private val appContext = context.applicationContext
    private val downloadIdKey = "${installTarget.id}_active_download_id"

    // Получение downloadId
    fun get(): Long {
        val preferences = appContext.getSharedPreferences(
            DOWNLOAD_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

        val targetDownloadId = preferences.getLong(
            downloadIdKey,
            NO_DOWNLOAD_ID,
        )

        if (targetDownloadId != NO_DOWNLOAD_ID) {
            return targetDownloadId
        }

        return legacyDownloadIdKey?.let { legacyKey ->
            preferences.getLong(
                legacyKey,
                NO_DOWNLOAD_ID,
            )
        } ?: NO_DOWNLOAD_ID
    }

    //
    fun save(downloadId: Long) {
        appContext.getSharedPreferences(
            DOWNLOAD_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).edit {
            putLong(downloadIdKey, downloadId)
            legacyDownloadIdKey?.let { legacyKey ->
                remove(legacyKey)
            }
        }
    }

    fun clear() {
        appContext.getSharedPreferences(
            DOWNLOAD_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).edit {
            remove(downloadIdKey)
            legacyDownloadIdKey?.let { legacyKey ->
                remove(legacyKey)
            }
        }
    }

    private companion object {
        const val DOWNLOAD_PREFERENCES_NAME = "ksenax_download_preferences"
    }
}
