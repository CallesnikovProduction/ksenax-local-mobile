# SPEC-001C — Chat и Temporaric

@author Stephan Kolesnikov
@since 0.4

## /spec: фактический поток

Basic: `KsenaxBasicChatScreen → KsenaxBasicChatViewModel → KsenaxBasicChatCoordinator
→ KsenaxModelSession.streamPersistent → model runtime → delta/completed → ViewModel`.
История: `storage.chat.domain.KsenaxChatRepository → Room → Flow → UI snapshot`.
SavedStateHandle сохраняет только activeChatId; временный streaming-буфер не является второй базой.
Coordinator добавляет сохранённый transcript к существующей system instruction;
runtime пересоздаёт conversation при изменении instruction. Этот контракт не меняется.

Temporaric: `KsenaxTemporaricChatScreen → KsenaxTemporaricChatViewModel
→ KsenaxTemporaricChatCoordinator → KsenaxModelSession.streamEphemeral → delta/completed`.
Сообщения принадлежат ViewModel главной back-stack записи. Room/SavedStateHandle
нет; каждый turn изолирован. Выход сам по себе не уничтожает эту ViewModel.

Обе ViewModel владеют jobs через viewModelScope; model runtime принадлежит
application, а не экрану. Screen наблюдает read-only state и эффекты.

## Находки до изменений

| Место | Наблюдение | Почему важно | Оценка | Исправление | Риск |
| --- | --- | --- | --- | --- | --- |
| UiState, repository | История и временный буфер имеют разных владельцев | Нет лишней копии постоянного хранилища | KEEP | Сохранить | Нет |
| Coordinators | SDK скрыт, persistent/ephemeral различаются явно | Граница transport уже корректна | KEEP | Не менять prompt/session | Нет |
| Basic.sendMessage | busy выставлен после suspend persistence | Повторный send отменяет первый; выбор чата может обогнать запись | ISSUE | Резервировать request до launch/suspend | Низкий; исправляет гонку |
| Temporaric navigation/reset | verificationJob остаётся активным | Поздняя генерация после выхода/очистки | ISSUE | Отмена gate у владельца lifecycle | Низкий; исправляет гонку |
| Basic.onCleared | Флаг сохранения выставляется после отмены scope | Уже полученный partial теряется | ISSUE | Сохранение в cancellation handler по lifecycle scope | Низкий; восстанавливает заявленную семантику |
| Basic.latestStoredChats | История запроса берётся из копии UI-уведомлений | При задержке уведомления теряется сохранённый контекст | ISSUE | Читать snapshot через repository.observeChat.first | Низкий; тот же transcript из актуальных данных |
| Constructors | Конкретный verifier и Android clock | Затрудняют JVM-проверку lifecycle | ISSUE | Существующий interface, injectable clock с тем же default | Низкий |
| Chat events | Два одинаковых DTO и mapping одного stream | Исправления transport нужно повторять | MINOR | Один общий stream-контракт | Низкий; только два flow |
| Gate enum в basic | Используется также другими режимами | Размещение неидеально, но перенос затронет вне-scope код | KEEP | Не переносить в этом аудите | Нет |

## /plan

1. Тестовые границы: verifier interface, управляемые часы, test-only coroutines-test
   той же версии 1.10.2. Воспроизвести гонки на задержанных fake dependencies.
2. Lifecycle: синхронный допуск одного запроса, отмена verification при уходе/reset;
   partial/error/retry и постоянная/RAM-only история сохраняют свою семантику.
3. Общий DTO ответа и mapping; не объединять хранение или полные ViewModel.
4. Targeted tests после каждого этапа, затем полный unit/build/lint и отдельный review.

Agentic, model configuration, STT, prompts и дизайн UI не входят в изменения.
Live device / ADB: NOT APPLICABLE FOR SPEC-001C.
Agentic inference: NOT TESTED BY DESIGN. Commit/push запрещены.

## /build: выполненные срезы

| Срез | Причина и изменение | Файлы | Владелец ответственности |
| --- | --- | --- | --- |
| A — тестовые границы | Concrete verifier заменён существующим интерфейсом; Android clock сохранён как default; добавлена только testImplementation coroutines-test 1.10.2 | Обе chat ViewModel, app/build.gradle.kts, ChatTestFixtures.kt | Presentation принимает зависимости, IO/runtime остаются ниже |
| B — lifecycle | Допуск до suspend; вход в generation cleanup через UNDISPATCHED; pending gate отменяется при уходе/reset; partial сохраняется при отмене scope; отмена до записи очищает только transient, не draft | Обе chat ViewModel, ChatLifecycleTest.kt | ViewModel владеет запросом и UI-состоянием, lifecycle владеет scope |
| C — история | latestStoredChats удалён; snapshot для нового turn-а читается через repository.observeChat.first | Basic ViewModel | Repository, не UI-кэш, владеет постоянной историей |
| D — transport | Один KsenaxChatStreamEvent и toChatEvents вместо двух одинаковых DTO/mapping | basechat coordinators, KsenaxChatStreamEvent.kt, оба потребителя | Communication переводит model events в chat events |

