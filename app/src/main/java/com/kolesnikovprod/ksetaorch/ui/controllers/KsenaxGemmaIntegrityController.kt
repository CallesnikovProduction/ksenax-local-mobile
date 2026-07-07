package com.kolesnikovprod.ksetaorch.ui.controllers

import com.kolesnikovprod.ksetaorch.download.contracts.KsenaxModelInstallUseCase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Управляет проверкой локального файла Gemma перед первым запуском модели в
 * текущем процессе приложения.
 *
 * Контроллер последовательно проверяет две вещи:
 * 1. существует ли непустой файл-кандидат;
 * 2. совпадают ли его размер и SHA-256 с ожидаемыми значениями.
 *
 * Успешная проверка запоминается до завершения процесса. Поэтому повторное
 * открытие Basic- или Agentic-чата не перечитывает большой файл с диска.
 * Результаты [KsenaxGemmaVerificationResult.Missing] и
 * [KsenaxGemmaVerificationResult.Invalid] не кэшируются: после установки или
 * замены модели следующий запрос сможет выполнить проверку заново.
 *
 * Контроллер не загружает модель в память, не запускает LiteRT-LM и не
 * исправляет повреждённый файл. Он только сообщает вызывающей ViewModel,
 * разрешён ли переход к подготовке model runtime.
 *
 * @property installUseCase источник операций проверки выбранного артефакта
 * семейства Gemma.
 *
 * @author Stephan Kolesnikov
 */
interface KsenaxModelIntegrityVerifier {
    suspend fun verifyOnce(
        onStageChanged: (KsenaxGemmaVerificationStage) -> Unit,
    ): KsenaxGemmaVerificationResult
}

class KsenaxGemmaIntegrityController(
    private val installUseCase: KsenaxModelInstallUseCase,
) : KsenaxModelIntegrityVerifier {

    /**
     * Не позволяет нескольким параллельным чатам одновременно считать и
     * проверять один и тот же файл модели.
     *
     * Проверка внутри mutex повторно смотрит на кэшированный флаг: пока одна
     * coroutine ждала блокировку, другая могла уже успешно завершить проверку.
     */
    private val verificationMutex = Mutex()

    /**
     * Признак успешной проверки Gemma в текущем процессе приложения.
     *
     * `@Volatile` делает быстрый путь чтения видимым между потоками. Изменение
     * значения всё равно выполняется только внутри [verificationMutex].
     */
    @Volatile
    private var isVerifiedForCurrentProcess = false

    /**
     * Проверяет файл Gemma, если он ещё не был подтверждён в текущем процессе.
     *
     * При первой реальной проверке вызывающая сторона получает этап
     * [KsenaxGemmaVerificationStage.CheckingPresence], а после обнаружения
     * файла — [KsenaxGemmaVerificationStage.CheckingIntegrity]. Если другой
     * вызов уже успешно проверил модель, метод сразу возвращает
     * [KsenaxGemmaVerificationResult.Valid] и не повторяет этапы.
     *
     * Ошибка доступа к файлу преобразуется install use case-ом в неуспешную
     * integrity-проверку. Отмена coroutine не перехватывается и передаётся
     * вызывающему lifecycle-контуру.
     *
     * @param onStageChanged callback для отображения текущего этапа проверки.
     * Он вызывается только тогда, когда соответствующая проверка действительно
     * начинается.
     * @return [KsenaxGemmaVerificationResult.Valid], если файл можно передавать
     * model runtime; [KsenaxGemmaVerificationResult.Missing], если кандидата
     * нет; [KsenaxGemmaVerificationResult.Invalid], если размер или SHA-256 не
     * прошли проверку.
     *
     * @since 0.2
     */
    override suspend fun verifyOnce(
        onStageChanged: (KsenaxGemmaVerificationStage) -> Unit,
    ): KsenaxGemmaVerificationResult {
        if (isVerifiedForCurrentProcess) {
            return KsenaxGemmaVerificationResult.Valid
        }

        return verificationMutex.withLock {
            if (isVerifiedForCurrentProcess) {
                return@withLock KsenaxGemmaVerificationResult.Valid
            }

            onStageChanged(KsenaxGemmaVerificationStage.CheckingPresence)
            if (!installUseCase.hasInstallCandidate()) {
                return@withLock KsenaxGemmaVerificationResult.Missing
            }

            onStageChanged(KsenaxGemmaVerificationStage.CheckingIntegrity)
            if (!installUseCase.hasValidInstallation()) {
                return@withLock KsenaxGemmaVerificationResult.Invalid
            }

            isVerifiedForCurrentProcess = true
            KsenaxGemmaVerificationResult.Valid
        }
    }
}

/**
 * Проверяет пару моделей, обязательную для agentic work pipeline-а:
 * G4 planner + FunctionGemma action compiler.
 *
 * UI пока имеет две стадии — presence и integrity — поэтому composite
 * прокидывает те же stages для каждой модели. Детализация "какая именно модель"
 * остаётся в сообщении ошибки ViewModel через общий заголовок agentic-моделей.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
class KsenaxCompositeModelIntegrityVerifier(
    private val verifiers: List<KsenaxModelIntegrityVerifier>,
) : KsenaxModelIntegrityVerifier {

    init {
        require(verifiers.isNotEmpty()) {
            "Composite integrity verifier requires at least one verifier."
        }
    }

    override suspend fun verifyOnce(
        onStageChanged: (KsenaxGemmaVerificationStage) -> Unit,
    ): KsenaxGemmaVerificationResult {
        verifiers.forEach { verifier ->
            when (val result = verifier.verifyOnce(onStageChanged)) {
                KsenaxGemmaVerificationResult.Valid -> Unit
                else -> return result
            }
        }
        return KsenaxGemmaVerificationResult.Valid
    }
}

/**
 * Этапы проверки, которые UI может показать пользователю.
 *
 * Этап запуска модели сюда не входит: подготовкой LiteRT-LM управляет chat
 * ViewModel после результата [KsenaxGemmaVerificationResult.Valid].
 *
 * @author Stephan Kolesnikov
 */
enum class KsenaxGemmaVerificationStage {
    /** Поиск непустого файла-кандидата Gemma в ожидаемой директории. */
    CheckingPresence,

    /** Проверка ожидаемого размера файла и его SHA-256. */
    CheckingIntegrity,
}

/**
 * Итог проверки локальной установки Gemma.
 *
 * Результат описывает только состояние файла. Он не гарантирует, что LiteRT-LM
 * engine уже создан или что устройству хватит памяти для запуска модели.
 *
 * @author Stephan Kolesnikov
 */
sealed interface KsenaxGemmaVerificationResult {
    /** Файл существует и прошёл проверку размера и SHA-256. */
    data object Valid : KsenaxGemmaVerificationResult

    /** Непустой файл-кандидат Gemma не найден. */
    data object Missing : KsenaxGemmaVerificationResult

    /** Файл найден, но его размер, SHA-256 или чтение не прошли проверку. */
    data object Invalid : KsenaxGemmaVerificationResult
}
