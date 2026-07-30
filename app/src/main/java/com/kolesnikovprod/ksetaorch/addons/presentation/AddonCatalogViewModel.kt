package com.kolesnikovprod.ksetaorch.addons.presentation

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kolesnikovprod.ksetaorch.addons.banner.AddonBannerLoadResult
import com.kolesnikovprod.ksetaorch.addons.banner.AddonBannerRepository
import com.kolesnikovprod.ksetaorch.addons.coordination.AddonActionResult
import com.kolesnikovprod.ksetaorch.addons.coordination.AddonCoordinator
import com.kolesnikovprod.ksetaorch.addons.coordination.AddonUninstallRequest
import com.kolesnikovprod.ksetaorch.addons.download.AddonInstallProgress
import com.kolesnikovprod.ksetaorch.addons.registry.AddonBannerArtifact
import com.kolesnikovprod.ksetaorch.addons.registry.AddonInstallationState
import com.kolesnikovprod.ksetaorch.addons.registry.AddonRegistry
import com.kolesnikovprod.ksetaorch.addons.registry.AddonRegistryRefreshMode
import com.kolesnikovprod.ksetaorch.addons.registry.AddonRegistrySourceStatus
import com.kolesnikovprod.ksetaorch.addons.registry.AddonRegistryState
import com.kolesnikovprod.ksetaorch.addons.storage.AddonLocalStore
import com.kolesnikovprod.ksetaorch.addons.storage.InstalledAddonRecord
import dev.openksenax.addons.contract.AddonId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Владелец состояния host-каталога и пользовательских addon-команд.
 *
 * @since 0.3
 */
