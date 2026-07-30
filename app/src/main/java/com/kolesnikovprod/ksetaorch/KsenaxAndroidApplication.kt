package com.kolesnikovprod.ksetaorch

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.kolesnikovprod.ksetaorch.clean.KsenaxRuntimeCacheCleanupManager
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxLiteRtAudioBackend
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelRuntimeConfig
import com.kolesnikovprod.ksetaorch.communication.model.KsenaxModelSession
import com.kolesnikovprod.ksetaorch.communication.model.LiteRtKsenaxModelSession
import com.kolesnikovprod.ksetaorch.communication.orchestration.basechat.KsenaxBasicChatCoordinator
import com.kolesnikovprod.ksetaorch.communication.orchestration.basechat.KsenaxTemporaricChatCoordinator
import com.kolesnikovprod.ksetaorch.download.domain.usecases.KsenaxGemma4E2BInstallUseCase
import com.kolesnikovprod.ksetaorch.download.domain.usecases.KsenaxFunctionGemmaInstallUseCase
import com.kolesnikovprod.ksetaorch.storage.chat.data.RoomKsenaxChatRepository
import com.kolesnikovprod.ksetaorch.storage.chat.data.local.KsenaxChatDatabase
import com.kolesnikovprod.ksetaorch.storage.chat.domain.KsenaxChatRepository
import com.kolesnikovprod.ksetaorch.ui.controllers.KsenaxAgentRuntimeController
import com.kolesnikovprod.ksetaorch.ui.controllers.KsenaxAgenticWorkController
import com.kolesnikovprod.ksetaorch.ui.controllers.modelvalidation.KsenaxCompositeModelIntegrityVerifier
import com.kolesnikovprod.ksetaorch.ui.controllers.modelvalidation.KsenaxGemmaIntegrityController
import com.kolesnikovprod.ksetaorch.ui.controllers.modelvalidation.KsenaxModelIntegrityVerifier
import com.kolesnikovprod.ksetaorch.ui.controllers.modelvalidation.KsenaxModelVerificationSessionRegistry

/**
 * Process-level application object.
 *
 * It prepares cleanup for runtime artifacts before any Activity creates a
 * LiteRT-LM engine. This is the right level for crash/session markers because
 * cache garbage belongs to the process lifecycle, not to a single composable.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
class KsenaxAndroidApplication : Application() {

    /**
     * Foreground-session кэш успешной проверки локальных моделей.
     *
     * @since 0.3
     */
    val modelVerificationSessionRegistry =
        KsenaxModelVerificationSessionRegistry()

    private val modelVerificationLifecycleObserver =
        object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                modelVerificationSessionRegistry.onAppForegrounded()
            }

            override fun onStop(owner: LifecycleOwner) {
                modelVerificationSessionRegistry.onAppBackgrounded()
            }
        }

    /**
     * Занимается очисткой runtime-cache, crash/session markers и подобным мусором.
     * @since 0.2
     */
    lateinit var runtimeCacheCleanupManager: KsenaxRuntimeCacheCleanupManager
        private set

    override fun onCreate() {
        super.onCreate() // Подтягиваем базовый Application
        runtimeCacheCleanupManager = KsenaxRuntimeCacheCleanupManager(this)

        runtimeCacheCleanupManager.prepareProcessSession()
        runtimeCacheCleanupManager.installUncaughtExceptionCleanupHook()
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            modelVerificationLifecycleObserver,
        )
    }

    /**
     * Создаёт Room database.
     *
     * Для базы нужен context, но нельзя хранить Activity context,
     * потому что Activity может умереть.
     *
     * Application context живёт весь процесс.
     *
     * @since 0.2
     */
    val chatDatabase: KsenaxChatDatabase by lazy {
        KsenaxChatDatabase.create(this)
    }

    /**
     * Репозиторий поверх базы.
     * @since 0.2
     */
    val chatRepository: KsenaxChatRepository by lazy {
        RoomKsenaxChatRepository(chatDatabase)
    }

    /*
    * MODEL-INSTALL/-FIND USE-CASES
    * */
    val gemmaInstallUseCase: KsenaxGemma4E2BInstallUseCase by lazy {
        KsenaxGemma4E2BInstallUseCase(this)
    }

    val gemmaIntegrityController: KsenaxGemmaIntegrityController by lazy {
        KsenaxGemmaIntegrityController(
            installUseCase = gemmaInstallUseCase,
            sessionRegistry = modelVerificationSessionRegistry,
        )
    }

    val functionGemmaInstallUseCase: KsenaxFunctionGemmaInstallUseCase by lazy {
        KsenaxFunctionGemmaInstallUseCase(this)
    }

    val functionGemmaIntegrityController: KsenaxGemmaIntegrityController by lazy {
        KsenaxGemmaIntegrityController(
            installUseCase = functionGemmaInstallUseCase,
            sessionRegistry = modelVerificationSessionRegistry,
        )
    }

    val agenticModelsIntegrityController: KsenaxModelIntegrityVerifier by lazy {
        KsenaxCompositeModelIntegrityVerifier(
            listOf(
                gemmaIntegrityController,
                functionGemmaIntegrityController,
            )
        )
    }

    /*
    * SESSION-OVER-LITERT-LM API
    * */

    val gemmaModelSession: KsenaxModelSession by lazy {
        LiteRtKsenaxModelSession(
            modelPath    = gemmaInstallUseCase.getGemma4E2BModelPath(),
            cacheDirPath = gemmaInstallUseCase.getGemma4E2BCacheDirPath(),
            audioBackend = KsenaxLiteRtAudioBackend.CPU,
        )
    }

    val functionGemmaModelSession: KsenaxModelSession by lazy {
        LiteRtKsenaxModelSession(
            modelPath    = functionGemmaInstallUseCase.getInstalledPath(),
            cacheDirPath = functionGemmaInstallUseCase.getRuntimeCachePath(),
            audioBackend = null,
            runtimeConfig = KsenaxModelRuntimeConfig(
                maxContextTokens = 1_024,
            ),
        )
    }

    /*
    * MODEL COORDINATORS FOR DIFFERENT CHAT-MODES
    * */
    val basicChatCoordinator: KsenaxBasicChatCoordinator by lazy {
        KsenaxBasicChatCoordinator(gemmaModelSession)
    }

    val agenticWorkRuntimeController: KsenaxAgentRuntimeController by lazy {
        KsenaxAgenticWorkController(
            context = this,
            plannerSession = gemmaModelSession,
            actionSession = functionGemmaModelSession,
        )
    }

    val temporaricChatCoordinator: KsenaxTemporaricChatCoordinator by lazy {
        KsenaxTemporaricChatCoordinator(gemmaModelSession)
    }

    val functionGemmaBasicChatCoordinator: KsenaxBasicChatCoordinator by lazy {
        KsenaxBasicChatCoordinator(functionGemmaModelSession)
    }

    val functionGemmaTemporaricChatCoordinator: KsenaxTemporaricChatCoordinator by lazy {
        KsenaxTemporaricChatCoordinator(functionGemmaModelSession)
    }

}
