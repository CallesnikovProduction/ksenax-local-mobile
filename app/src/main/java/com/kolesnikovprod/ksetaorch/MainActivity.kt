package com.kolesnikovprod.ksetaorch

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kolesnikovprod.ksetaorch.conv.KsenaxLocalConversation
import com.kolesnikovprod.ksetaorch.download.KsenaxDownloadState
import com.kolesnikovprod.ksetaorch.download.KsenaxModelInstallService
import com.kolesnikovprod.ksetaorch.tools.KSENAX_TORCH_ROUTER_SYSTEM_PROMPT
import com.kolesnikovprod.ksetaorch.tools.KsenaxFlashlightTool
import com.kolesnikovprod.ksetaorch.tools.KsenaxToolResult
import com.kolesnikovprod.ksetaorch.tools.KsenaxTorchRoute
import com.kolesnikovprod.ksetaorch.tools.KsenaxTorchRouteParser
import com.kolesnikovprod.ksetaorch.tools.KsenaxTorchToolExecutor
import com.kolesnikovprod.ksetaorch.ui.generalscreen.AgentDialogueMessage
import com.kolesnikovprod.ksetaorch.ui.generalscreen.AgentDialogueSender
import com.kolesnikovprod.ksetaorch.ui.generalscreen.AgentDialogueWindow
import com.kolesnikovprod.ksetaorch.ui.generalscreen.AgentPromptComposer
import com.kolesnikovprod.ksetaorch.ui.generalscreen.KsenaxHeader
import com.kolesnikovprod.ksetaorch.ui.generalscreen.KsenaxNebulaBackground
import com.kolesnikovprod.ksetaorch.ui.generalscreen.LocalAgentBottomBar
import com.kolesnikovprod.ksetaorch.ui.generalscreen.ModelIndexingOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import com.kolesnikovprod.ksetaorch.download.NO_DOWNLOAD_ID as NO_DOWNLOAD_ID

/**
 * Стандартная Android-точка входа, которая запускает Compose-экран приложения.
 *
 * Activity не содержит низкоуровневую установку модели напрямую: установка вынесена
 * в [KsenaxModelInstallService], а визуальные элементы экрана собраны из composable
 * компонентов `ui.generalscreen`.
 *
 * @since 0.1
 * @author Stepan Kolesnikov
 */
class MainActivity : ComponentActivity() {