class AddonCatalogViewModel internal constructor(
    private val registry: AddonRegistry,
    private val coordinator: AddonCoordinator,
    private val bannerRepository: AddonBannerRepository,
    private val localStore: AddonLocalStore,
) : ViewModel() {

    private val selectedAddonId = MutableStateFlow<AddonId?>(null)
    private val actionMessage = MutableStateFlow<String?>(null)
    private val installProgress =
        MutableStateFlow<Map<AddonId, AddonInstallProgress>>(emptyMap())
    private val bannerStates =
        MutableStateFlow<Map<AddonId, AddonBannerUiState>>(emptyMap())
    private val checkingUpdateIds =
        MutableStateFlow<Set<AddonId>>(emptySet())
    private val updateMessages =
        MutableStateFlow<Map<AddonId, String>>(emptyMap())
    private val infoOverlay =
        MutableStateFlow<AddonInfoUiModel?>(null)
    private val pendingUninstallIds =
        MutableStateFlow<Set<AddonId>>(emptySet())
    private val hiddenAvailableAddonIds =
        MutableStateFlow<Set<AddonId>>(emptySet())

    private val bannerJobs = mutableMapOf<AddonId, Job>()
    private val installJobs = mutableMapOf<AddonId, Job>()
    private val updateJobs = mutableMapOf<AddonId, Job>()
    private val updateMessageExpiryJobs =
        mutableMapOf<AddonId, Job>()
    private val pendingUninstallRequests =
        mutableMapOf<AddonId, AddonUninstallRequest>()
    private val mutableEffects =
        Channel<AddonCatalogEffect>(capacity = Channel.BUFFERED)

    internal val effects = mutableEffects.receiveAsFlow()

    private val unavailableAddonVisibility = combine(
        hiddenAvailableAddonIds,
        pendingUninstallIds,
    ) { hiddenIds, pendingIds ->
        hiddenIds + pendingIds
    }

    private val registryMappingState = combine(
        registry.state,
        unavailableAddonVisibility,
    ) { registryState, hiddenIds ->
        RegistryMappingInput(
            registryState = registryState,
            hiddenAvailableAddonIds = hiddenIds,
        )
    }

    private val primaryState = combine(
        registryMappingState,
        selectedAddonId,
        actionMessage,
        infoOverlay,
    ) { registryInput, selectedId, message, overlay ->
        PrimaryMappingInput(
            registryState = registryInput.registryState,
            selectedAddonId = selectedId,
            actionMessage = message,
            infoOverlay = overlay,
            hiddenAvailableAddonIds =
                registryInput.hiddenAvailableAddonIds,
        )
    }

    private val transientState = combine(
        installProgress,
        bannerStates,
        checkingUpdateIds,
        updateMessages,
    ) { progress, banners, checkingIds, messages ->
        TransientMappingInput(
            installProgress = progress,
            bannerStates = banners,
            checkingUpdateIds = checkingIds,
            updateMessages = messages,
        )
    }

    internal val uiState: StateFlow<AddonCatalogUiState> = combine(
        primaryState,
        transientState,
    ) { primary, transient ->
        AddonUiMapper.map(
            registryState = primary.registryState,
            selectedAddonId = primary.selectedAddonId,
            actionMessage = primary.actionMessage,
            installProgress = transient.installProgress,
            bannerStates = transient.bannerStates,
            checkingUpdateIds = transient.checkingUpdateIds,
            updateMessages = transient.updateMessages,
            infoOverlay = primary.infoOverlay,
            hiddenAvailableAddonIds =
                primary.hiddenAvailableAddonIds,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AddonUiMapper.map(
            registryState = registry.state.value,
            selectedAddonId = null,
            actionMessage = null,
        ),
    )

    init {
        viewModelScope.launch {
            try {
                val state = registry.ensureInitialized()
                localStore.reconcile(state)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                actionMessage.value =
                    error.message ?: "Не удалось инициализировать registry"
            }
        }
        viewModelScope.launch {
            registry.state.collect { state ->
                syncBannerArtifacts(state)
                try {
                    localStore.reconcile(state)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    // Локальная UI-проекция не меняет registry trust verdict.
                }
                clearMissingSelection(state)
                clearMissingInfoOverlay(state)
            }
        }
    }

    /**
     * Получает свежий каталог. Кнопка не публикует глобальный verdict
     * актуальности: он принадлежит отдельной команде конкретного аддона.
     *
     * @since 0.3
     */
    internal fun refresh() {
        hiddenAvailableAddonIds.value = emptySet()
        refreshRegistry(
            mode = AddonRegistryRefreshMode.FORCE_REMOTE,
            retryUnavailableBanners = true,
        )
    }

    /**
     * Сверяет PackageManager после возврата пользователя в OKx.
     *
     * @since 0.3
     */
    internal fun onHostResumed() {
        if (pendingUninstallIds.value.isNotEmpty()) return

        refreshRegistry(
            mode = AddonRegistryRefreshMode.DEFAULT,
            retryUnavailableBanners = false,
        )
    }

    private fun refreshRegistry(
        mode: AddonRegistryRefreshMode,
        retryUnavailableBanners: Boolean,
    ) {
        viewModelScope.launch {
            actionMessage.value = null
            try {
                val state = registry.refresh(mode)
                localStore.reconcile(state)
                syncBannerArtifacts(
                    registryState = state,
                    retryUnavailable = retryUnavailableBanners,
                )
                clearMissingSelection(state)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                actionMessage.value =
                    error.message ?: "Не удалось обновить каталог аддонов"
            }
        }
    }

    private fun clearMissingSelection(state: AddonRegistryState) {
        val selected = selectedAddonId.value ?: return
        if (
            state.addons.none { addon ->
                addon.addonId == selected && addon.isInstalled
            }
        ) {
            selectedAddonId.value = null
        }
    }

    private fun syncBannerArtifacts(
        registryState: AddonRegistryState,
        retryUnavailable: Boolean = false,
    ) {
        val requests = registryState.addons.mapNotNull { addon ->
            val published = addon.catalogMetadata
                ?: return@mapNotNull null
            val artifact = published.bannerArtifact
                ?: return@mapNotNull null
            addon.addonId to BannerLoadRequest(
                packageName = published.packageName,
                isInstalled = addon.isInstalled,
                artifact = artifact,
            )
        }.toMap()

        bannerJobs.keys
            .filter { addonId ->
                val expected = requests[addonId]?.artifact
                val runningSha = bannerStates.value[addonId]?.sha256
                expected == null || runningSha != expected.sha256
            }
            .forEach { addonId ->
                bannerJobs.remove(addonId)?.cancel()
            }

        bannerStates.update { current ->
            current.filter { (addonId, state) ->
                state.sha256 == requests[addonId]?.artifact?.sha256
            }
        }

        requests.forEach { (addonId, request) ->
            val current = bannerStates.value[addonId]
            val alreadyResolved =
                current is AddonBannerUiState.Ready ||
                    (
                        current is AddonBannerUiState.Unavailable &&
                            !retryUnavailable
                        )
            if (
                current?.sha256 == request.artifact.sha256 &&
                (
                    alreadyResolved ||
                        current is AddonBannerUiState.Loading
                    )
            ) {
                return@forEach
            }
            loadBanner(addonId, request)
        }
    }

    private fun loadBanner(
        addonId: AddonId,
        request: BannerLoadRequest,
    ) {
        bannerJobs.remove(addonId)?.cancel()
        bannerStates.update { current ->
            current + (
                addonId to AddonBannerUiState.Loading(
                    request.artifact.sha256,
                )
                )
        }

        lateinit var loadingJob: Job
        loadingJob = viewModelScope.launch(
            start = CoroutineStart.LAZY,
        ) {
            try {
                val bannerState = when (
                    val result = bannerRepository.load(
                        addonId = addonId,
                        packageName = request.packageName,
                        isInstalled = request.isInstalled,
                        bannerUrl = request.artifact.url,
                        expectedSha256 = request.artifact.sha256,
                    )
                ) {
                    is AddonBannerLoadResult.Ready ->
                        AddonBannerUiState.Ready(
                            sha256 = request.artifact.sha256,
                            bitmap = result.bitmap,
                        )

                    is AddonBannerLoadResult.Failed ->
                        AddonBannerUiState.Unavailable(
                            sha256 = request.artifact.sha256,
                        )
                }

                bannerStates.update { current ->
                    if (
                        current[addonId]?.sha256 ==
                        request.artifact.sha256
                    ) {
                        current + (addonId to bannerState)
                    } else {
                        current
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } finally {
                if (bannerJobs[addonId] === loadingJob) {
                    bannerJobs.remove(addonId)
                }
            }
        }
        bannerJobs[addonId] = loadingJob
        loadingJob.start()
    }

    internal fun selectAddon(addonId: AddonId) {
        val addon = registry.find(addonId)
        if (addon?.isInstalled != true) return
        selectedAddonId.value = if (
            selectedAddonId.value == addonId
        ) {
            null
        } else {
            addonId
        }
    }

    internal fun closeManagement() {
        selectedAddonId.value = null
        infoOverlay.value = null
        actionMessage.value = null
    }

    internal fun openAddon(addonId: AddonId) {
        execute { coordinator.open(addonId) }
    }

    internal fun uninstall(addonId: AddonId) {
        if (registry.find(addonId)?.isInstalled != true) return
        if (addonId in pendingUninstallIds.value) return

        pendingUninstallIds.update { ids -> ids + addonId }
        viewModelScope.launch {
            try {
                val result = coordinator.requestUninstall(addonId)
                if (
                    result !is
                    AddonActionResult
                        .UninstallationConfirmationRequired
                ) {
                    pendingUninstallIds.update { ids ->
                        ids - addonId
                    }
                }
                handle(result)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                pendingUninstallIds.update { ids ->
                    ids - addonId
                }
                actionMessage.value =
                    error.message ?: "Удаление аддона не выполнено"
            }
        }
    }

    /**
     * Завершает uninstall-транзакцию после возврата Android system UI.
     *
     * Host-owned файлы удаляются только после независимой проверки, что
     * package действительно исчез из PackageManager.
     *
     * @since 0.3
     */
    internal fun onUninstallSystemUiResult(
        addonId: AddonId,
        succeeded: Boolean,
    ) {
        val request = pendingUninstallRequests.remove(addonId)
        if (request == null) {
            pendingUninstallIds.update { ids -> ids - addonId }
            return
        }

        if (!succeeded) {
            pendingUninstallIds.update { ids -> ids - addonId }
            actionMessage.value = null
            refreshRegistry(
                mode = AddonRegistryRefreshMode.CACHE_ONLY,
                retryUnavailableBanners = false,
            )
            return
        }

        viewModelScope.launch {
            try {
                when (
                    val result =
                        coordinator.completeUninstall(request)
                ) {
                    is AddonActionResult.AddonUninstalled -> {
                        localStore.remove(
                            addonId = result.addonId,
                            packageName = result.packageName,
                        )
                        clearAddonTransientState(result.addonId)
                        pendingUninstallIds.update { ids ->
                            ids - result.addonId
                        }
                        hiddenAvailableAddonIds.update { ids ->
                            ids + result.addonId
                        }
                        if (
                            selectedAddonId.value ==
                            result.addonId
                        ) {
                            selectedAddonId.value = null
                        }
                        if (
                            infoOverlay.value?.addonId ==
                            result.addonId
                        ) {
                            infoOverlay.value = null
                        }
                        actionMessage.value = null
                    }

                    else -> {
                        pendingUninstallIds.update { ids ->
                            ids - addonId
                        }
                        handle(result)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                pendingUninstallIds.update { ids -> ids - addonId }
                actionMessage.value =
                    error.message ?: "Удаление аддона не завершено"
            }
        }
    }

    private fun clearAddonTransientState(addonId: AddonId) {
        bannerJobs.remove(addonId)?.cancel()
        installJobs.remove(addonId)?.cancel()
        updateJobs.remove(addonId)?.cancel()
        updateMessageExpiryJobs.remove(addonId)?.cancel()
        bannerStates.update { states -> states - addonId }
        installProgress.update { progress -> progress - addonId }
        checkingUpdateIds.update { ids -> ids - addonId }
        updateMessages.update { messages -> messages - addonId }
    }

    internal fun onUninstallSystemUiLaunchFailed(
        addonId: AddonId,
        message: String?,
    ) {
        pendingUninstallRequests.remove(addonId)
        pendingUninstallIds.update { ids -> ids - addonId }
        actionMessage.value = message
            ?.takeIf(String::isNotBlank)
            ?: "Не удалось открыть системное удаление APK"
    }

    /**
     * Первый вызов начинает download, повторный во время download отменяет его.
     *
     * @since 0.3
     */
    internal fun install(addonId: AddonId) {
        installJobs[addonId]
            ?.takeIf(Job::isActive)
            ?.let { job ->
                job.cancel(
                    CancellationException("Cancelled by user"),
                )
                return
            }

        lateinit var installJob: Job
        installJob = viewModelScope.launch(
            start = CoroutineStart.LAZY,
        ) {
            actionMessage.value = null
            try {
                val result = coordinator.requestInstall(addonId) { progress ->
                    installProgress.update { current ->
                        current + (addonId to progress)
                    }
                }
                handle(result)
            } catch (cancellation: CancellationException) {
                if (installJobs[addonId] === installJob) {
                    actionMessage.value = "Загрузка аддона отменена"
                }
                throw cancellation
            } catch (error: Exception) {
                actionMessage.value =
                    error.message ?: "Установка аддона не выполнена"
            } finally {
                installProgress.update { current ->
                    current - addonId
                }
                if (installJobs[addonId] === installJob) {
                    installJobs.remove(addonId)
                }
            }
        }
        installJobs[addonId] = installJob
        installJob.start()
    }

    internal fun checkForUpdates(addonId: AddonId) {
        if (registry.find(addonId)?.isInstalled != true) return
        if (updateJobs[addonId]?.isActive == true) return

        lateinit var updateJob: Job
        updateJob = viewModelScope.launch(
            start = CoroutineStart.LAZY,
        ) {
            checkingUpdateIds.update { ids -> ids + addonId }
            updateMessageExpiryJobs.remove(addonId)?.cancel()
            updateMessages.update { messages -> messages - addonId }
            try {
                val state = registry.refresh(
                    AddonRegistryRefreshMode.FORCE_REMOTE,
                )
                localStore.reconcile(state)
                syncBannerArtifacts(
                    registryState = state,
                    retryUnavailable = true,
                )
                val message = if (
                    state.catalogStatus !=
                    AddonRegistrySourceStatus.Fresh
                ) {
                    "Не удалось получить свежий каталог"
                } else {
                    state.addons
                        .firstOrNull { addon ->
                            addon.addonId == addonId
                        }
                        ?.installation
                        .toUpdateMessage()
                }
                showTemporaryUpdateMessage(
                    addonId = addonId,
                    message =
                        message ?: "Аддон отсутствует в каталоге",
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                showTemporaryUpdateMessage(
                    addonId = addonId,
                    message = error.message
                        ?: "Проверка обновлений не выполнена",
                )
            } finally {
                checkingUpdateIds.update { ids -> ids - addonId }
                if (updateJobs[addonId] === updateJob) {
                    updateJobs.remove(addonId)
                }
            }
        }
        updateJobs[addonId] = updateJob
        updateJob.start()
    }

    internal fun showInfo(addonId: AddonId) {
        val addon = registry.find(addonId) ?: return
        val packageName = addon.installedMetadata?.packageName ?: return

        viewModelScope.launch {
            try {
                localStore.reconcile(registry.state.value)
                val record = localStore.read(packageName)
                if (record == null) {
                    actionMessage.value =
                        "Локальные метаданные аддона недоступны"
                } else {
                    infoOverlay.value = record.toInfoUiModel()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                actionMessage.value =
                    error.message ?: "Не удалось прочитать метаданные аддона"
            }
        }
    }

    internal fun dismissInfo() {
        infoOverlay.value = null
    }

    private fun clearMissingInfoOverlay(
        state: AddonRegistryState,
    ) {
        val overlay = infoOverlay.value ?: return
        if (
            state.addons.none { addon ->
                addon.addonId == overlay.addonId &&
                    addon.isInstalled
            }
        ) {
            infoOverlay.value = null
        }
    }

    private fun showTemporaryUpdateMessage(
        addonId: AddonId,
        message: String,
    ) {
        updateMessageExpiryJobs.remove(addonId)?.cancel()
        updateMessages.update { messages ->
            messages + (addonId to message)
        }

        lateinit var expiryJob: Job
        expiryJob = viewModelScope.launch(
            start = CoroutineStart.LAZY,
        ) {
            delay(UPDATE_MESSAGE_VISIBLE_MILLIS)
            updateMessages.update { messages ->
                if (messages[addonId] == message) {
                    messages - addonId
                } else {
                    messages
                }
            }
            if (updateMessageExpiryJobs[addonId] === expiryJob) {
                updateMessageExpiryJobs.remove(addonId)
            }
        }
        updateMessageExpiryJobs[addonId] = expiryJob
        expiryJob.start()
    }

    private fun execute(command: suspend () -> AddonActionResult) {
        viewModelScope.launch {
            try {
                handle(command())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                actionMessage.value =
                    error.message ?: "Команда аддона не выполнена"
            }
        }
    }

    private fun handle(result: AddonActionResult) {
        when (result) {
            AddonActionResult.InstallationUiOpened ->
                actionMessage.value =
                    "APK проверен. Завершите установку в окне Android"

            is AddonActionResult
                .UninstallationConfirmationRequired -> {
                val request = result.request
                pendingUninstallRequests[request.addonId] = request
                val accepted = mutableEffects.trySend(
                    AddonCatalogEffect.ConfirmAddonUninstall(
                        addonId = request.addonId,
                        intent = request.confirmationIntent,
                    ),
                ).isSuccess
                if (accepted) {
                    actionMessage.value =
                        "Подтвердите полное удаление в окне Android"
                } else {
                    pendingUninstallRequests.remove(request.addonId)
                    pendingUninstallIds.update { ids ->
                        ids - request.addonId
                    }
                    actionMessage.value =
                        "Не удалось открыть системное удаление APK"
                }
            }

            is AddonActionResult.AddonUninstalled ->
                actionMessage.value = null

            AddonActionResult.AddonUiOpened ->
                actionMessage.value = null

            is AddonActionResult.RuntimeStatusReceived ->
                actionMessage.value = null

            is AddonActionResult.RuntimeStateChanged ->
                actionMessage.value = null

            is AddonActionResult.UnknownSourcesPermissionRequired -> {
                val accepted = mutableEffects.trySend(
                    AddonCatalogEffect.OpenAndroidIntent(
                        result.settingsIntent,
                    ),
                ).isSuccess
                actionMessage.value = if (accepted) {
                    "Разрешите OpenKsenax устанавливать addon APK"
                } else {
                    "Не удалось открыть настройки установки APK"
                }
            }

            is AddonActionResult.Failed -> {
                actionMessage.value = buildString {
                    append(result.reason.name)
                    result.message
                        ?.takeIf(String::isNotBlank)
                        ?.let { message ->
                            append(": ")
                            append(message)
                        }
                }
            }
        }
    }

    /**
     * Фабрика process-owned dependency graph приложения.
     *
     * @since 0.3
     */
    internal class Factory(
        private val registry: AddonRegistry,
        private val coordinator: AddonCoordinator,
        private val bannerRepository: AddonBannerRepository,
        private val localStore: AddonLocalStore,
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(
                modelClass.isAssignableFrom(
                    AddonCatalogViewModel::class.java,
                ),
            ) {
                "Unsupported ViewModel: ${modelClass.name}"
            }
            return AddonCatalogViewModel(
                registry = registry,
                coordinator = coordinator,
                bannerRepository = bannerRepository,
                localStore = localStore,
            ) as T
        }
    }
}

private fun AddonInstallationState?.toUpdateMessage(): String {
    return when (this) {
        is AddonInstallationState.UpdateAvailable ->
            "Доступно обновление $availableVersionName"

        is AddonInstallationState.Installed ->
            "Установлена актуальная версия"

        is AddonInstallationState.InstalledNewerThanCatalog ->
            "Установлена версия новее каталога"

        AddonInstallationState.NotInstalled ->
            "Аддон больше не установлен"

        AddonInstallationState.Unknown,
        null,
        -> "Не удалось определить установленную версию"
    }
}

private fun InstalledAddonRecord.toInfoUiModel(): AddonInfoUiModel {
    return AddonInfoUiModel(
        addonId = AddonId(addonId),
        title = displayName,
        packageName = packageName,
        fullDescription = fullDescription
            ?.takeIf(String::isNotBlank)
            ?: shortDescription
                .takeIf(String::isNotBlank)
                .orEmpty(),
        versionLabel = versionName
            ?.let { version -> "v$version ($versionCode)" }
            ?: "versionCode $versionCode",
        installedAtEpochMillis = installedAtEpochMillis,
        lastUpdatedAtEpochMillis = lastUpdatedAtEpochMillis,
        requiredCapabilities = requiredHostCapabilities,
        repositoryUrl = repositoryUrl,
    )
}

private data class PrimaryMappingInput(
    val registryState: AddonRegistryState,
    val selectedAddonId: AddonId?,
    val actionMessage: String?,
    val infoOverlay: AddonInfoUiModel?,
    val hiddenAvailableAddonIds: Set<AddonId>,
)

private data class RegistryMappingInput(
    val registryState: AddonRegistryState,
    val hiddenAvailableAddonIds: Set<AddonId>,
)

private data class TransientMappingInput(
    val installProgress: Map<AddonId, AddonInstallProgress>,
    val bannerStates: Map<AddonId, AddonBannerUiState>,
    val checkingUpdateIds: Set<AddonId>,
    val updateMessages: Map<AddonId, String>,
)

private data class BannerLoadRequest(
    val packageName: String,
    val isInstalled: Boolean,
    val artifact: AddonBannerArtifact,
)

private const val UPDATE_MESSAGE_VISIBLE_MILLIS = 3_000L

/**
 * Одноразовый Android UI-effect coordination-контура.
 *
 * @since 0.3
 */
internal sealed interface AddonCatalogEffect {
    data class OpenAndroidIntent(val intent: Intent) : AddonCatalogEffect

    data class ConfirmAddonUninstall(
        val addonId: AddonId,
        val intent: Intent,
    ) : AddonCatalogEffect
}
