package com.kolesnikovprod.ksetaorch.ui.controllers

import android.content.Context
import android.net.Uri
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelSession
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.alarm.AlarmOneShotExecutor
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.alarm.AlarmOneShotToolModule
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.calendar.CalendarEventOneShotExecutor
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.calendar.CalendarEventOneShotToolModule
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.calendar.CalendarEventToolExecutor
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.flashlight.TorchExecutor
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.flashlight.TorchToolModule
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.notes.ObsidianNoteOneShotExecutor
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.notes.ObsidianNoteOneShotToolModule
import com.kolesnikovprod.ksetaorch.communication.tools.builtin.notes.ObsidianWriterToolExecutor
import com.kolesnikovprod.ksetaorch.communication.work.runtime.KsenaxAgenticWorkRuntime
import com.kolesnikovprod.ksetaorch.communication.work.turn.KsenaxAgentTurnRuntime
import com.kolesnikovprod.ksetaorch.storage.KsenaxTextFileResolver
import com.kolesnikovprod.ksetaorch.storage.dto.KsenaxTextFileWriteResult
import com.kolesnikovprod.ksetaorch.storage.resolve.text.DocumentsFolderTextFileManager
import com.kolesnikovprod.ksetaorch.storage.resolve.text.UserSelectedTextWorkspaceStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Composition root нового agentic work pipeline-а.
 *
 * Здесь сходятся две модели:
 * - G4 planner session;
 * - FG action compiler session.
 *
 * Модельные сессии остаются в `communication.model`, а рабочая оркестрация
 * собирается в `communication.work`.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
class KsenaxAgenticWorkController(
    context: Context,
    private val plannerSession: KsenaxModelSession,
    private val actionSession: KsenaxModelSession,
) : KsenaxAgentRuntimeController {

    private val appContext = context.applicationContext

    override suspend fun initializeWorkspace(
        workspaceTreeUri: String?,
        workspaceDisplayPath: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            createInitializedStorage(
                workspaceTreeUri = workspaceTreeUri,
                workspaceDisplayPath = workspaceDisplayPath,
            )
            Unit
        }
    }

    override suspend fun createCoordinator(
        workspaceTreeUri: String?,
        workspaceDisplayPath: String,
    ): Result<KsenaxAgentTurnRuntime> = withContext(Dispatchers.IO) {
        runCatching {
            val storage = createInitializedStorage(
                workspaceTreeUri = workspaceTreeUri,
                workspaceDisplayPath = workspaceDisplayPath,
            )
            val noteWriter = ObsidianWriterToolExecutor(storage)

            KsenaxAgenticWorkRuntime(
                plannerSession = plannerSession,
                actionSession = actionSession,
                actionKits = listOf(
                    TorchToolModule(
                        executor = TorchExecutor(appContext),
                    ),
                    AlarmOneShotToolModule(
                        executor = AlarmOneShotExecutor(appContext),
                    ),
                    CalendarEventOneShotToolModule(
                        executor = CalendarEventOneShotExecutor(
                            CalendarEventToolExecutor(appContext),
                        ),
                    ),
                    ObsidianNoteOneShotToolModule(
                        executor = ObsidianNoteOneShotExecutor(noteWriter),
                    ),
                ),
            )
        }
    }

    private fun createInitializedStorage(
        workspaceTreeUri: String?,
        workspaceDisplayPath: String,
    ): KsenaxTextFileResolver {
        if (workspaceTreeUri.isNullOrBlank()) {
            val storage = DocumentsFolderTextFileManager(appContext)
            when (val markerResult = storage.initializeWorkspaceZone()) {
                is KsenaxTextFileWriteResult.Failure -> error(markerResult.message)
                is KsenaxTextFileWriteResult.Success -> Unit
            }
            return storage
        }

        require(workspaceDisplayPath.isNotBlank()) {
            "Не удалось определить имя рабочей директории."
        }

        val storage = UserSelectedTextWorkspaceStorage(
            context = appContext,
            workspaceTreeUri = Uri.parse(workspaceTreeUri),
            workspaceDisplayName = workspaceDisplayPath,
        )
        when (val markerResult = storage.initializeWorkspaceZone()) {
            is KsenaxTextFileWriteResult.Failure -> error(markerResult.message)
            is KsenaxTextFileWriteResult.Success -> Unit
        }
        return storage
    }
}
