package com.kolesnikovprod.ksetaorch.download.domain.data

/**
 * Сетевая политика для одной новой задачи загрузки модели.
 *
 * Значения применяются только в момент постановки задачи в Android
 * `DownloadManager` и не изменяют уже запущенную загрузку.
 *
 * @property allowOverMeteredNetwork разрешить загрузку через сеть с лимитом трафика.
 * **По умолчанию запрещено**.
 * @property allowOverRoaming разрешить загрузку в роуминге.
 * **По умолчанию запрещено**.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
data class KsenaxDownloadPolicy(
    val allowOverMeteredNetwork: Boolean = false,
    val allowOverRoaming: Boolean = false,
)