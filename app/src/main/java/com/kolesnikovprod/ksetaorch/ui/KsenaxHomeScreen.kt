package com.kolesnikovprod.ksetaorch.ui

import android.Manifest
import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.kolesnikovprod.ksetaorch.download.NO_DOWNLOAD_ID
import com.kolesnikovprod.ksetaorch.conv.tools.KSENAX_TORCH_ROUTER_SYSTEM_PROMPT
import com.kolesnikovprod.ksetaorch.conv.tools.KsenaxFlashlightTool
import com.kolesnikovprod.ksetaorch.conv.tools.KsenaxToolResult
import com.kolesnikovprod.ksetaorch.conv.tools.KsenaxTorchRoute
import com.kolesnikovprod.ksetaorch.conv.tools.KsenaxTorchRouteParser
import com.kolesnikovprod.ksetaorch.conv.tools.KsenaxTorchToolExecutor
import com.kolesnikovprod.ksetaorch.ui.generalscreen.AgentDialogueMessage
import com.kolesnikovprod.ksetaorch.ui.generalscreen.AgentDialogueSender
import com.kolesnikovprod.ksetaorch.ui.generalscreen.AgentDialogueWindow
import com.kolesnikovprod.ksetaorch.ui.generalscreen.AgentPromptComposer
import com.kolesnikovprod.ksetaorch.ui.generalscreen.KsenaxHeader
import com.kolesnikovprod.ksetaorch.ui.generalscreen.KsenaxNebulaBackground
import com.kolesnikovprod.ksetaorch.ui.generalscreen.LocalAgentBottomBar
import com.kolesnikovprod.ksetaorch.ui.before.ModelIndexingOverlay
import com.kolesnikovprod.ksetaorch.ui.before.OverlayLoadingState
import com.kolesnikovprod.ksetaorch.ui.before.OverlayState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Главный экран Ksenax.
 *
 * Экран связывает:
 * - UI-состояние,
 * - установку модели,
 * - локальную LiteRT-LM conversation
 * - и выполнение allowlisted Android-tools.
 *
 * Низкоуровневые download-операции делегируются
 * [KsenaxModelInstallService], а конкретное выполнение фонарика - tool-слою.
 *
 * @since 0.1
 * @author Stepan Kolesnikov
 */
