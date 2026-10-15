package com.kolesnikovprod.ksetaorch.download.domain

import kotlinx.coroutines.CancellationException

/** Владелец задачи сменился; это не отмена coroutine observer-а. @since 0.4 */
internal class InstallSupersededException : CancellationException("Install task superseded")
