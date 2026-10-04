package com.kolesnikovprod.ksetaorch.communication.work.runtime

/**
 * Диагностика без текста UP, заметок и аргументов.
 * @author Stephan Kolesnikov
 * @since 0.4
 */
data class KsenaxWorkDiagnostic(val stage: String, val action: String?, val latencyMs: Long?, val category: String?)