@Composable
fun KsenaxHomeScreen() {

    // Текст в поле ввода
    var promptText by remember { mutableStateOf("") }

    // Текст в режиме открытого диалога (уже на переключенном экране)
    var dialogueInputText by remember { mutableStateOf("") }

    /**
     * Текущий контекст Android, чтобы можно было через него:
     * - открывать файл
     * - получить Application
     * - обращаться к системным сервисам
     * - запускать Intent
     * - создавать прочие сервисы
     */
    val context = LocalContext.current

    // Создаёт корутинный scope, который будет привязан к этому Composable (HomeScreen)
    // Фактически, асинхронная работа.
    val coroutineScope = rememberCoroutineScope()

    // Держит один и тот же сервис по установке
    val installService = remember(context) {
        // Против утечек памяти -> applicationContext
        KsenaxModelInstallService(context.applicationContext)
    }

    // ID последней загрузки модели, если приложение перезапустилось, то можно
    // продолжить следить именно за этой загрузкой
    val savedDownloadId = remember(installService) {
        installService.getSavedDownloadId()
    }

    /**
     * Общая переменная с текущими состояниями приложения
     */
    var uiState by remember { mutableStateOf(HomeUiState()) }

    // ID текущей загрузки модели Gemma-4-e2b-it
    var currentDownloadId by rememberSaveable {
        mutableLongStateOf(savedDownloadId)
    }

    // Уникальный счётчик сообщений (чтобы можно было вести диалог с моделью в режиме переписки)
    var nextDialogueMessageId by remember {
        mutableLongStateOf(0L)
    }

    // История чата, которую Composable автоматически перерисует, когда список изменится.
    var dialogueMessages by remember {
        mutableStateOf(emptyList<AgentDialogueMessage>())
    }

    // Хранит ЕДИНСТВЕННЫЙ ЭКЗЕМПЛЯР общения с Gemma, помогает не инициализировать модель заново
    // при каждом запросе
    var localConversation by remember {
        mutableStateOf<KsenaxLocalConversation?>(null)
    }

    // ОТЛОЖЕННАЯ команда, ожидающая выдачи разрешения на включение фонарика.
    var pendingTorchRoute by remember {
        mutableStateOf<KsenaxTorchRoute?>(null)
    }

    /**
     * Низкоуровневый объект, умеющий работать с фонариком через Android API
     */
    val flashlightTool = remember(context) {
        KsenaxFlashlightTool(context.applicationContext)
    }

    /**
     * Следующий уровень абстракции, работающий в системе:
     * - Gemma
     * - [KsenaxTorchRoute]
     * - [KsenaxTorchToolExecutor]
     * - [KsenaxFlashlightTool]
     * - Android API (CameraManager)
     * - Физический фонарик врубается
     */
    val torchExecutor = remember(flashlightTool) {
        KsenaxTorchToolExecutor(flashlightTool)
    }

    /**
     * Создает и добавляет новое сообщение в список [dialogueMessages]
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
        nextDialogueMessageId += 1 // +1 к истории
    }

    /**
     * Объект, который умеет открывать системное Android-окно с запросом на разрешение
     * доступа к камере.
     *
     * К камере потому, что на Android
     * фонарик управляется через разрешение к камере (camera permission)
     */
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        // Прошу одно разрешение, получаю Boolean:
        // true = ok
        // false = discard
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->                        // callback-контур
        val route = pendingTorchRoute
        pendingTorchRoute = null // защита от повторного исполнения

        if (!isGranted) {
            // Если доступа нет, то агент (Gemma-4) сообщает об этом.
            appendDialogueMessage(
                sender = AgentDialogueSender.AGENT,
                text = "Не могу выполнить: без разрешения камеры фонарик недоступен.",
            )

            // ЗАЩИТА: без labeled return можно вылететь из KsenaxHomeScreen вовсе
            return@rememberLauncherForActivityResult
        }

        // Дополнительная проверка, технически, callback permission мог сработать,
        // а отложенного рутера уже нет...
        if (route != null) {
            appendDialogueMessage(
                sender = AgentDialogueSender.AGENT,
                text = "Разрешение получила. Выполняю...",
            )

            // А ВОТ И ТОЧКА ОРКЕСТРАЦИИ, здесь начинают пути расходиться...
            // Красным подчёркнуто потому, что компилятор не всегда умеет вывести
            // из callback-а permission, хотя логически уже было проверено:
            //
            //      if (!isGranted) ...

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
     * Основная точка исполнения инструмента.
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
        // Делает первую попытку.
        when (val result = torchExecutor.execute(route)) {

            // Далее идёт три исхода:

            // Если нужно разрешение — вызывается лаунчер и вызов уже лезет туда
            is KsenaxToolResult.PermissionRequired -> {
                pendingTorchRoute = route
                appendDialogueMessage(
                    sender = AgentDialogueSender.AGENT,
                    text = "Нужно разрешение камеры. Открою системный запрос доступа.",
                )

                // Делегируем далее задачу уже лаунчеру и забываем про него в контуре функции
                cameraPermissionLauncher.launch(result.permission)
            }

            // При успехе тупо возвращаем удачное диалоговое окно
            is KsenaxToolResult.Success -> appendDialogueMessage(
                sender = AgentDialogueSender.AGENT,
                text = "Сделала!",
            )

            // При неудаче:
            // 1. Если отклонённое действие — не может выполнить по правилам промпта
            // 2. Если что-то другое, то это проблема на стороне устройства (камеры нет)
            is KsenaxToolResult.Failure -> {
                val prefix = if (route is KsenaxTorchRoute.Refused) {
                    "Не могу!"
                } else {
                    "Не получилось!"
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
     * TODO: вынести в отдельный контроллер, так как тут уже происходит оркестрация
     *
     * @since 0.1
     * @author Stepan Kolesnikov
     */
    @RequiresPermission(Manifest.permission.CAMERA)
    fun sendPromptToLocalAgent(userCommand: String) {
        // Проверка на корректность ввода ИЛИ является ли уже разруливаемым промптом (задачей)
        if (userCommand.isEmpty() || userCommand.isBlank() || uiState.isRoutingPrompt) {
            return
        }

        // Общение открывается
        uiState = uiState.copy(
            isConversationOpen = true
        )
        // Отрисовка сообщение пользователя
        appendDialogueMessage(
            sender = AgentDialogueSender.USER,
            text = userCommand, // raw user-prompt to orchestrator
        )

        // Теперь задача пользователя положена в состояние разруливаемой задачи (busy-flag).
        uiState = uiState.copy(
            isRoutingPrompt = true
        )

        // Больше пользователь пока что не может докидывать задачи.

        // Запуск новой корутины (почти эквивалент Java: Executor.submit(() -> doWork()))
        // Это делается ради того, чтобы НЕ ВЫПОЛНЯТЬ в UI-потоке!!!
        coroutineScope.launch {
            try {
                // Проверка, у нас вообще прямо сейчас ПЕРВОЕ сообщение, или нет?
                val shouldInitializeModel = localConversation == null

                // Если да, то нужно загрузиться модели
                // TODO: сделать реальную проверку на загрузку
                if (shouldInitializeModel) {
                    appendDialogueMessage(
                        sender = AgentDialogueSender.AGENT,
                        text = "Инициализируюсь...",
                    )
                }

                // Lazy initialization: если диалог отсутствует, то создаётся новый объект
                // А если был, то берем имеющийся
                val conversation = localConversation ?: run {
                    val nextConversation = KsenaxLocalConversation(
                        modelPath = installService.getGemma4E2BModelPath(),
                        cacheDirPath = installService.getGemma4E2BCacheDirPath(),
                        systemInstruction = KSENAX_TORCH_ROUTER_SYSTEM_PROMPT,
                    )

                    // Поднимается LiteRT-LM API, model mapping, inference context
                    nextConversation.initialize()
                    localConversation = nextConversation
                    nextConversation
                }

                // А теперь, когда диалог подготовлен, то модель едет оркестрировать
                appendDialogueMessage(
                    sender = AgentDialogueSender.AGENT,
                    text = "Выполняю...",
                )

                // Подкидывается пользовательский промпт для задачи...
                val modelResponse = conversation.ask(
                    "Команда пользователя:\n$userCommand"
                )

                // Парсинг JSON ответа нейросетевой модели (согласно системному промпту)
                val route = KsenaxTorchRouteParser.parse(modelResponse)

                // На этом этапе оркестратор понял, что за команда, только теперь объект
                // Например, KsenaxTorchRoute.TurnOn

                // Разруливатель
                // TODO: намутить отдельный контур с абстрактным маршрутизатором в разные тулзы
                executeTorchRoute(route)
            } catch (exception: Exception) {
                // Грубое подавление исключений любых.
                appendDialogueMessage(
                    sender = AgentDialogueSender.AGENT,
                    text = exception.message
                        ?: "Не удалось выполнить команду через локальную модель.",
                )
            } finally {
                // так как этот этап выполнится в любом случае, то здесь мы
                // обнуляем задачу, потому что она либо упала, либо выполнилась.
                uiState = uiState.copy(
                    isRoutingPrompt = false
                )
            }
        }
    }

    // Нужен, когда у Composable есть ресурс, который надо освободить при уходе экрана.
    // Выход на другой экран, swipe away из вкладок приложений и другие случаи...
    DisposableEffect(Unit) {
        // Из тяжелого у нас только общение с моделью, а значит закрываем его напрочь
        onDispose {
            localConversation?.close()
        }
    }

    // Есть ли на диске вообще кандидат из внутренних файлов?
    var hasGemmaCandidate by remember {
        mutableStateOf(OverlayState())
    }

    // Является ли кандидат валидным для работы (по SHA-256)?
    var isValidGemmaFile by remember {
        mutableStateOf(OverlayState())
    }

    /*
    * Запускает корутину, которая привязана к Composable.
    *      currentDownloadId: если меняется, то старый эффект отменится и запустится новый
    *      installService: аналогично
    * */
    LaunchedEffect(currentDownloadId, installService) {

        /*
        * САМА КОРУТИНА ЗАПУСКАЕТСЯ В ДВУХ СЛУЧАЯХ:
        * 1) При старте приложения (поэтому и оверлей работает)
        * 2) При загрузке модели
        * */

        // Первая попытка проверки.
        uiState = uiState.copy(
            isModelValidating = true
        )

        // Активной закачки модели в данный момент нет.
        // Но сама модель ещё пока может находиться на диске устройства
        if (currentDownloadId == NO_DOWNLOAD_ID) {

            hasGemmaCandidate = hasGemmaCandidate.copy(
                paragraph = OverlayLoadingState.LOADING
            )
            isValidGemmaFile = isValidGemmaFile.copy(
                paragraph = OverlayLoadingState.LOADING
            )

            // Если не запоминалась информация о скаченной модели, проверяется диск...
            if (!uiState.isDownloadRemembered) {
                // Модель вообще есть как файл?
                val hasCandidateModelFile = installService.hasGemma4E2BCandidateFile()

                if (!hasCandidateModelFile) {
                    hasGemmaCandidate = hasGemmaCandidate.copy(
                        paragraph = OverlayLoadingState.FAILURE
                    )
                    isValidGemmaFile = isValidGemmaFile.copy(
                        paragraph = OverlayLoadingState.FAILURE
                    )

                    uiState = uiState.copy(
                        isModelValidating = false
                    )

                    return@LaunchedEffect // вылет из тела эффекта
                }

                hasGemmaCandidate = hasGemmaCandidate.copy(
                    paragraph = OverlayLoadingState.NON_CONFIRMED
                )

                val isValidModelFile = installService.hasValidGemma4E2BFile()

                hasGemmaCandidate = hasGemmaCandidate.copy(
                    paragraph =
                        if (isValidModelFile)
                            OverlayLoadingState.SUCCESS
                        else
                            OverlayLoadingState.FAILURE
                )

                isValidGemmaFile = isValidGemmaFile.copy(
                    paragraph =
                        if (isValidModelFile)
                            OverlayLoadingState.SUCCESS
                        else
                            OverlayLoadingState.FAILURE
                )

                delay(Duration.ofMillis(500))

                // Запомнилась моделька + не прервалась.
                uiState = uiState.copy(
                    isDownloadRemembered = isValidModelFile,
                    isDownloadInterrupted = !isValidModelFile,
                    isModelValidating = false
                )

                // Если не валидна — сносится
                if (!isValidModelFile) {
                    installService.deleteGemma4E2BFile()
                }
            }

            return@LaunchedEffect // после проверки локального файла эффект заканчивается
        }

        // ВЕТКА: ЗАГРУЗКА АКТИВНА
        val downloadId = currentDownloadId // следит именно за тем ID, с которым стартовал

        uiState = uiState.copy(
            isModelValidating = false,
            isDownloading = true,
            isDownloadInterrupted = false,
            isDownloadCancelled = false
        )

        // Цикл наблюдения за DownloadManager
        while (true) {
            val status = installService.queryDownloadSnapshot(downloadId)

            if (status == null) {
                uiState = uiState.copy(
                    isDownloading = false,
                    isDownloadInterrupted = true
                )

                // Сброс активный ID
                currentDownloadId = NO_DOWNLOAD_ID
                // Чистка сохранённых следов загрузок:
                installService.clearGemma4E2BDownloadArtifacts()
                break
            }

            // Статус норм -> скачивается модель

            uiState = uiState.copy(
                downloadProgress = status.progress
            )

            // Обработка состояния загрузки
            when (status.state) {
                KsenaxDownloadState.SUCCESSFUL -> {

                    // 1. ПРОВЕРКА СКАЧЕННОГО ФАЙЛА (мало ли, скачался не тот файл или иное)

                    uiState = uiState.copy(
                        isModelValidating = true
                    )

                    val isValidModelFile = try {
                        installService.hasValidGemma4E2BFile()
                    } finally {
                        uiState = uiState.copy(
                            isModelValidating = false
                        )
                    }

                    // 2. Обновляется состояние UI по принципу из крайности в крайность.
                    uiState = uiState.copy(
                        downloadProgress = if (isValidModelFile) 1f else 0f,
                        isDownloading = false,
                        isDownloadInterrupted = !isValidModelFile,
                        isDownloadRemembered = isValidModelFile
                    )

                    // 3. СБРОС И ФИНАЛИЗАЦИЯ

                    // Активной загрузки нет, сброс
                    currentDownloadId = NO_DOWNLOAD_ID
                    // Стирание старого сохранённого ID, так как он больше не нужен
                    installService.clearSavedDownloadId()

                    // Невалидное -> чистка
                    if (!isValidModelFile) {
                        installService.deleteGemma4E2BFile()
                    }

                    break
                }

                KsenaxDownloadState.FAILED -> {
                    // Тупо обновление состояния (больше не качается, прервана)
                    uiState = uiState.copy(
                        isDownloading = false,
                        isDownloadInterrupted = true
                    )

                    // Очистка мусора...
                    currentDownloadId = NO_DOWNLOAD_ID
                    installService.clearGemma4E2BDownloadArtifacts()
                    break
                }

                // Всё остальное подавляется
                KsenaxDownloadState.PENDING,
                KsenaxDownloadState.RUNNING,
                KsenaxDownloadState.PAUSED,
                KsenaxDownloadState.UNKNOWN -> Unit
            }

            // Каждые полсекунды обновляется прогресс-бар.
            delay(500L.milliseconds)
        }
    }


    /*
    * КОНТУР ОТРИСОВКИ UI ПО СОСТОЯНИЮ ЧЕРЕЗ JETPACK COMPOSE
    * */

    // Давится предупреждение о правах, потому что выше везде было задекларировано,
    // что требуются права на камеру!
    @SuppressLint("MissingPermission")
    Box(modifier = Modifier.fillMaxSize()) {
        // Рисует фон на весь размер родительского Box
        KsenaxNebulaBackground(modifier = Modifier.matchParentSize())

        // Если пользователь отправил сообщение, то нужно открыть переписку
        if (uiState.isConversationOpen) {
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

                    // Очистка поля активного промпта и запуск
                    if (userCommand.isNotBlank()) {
                        dialogueInputText = ""
                        sendPromptToLocalAgent(userCommand)
                    }
                },
                isBusy = uiState.isRoutingPrompt,
                modifier = Modifier.matchParentSize(), // Занимаем фулл экран
            )
        } else {
            // Если диалога еще нет, то показывается главная
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent, // это для отображения KsenaxNebulaBackground
                bottomBar = {
                    LocalAgentBottomBar(
                        modifier = Modifier
                            // Занимать всю ширину
                            .fillMaxWidth()
                            // Не залезать под Android Navigation Bar
                            .navigationBarsPadding()
                            // Добавить внутренние/внешние отступы вокруг элемента
                            .padding(start = 45.dp, end = 45.dp,
                                top = 10.dp, bottom = 8.dp),
                    )
                },
            ) { innerPadding -> // Напомню, что innerPadding отдаётся Scaffold-ом для отступов
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {

                    //        Ksenax
                    //      --- * ---
                    // Agentic orchestrator...
                    KsenaxHeader(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(start = 28.dp, end = 28.dp, top = 56.dp),
                    )

                    // Стартовое поле ввода команды
                    AgentPromptComposer(
                        text = promptText,                 // Стартовое поле ввода команды
                        onTextChange = { nextText ->       // controlled input
                            if (nextText.length <= 500) {
                                promptText = nextText
                            }
                        },
                        onSend = {                         // Логика отправки с главного экрана
                            val userCommand = promptText.trim()

                            if (userCommand.isNotBlank()) {
                                promptText = ""
                                sendPromptToLocalAgent(userCommand)
                            }
                        },
                        modifier = Modifier                // Поле ввода прибито к низу экрана
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(start = 22.dp, end = 22.dp, bottom = 12.dp),
                        uiStateFromHomeScreen = uiState,   // Передача состояния как объекта
                        onDownloadModel = {                // Callback на кнопку скачки/отмены
                            // Логика такая:
                            // Если модель скачивается, значит есть вариант ТОЛЬКО ОТМЕНИТЬ
                            if (uiState.isDownloading) {
                                installService.cancelGemma4E2BDownload(currentDownloadId)

                                // После отмены нужно переписать состояние сейчас
                                uiState = uiState.copy(
                                    isDownloading = false,
                                    isDownloadInterrupted = false,
                                    isDownloadCancelled = true
                                )

                                // Сброс ID закачки
                                currentDownloadId = NO_DOWNLOAD_ID
                            }
                            // Если модель не скачивается, то нажатие приводит к СКАЧКЕ
                            else {
                                val downloadId = installService.startGemma4E2BDownload()
                                currentDownloadId = downloadId

                                uiState = uiState.copy(
                                    downloadProgress = 0f,
                                    isDownloading = true,
                                    isDownloadInterrupted = false,
                                    isDownloadCancelled = false
                                )
                            }
                        }
                    )
                }
            }
        }

        // Если модель в данный момент проверяется, то запускается отдельный оверлей
        AnimatedVisibility(
            visible = uiState.isModelValidating,
            modifier = Modifier.matchParentSize(),
            enter = EnterTransition.None,
            exit = fadeOut(
                animationSpec = tween(260)
            ) + slideOutVertically(
                animationSpec = tween(360),
                targetOffsetY = { -it / 3 }
            ),
        ) {
            ModelIndexingOverlay(
                modifier = Modifier.matchParentSize(),
                hasGemmaCandidateFile = hasGemmaCandidate,
                isValidGemmaFile = isValidGemmaFile,
            )
        }
    }
}