    /**
     * Инициализирует edge-to-edge режим и подключает главный Compose-экран.
     *
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                KsenaxHomeScreen()
            }
        }
    }
}

/**
 * Главный экран Ksenax.
 *
 * Экран связывает UI-состояние, установку модели, локальную LiteRT-LM conversation
 * и выполнение allowlisted Android-tools. Низкоуровневые download-операции делегируются
 * [KsenaxModelInstallService], а конкретное выполнение фонарика - tool-слою.
 *
 * @since 0.1
 * @author Stepan Kolesnikov
 */
@Composable
private fun KsenaxHomeScreen() {
    var promptText by remember { mutableStateOf("") }
    var dialogueInputText by remember { mutableStateOf("") }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val installService = remember(context) {
        KsenaxModelInstallService(context.applicationContext)
    }
    val savedDownloadId = remember(installService) {
        installService.getSavedDownloadId()
    }

    var isDownloadRemembered by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var isDownloadInterrupted by remember { mutableStateOf(false) }
    var isDownloadCancelled by remember { mutableStateOf(false) }
    var isModelIndexing by remember { mutableStateOf(false) }
    var isRoutingPrompt by remember { mutableStateOf(false) }
    var isConversationOpen by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var currentDownloadId by rememberSaveable {
        mutableLongStateOf(savedDownloadId)
    }
    var nextDialogueMessageId by remember {
        mutableLongStateOf(0L)
    }
    var dialogueMessages by remember {
        mutableStateOf(emptyList<AgentDialogueMessage>())
    }
    var localConversation by remember {
        mutableStateOf<KsenaxLocalConversation?>(null)
    }
    var pendingTorchRoute by remember {
        mutableStateOf<KsenaxTorchRoute?>(null)
    }

    val flashlightTool = remember(context) {
        KsenaxFlashlightTool(context.applicationContext)
    }
    val torchExecutor = remember(flashlightTool) {
        KsenaxTorchToolExecutor(flashlightTool)
    }

    /**
     * Добавляет сообщение в локальную историю диалога агента.
     *
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    fun appendDialogueMessage(
        sender: AgentDialogueSender,
        text: String,
    ) {
        dialogueMessages = dialogueMessages + AgentDialogueMessage(
            id = nextDialogueMessageId,
            sender = sender,
            text = text,
        )
        nextDialogueMessageId += 1
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        val route = pendingTorchRoute
        pendingTorchRoute = null

        if (!isGranted) {
            appendDialogueMessage(
                sender = AgentDialogueSender.AGENT,
                text = "Не могу выполнить: без разрешения камеры фонарик недоступен.",
            )
            return@rememberLauncherForActivityResult
        }

        if (route != null) {
            appendDialogueMessage(
                sender = AgentDialogueSender.AGENT,
                text = "Разрешение получила. Выполняю...",
            )

            when (val result = torchExecutor.execute(route)) {
                is KsenaxToolResult.Success -> appendDialogueMessage(
                    sender = AgentDialogueSender.AGENT,
                    text = "Сделала!",
                )
                is KsenaxToolResult.Failure -> appendDialogueMessage(
                    sender = AgentDialogueSender.AGENT,
                    text = "Не получилось: ${result.message}",
                )
                is KsenaxToolResult.PermissionRequired -> appendDialogueMessage(
                    sender = AgentDialogueSender.AGENT,
                    text = "Разрешение камеры все еще недоступно.",
                )
            }
        }
    }

    /**
     * Выполняет уже разобранный tool route через allowlisted executor.
     *
     * Если Android требует разрешение камеры, функция сохраняет route и запускает
     * системный permission-request, после которого выполнение продолжается в launcher.
     *
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    @RequiresPermission(Manifest.permission.CAMERA)
    fun executeTorchRoute(route: KsenaxTorchRoute) {
        when (val result = torchExecutor.execute(route)) {
            is KsenaxToolResult.PermissionRequired -> {
                pendingTorchRoute = route
                appendDialogueMessage(
                    sender = AgentDialogueSender.AGENT,
                    text = "Нужно разрешение камеры. Открою системный запрос доступа.",
                )
                cameraPermissionLauncher.launch(result.permission)
            }
            is KsenaxToolResult.Success -> appendDialogueMessage(
                sender = AgentDialogueSender.AGENT,
                text = "Сделала!",
            )
            is KsenaxToolResult.Failure -> {
                val prefix = if (route is KsenaxTorchRoute.Refused) {
                    "Не могу"
                } else {
                    "Не получилось"
                }
                appendDialogueMessage(
                    sender = AgentDialogueSender.AGENT,
                    text = "$prefix: ${result.message}",
                )
            }
        }
    }

    /**
     * Отправляет пользовательскую команду в локальную модель и исполняет выбранный route.
     *
     * Функция лениво инициализирует [KsenaxLocalConversation], передает модели команду,
     * парсит JSON tool-call через [KsenaxTorchRouteParser] и затем выполняет разрешенный
     * Android-tool через [executeTorchRoute].
     *
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    fun sendPromptToLocalAgent(userCommand: String) {
        if (userCommand.isBlank() || isRoutingPrompt) {
            return
        }

        isConversationOpen = true
        appendDialogueMessage(
            sender = AgentDialogueSender.USER,
            text = userCommand,
        )
        isRoutingPrompt = true

        coroutineScope.launch {
            try {
                val shouldInitializeModel = localConversation == null

                if (shouldInitializeModel) {
                    appendDialogueMessage(
                        sender = AgentDialogueSender.AGENT,
                        text = "Загружаюсь...",
                    )
                }

                val conversation = localConversation ?: run {
                    val nextConversation = KsenaxLocalConversation(
                        modelPath = installService.getGemma4E2BModelPath(),
                        cacheDirPath = installService.getGemma4E2BCacheDirPath(),
                        systemInstruction = KSENAX_TORCH_ROUTER_SYSTEM_PROMPT,
                    )

                    nextConversation.initialize()
                    localConversation = nextConversation
                    nextConversation
                }

                appendDialogueMessage(
                    sender = AgentDialogueSender.AGENT,
                    text = "Выполняю...",
                )

                val modelResponse = conversation.ask(
                    "Команда пользователя:\n$userCommand"
                )
                val route = KsenaxTorchRouteParser.parse(modelResponse)

                executeTorchRoute(route)
            } catch (exception: Exception) {
                appendDialogueMessage(
                    sender = AgentDialogueSender.AGENT,
                    text = exception.message
                        ?: "Не удалось выполнить команду через локальную модель.",
                )
            } finally {
                isRoutingPrompt = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            localConversation?.close()
        }
    }

    LaunchedEffect(currentDownloadId, installService) {
        if (currentDownloadId == NO_DOWNLOAD_ID) {
            if (!isDownloadRemembered) {
                val hasCandidateModelFile = installService.hasGemma4E2BCandidateFile()

                if (!hasCandidateModelFile) {
                    return@LaunchedEffect
                }

                isModelIndexing = true

                val isValidModelFile = try {
                    installService.hasValidGemma4E2BFile()
                } finally {
                    isModelIndexing = false
                }

                isDownloadRemembered = isValidModelFile
                isDownloadInterrupted = !isValidModelFile

                if (!isValidModelFile) {
                    installService.deleteGemma4E2BFile()
                }
            }

            return@LaunchedEffect
        }

        val downloadId = currentDownloadId

        isDownloading = true
        isDownloadInterrupted = false
        isDownloadCancelled = false

        while (true) {
            val status = installService.queryDownloadSnapshot(downloadId)

            if (status == null) {
                isDownloading = false
                isDownloadInterrupted = true
                currentDownloadId = NO_DOWNLOAD_ID
                installService.clearGemma4E2BDownloadArtifacts()
                break
            }

            downloadProgress = status.progress

            when (status.state) {
                KsenaxDownloadState.SUCCESSFUL -> {
                    isModelIndexing = true

                    val isValidModelFile = try {
                        installService.hasValidGemma4E2BFile()
                    } finally {
                        isModelIndexing = false
                    }

                    downloadProgress = if (isValidModelFile) 1f else 0f
                    isDownloading = false
                    isDownloadInterrupted = !isValidModelFile
                    isDownloadRemembered = isValidModelFile
                    currentDownloadId = NO_DOWNLOAD_ID
                    installService.clearSavedDownloadId()

                    if (!isValidModelFile) {
                        installService.deleteGemma4E2BFile()
                    }

                    break
                }

                KsenaxDownloadState.FAILED -> {
                    isDownloading = false
                    isDownloadInterrupted = true
                    currentDownloadId = NO_DOWNLOAD_ID
                    installService.clearGemma4E2BDownloadArtifacts()
                    break
                }

                KsenaxDownloadState.PENDING,
                KsenaxDownloadState.RUNNING,
                KsenaxDownloadState.PAUSED,
                KsenaxDownloadState.UNKNOWN -> Unit
            }

            delay(500L.milliseconds)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        KsenaxNebulaBackground(modifier = Modifier.matchParentSize())

        if (isConversationOpen) {
            AgentDialogueWindow(
                messages = dialogueMessages,
                inputText = dialogueInputText,
                onInputTextChange = { nextText ->
                    if (nextText.length <= 500) {
                        dialogueInputText = nextText
                    }
                },
                onSend = {
                    val userCommand = dialogueInputText.trim()

                    if (userCommand.isNotBlank()) {
                        dialogueInputText = ""
                        sendPromptToLocalAgent(userCommand)
                    }
                },
                isBusy = isRoutingPrompt,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                bottomBar = {
                    LocalAgentBottomBar(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(start = 45.dp, end = 45.dp, top = 10.dp, bottom = 8.dp),
                    )
                },
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    KsenaxHeader(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(start = 28.dp, end = 28.dp, top = 56.dp),
                    )

                    AgentPromptComposer(
                        text = promptText,
                        onTextChange = { nextText ->
                            if (nextText.length <= 500) {
                                promptText = nextText
                            }
                        },
                        onSend = {
                            val userCommand = promptText.trim()

                            if (userCommand.isNotBlank()) {
                                promptText = ""
                                sendPromptToLocalAgent(userCommand)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(start = 22.dp, end = 22.dp, bottom = 12.dp),
                        isDownloaded = isDownloadRemembered,
                        isSending = isRoutingPrompt,
                        isDownloading = isDownloading,
                        isDownloadInterrupted = isDownloadInterrupted,
                        isDownloadCancelled = isDownloadCancelled,
                        downloadProgress = downloadProgress,
                        onDownloadModel = {
                            if (isDownloading) {
                                installService.cancelGemma4E2BDownload(currentDownloadId)
                                isDownloading = false
                                isDownloadInterrupted = false
                                isDownloadCancelled = true
                                currentDownloadId = NO_DOWNLOAD_ID
                            } else {
                                val downloadId = installService.startGemma4E2BDownload()
                                currentDownloadId = downloadId
                                downloadProgress = 0f
                                isDownloading = true
                                isDownloadInterrupted = false
                                isDownloadCancelled = false
                            }
                        }
                    )
                }
            }
        }

        if (isModelIndexing) {
            ModelIndexingOverlay(modifier = Modifier.matchParentSize())
        }
    }
}
