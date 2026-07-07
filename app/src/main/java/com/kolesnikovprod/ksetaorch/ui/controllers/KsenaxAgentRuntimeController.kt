package com.kolesnikovprod.ksetaorch.ui.controllers

import com.kolesnikovprod.ksetaorch.communication.work.turn.KsenaxAgentTurnRuntime

/**
 * UI-facing factory agent runtime-а для конкретного чата.
 *
 * Agentic ViewModel не должна знать, какой runtime под ней стоит:
 * - старый workspace runtime с registry/tools/notes;
 * - лёгкий FunctionGemma one-shot runtime без рабочей директории.
 *
 * Поэтому у обоих путей один маленький контракт подготовки.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
interface KsenaxAgentRuntimeController {

    /**
     * Подготавливает рабочий контекст, если runtime в нём нуждается.
     *
     * FunctionGemma one-shot runtime возвращает success без filesystem side
     * effects: короткие actions не должны падать из-за workspace storage.
     *
     * @since 0.2
     */
    suspend fun initializeWorkspace(
        workspaceTreeUri: String?,
        workspaceDisplayPath: String,
    ): Result<Unit>

    /**
     * Создаёт runtime для turn-ов текущего Agentic-чата.
     *
     * @since 0.2
     */
    suspend fun createCoordinator(
        workspaceTreeUri: String?,
        workspaceDisplayPath: String,
    ): Result<KsenaxAgentTurnRuntime>
}