После A/B тесты действительно воспроизвели четыре исходных отказа, затем прошли.
После C воспроизведена потеря transcript при задержке уведомлений, затем исправлена.
Review выявил два края раннего Stop; отдельные red-регрессии добавлены и исправлены
для обоих режимов. Нормальные тексты, порядок событий, настройки модели и сообщения
об ошибках не менялись; только заявленные выше ошибочные сценарии исправлены.

## Архитектурный итог

| Область | До | Решение | После |
| --- | --- | --- | --- |
| State ownership | История запроса зависела от mirrored domain/UI snapshot | Удалить зеркало | Repository — источник истории, UiState — отображение |
| Transport | Корректные две session-семантики; дублированные события | Сохранить режимы, объединить mapping | Общий DTO, прежние persistent/ephemeral вызовы |
| Coroutine lifecycle | Окна повторного send, поздней генерации, раннего Stop | Синхронный допуск и определённый cleanup | Один активный запрос, отмена не становится ошибкой |
| Session ownership | Application владеет runtime; onCleared дублировал отмену с поздним флагом | Убрать поздний hook | Scope отменяет job, handler сохраняет partial, runtime не закрывается |
| Duplication | Одинаковые reply DTO/mapping | Общий контракт | Storage и navigation остаются раздельными |
| Packages | Границы пригодны; gate enum расположен неидеально | KEEP, без переноса Agentic-зависимостей | Общий reply-контракт расположен в basechat |

## /test

Команды (Windows, существующий Gradle/JDK):

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.kolesnikovprod.ksetaorch.ui.viewmodels.chat.ChatLifecycleTest' --offline --max-workers=1
.\gradlew.bat :app:testDebugUnitTest --tests 'com.kolesnikovprod.ksetaorch.communication.orchestration.basechat.*' --offline --max-workers=1
.\gradlew.bat :app:testDebugUnitTest --tests 'com.kolesnikovprod.*' :app:assembleDebug :app:lintDebug --offline --max-workers=1
git diff --check
```

- Профильное покрытие: 16 lifecycle + 4 Basic coordinator + 2 Temporaric coordinator,
  всего 22. Пустой ввод, порядок/однократность сообщений, отказ/повтор, ранняя и
  потоковая отмена, clear, history restore, задержка UI-уведомлений, RAM reset/изоляция.
- Полный финальный unit-пакет: **247 тестов, 0 failures, 0 errors**.
- Compile/assembleDebug: **PASS**, APK собран. diff --check: **PASS**.
- lintDebug: **FAIL**, существующий `PropertyEscape` в игнорируемом local.properties:8,
  1 error / 90 warnings. В chat/basechat-файлах lint-замечаний нет. Среди общих
  warnings — более новая coroutines-test; 1.10.2 намеренно совпадает с runtime.
  Local SDK setting, baseline, правила lint и версии production не изменялись.
- Итоговая составная Gradle-команда exit 1 только из-за lint; это не failure unit/build.
- Реальные Room IO, Android lifecycle/UI rendering и ответы модели не исполнялись:
  проверка использует управляемые JVM-зависимости, не выдаётся за device E2E.

Live device / ADB: NOT APPLICABLE FOR SPEC-001C.
Agentic inference: NOT TESTED BY DESIGN.

## /review

Независимый read-only review актуального среза и отдельный review primary:

| Ось | Итог | Основание |
| --- | --- | --- |
| Correctness | PASS | Дублирование, потеря history/partial и ранний Stop закрыты регрессиями |
| Architecture | PASS | ViewModel не исполняет runtime/SQL; storage и session имеют явных владельцев |
| Robustness | PASS | Scope cleanup, повтор после отказа, отсутствие позднего Temporaric turn |
| Maintainability | PASS | Один reply DTO/mapping, без generic framework и новых manager-классов |
| Scope compliance | PASS | Chat-only срез, прежние prompts/config/UI; нет ADB/inference/commit/push |

Рабочее дерево до SPEC-001C уже содержало изменения предыдущих спецификаций.
Они сохранены и не являются частью этого среза. Runtime/model/work/Agentic,
storage и экраны этим аудитом не редактировались. Локальная lint-ошибка отделена
от архитектурного gate: blocker в Chat/Temporaric не остался, но общепроектный
lint нельзя называть зелёным.

## /ship

SPEC-001C PASSED — READY FOR USER REVIEW
