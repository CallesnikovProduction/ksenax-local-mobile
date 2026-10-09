# SPEC-001: маршрутизация запросов (0.4)

> Это исторический отчёт локальных проверок SPEC-001. Его статус готовности
> не заменяет обязательную проверку на телефоне по SPEC-001A; актуальные
> результаты и ограничения находятся в [живом отчёте](SPEC-001A-live-routing.md).

@author Stephan Kolesnikov · @since 0.4

## /spec

Исходный поток: ViewModel → `KsenaxAgenticWorkRuntime` → FG direct/planned →
Keywords выбирают kit → второй FG-вызов → policy → executor. Календарь и
заметки всегда проходили G4. Это не yes/no, но те же ограничения двух состояний.
LiteRT-LM 0.13.1 поддерживает `OpenApiTool`, `Message.toolCalls` и
`automaticToolCalling=false`; ручной chat-template в Agentic больше не нужен.

UI, STT, persistence заметок, состав инструментов и shared engine не меняются.
Модель предлагает вызов; схема, время, policy и исполнение принадлежат Kotlin.

## /plan

| Срез | Причина и файлы | Наблюдаемый результат / проверка |
| --- | --- | --- |
| 1. Native functions | `model`: отдельные request/response, LiteRT adapter | Свежая Conversation, без автоматического выполнения и ручных токенов; compile |
| 2. Семантический маршрут | `work/routing`, runtime | Известная функция / G4 / уточнение / отказ, без Keywords; router tests |
| 3. Валидация | `work/oneshot`, alarm/calendar kits | Типы, обязательные поля, диапазоны, время и данные UP проверены до executor; negative tests |
| 4. Ошибки и диагностика | runtime, planning parser | Нет исполнения после ошибки, отмена сохраняется, метаданные без текста UP; runtime tests |
| 5. Регрессия и review | тесты и этот отчёт | Unit tests, APK, lint, пять осей review, перечень AC |

## Ограничение доказательств

Подменённые модельные ответы доказывают поведение Kotlin, но не качество реальной
FG на русском и не задержки телефона. Эти свойства требуют отдельного прогона
двух установленных моделей на устройстве. Нельзя объявлять их проверенными по
одной сборке или unit-тестам.

## Коррекция плана

Общий каталог полных схем мог переполнить 1024 токена. Поэтому root-вызов
выбирает только код; второй FG-запрос извлекает параметры одной функции.
Фонарик требует один FG-вызов. UP больше 256 UTF-8 байт проходит FG с единственным
маршрутом планирования; G4 получает полный исходный текст после обычного trim.
Это ограничение размера входа, не точный подсчёт токенов: SDK 0.13.1 не публикует
tokenizer API. Запас самого native template необходимо проверить на телефоне.

Календарный атомарный контракт использует local datetime, не одновременно delay
и all_day. Изменение предотвращает расхождение между проверенным временем и
реальным временем в Android intent. Внешний legacy executor сохранён.

## /build

| Срез | Изменение | Основные файлы | Проверка |
| --- | --- | --- | --- |
| 1 | Native функции, свежая Conversation, отключено automatic execution | `model/KsenaxModelFunctionRequest`, `FunctionCallAdapter`, `LiteRtModelSessionEngine` | compile, adapter tests |
| 2 | Четыре маршрута; семантический выбор кода без Keywords | `FunctionGemmaRoutingProtocol`, `RequestRoute`, `KsenaxAgenticWorkRuntime` | router/runtime tests |
| 3 | Схема, типы, диапазоны, русское время/числа, единый calendar start | `ActionArgumentsValidator`, `LocalScheduleParser`, alarm/calendar/torch kits | положительные и отрицательные сценарии |
| 4 | Строгий payload заметки, preflight всего плана, отмена и mutex | notes kit/executor, runtime, G4 parser | note/compound/cancellation/concurrency tests |
| 5 | Диагностика без UP/контента, регрессия, независимый review | controller, документация, тесты | unit tests, APK, lint, diff |

## Критерии AC

| Критерии | Доказательство и предел |
| --- | --- |
| AC-1,2 | `RequestRoute` и router tests: функция / план / уточнение / отказ, не yes/no |
| AC-3,4,5 | runtime tests: фонарик (1 FG), будильник и календарь (2 FG), 0 G4 для полных запросов |
| AC-6 | Даже валидное придуманное моделью время без времени в UP не исполняется |
| AC-7 | Заметки и генерация используют G4; полный текст проходит executor, не FG |
| AC-8 | Нет Keywords-gate; тесты перефразовок доказывают обработку соответствующего FG-call, не качество модели |
| AC-9 | Полный смешанный план; защита явной генерации; все входы и FG-calls проверены до исполнения |
| AC-10,11 | Неизвестные, пустые, множественные, неверно типизированные ответы не исполняются |
| AC-12,13 | Экраны, навигация и ViewModel не изменены; existing turn results показывают уточнение/отказ/ошибку |

## /test

Итоговый прогон: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --no-daemon --max-workers=1`.
169 тестов, включая 59 communication-тестов: 0 failures, 0 errors. APK собран.
Lint: 0 errors, 88 warnings. `git diff --check` проходит.

| Сценарий | Доказательство в тестах |
| --- | --- |
| Фонарик | Включение, выключение, toggle: 1 FG, без G4; несовпадение намерения не исполняется |
| Будильник | 2 FG, без G4; число, часы/минуты, русские формулировки, пропущенное время и диапазоны |
| Календарь | 2 FG, без G4; дата/время проверены; без точного времени требуется уточнение |
| Заметки | G4 готовит содержимое; FG подтверждает действие; весь текст передаётся executor |

Все перечисленные модельные ответы в тестах подменены. Реальная точность FG/G4,
вместимость native template в 1024 токена, скорость и Android-исполнение — NOT RUN:
`adb devices` не обнаружил подключённого телефона. Календарный executor открывает
системную форму, а не доказывает сохранение события. Массовое удаление будильников
не появилось: ограничение публичного Android API осталось явным отказом.

## /review

Проверены correctness, architecture, robustness, maintainability и scope.
Независимый review выявил и помог устранить: частичные числительные, неверный
clear-all, fallback текста заметки, конфликт start/all_day, проглоченную отмену,
частичное исполнение до проверки позднего FG-call и ложное совпадение «сейчас».
Android side effects не транзакционны; реальная ошибка второго executor не
откатывает первый. Результат сохраняет уже выполненные шаги и причину остановки.

| Axis | Result | Findings |
| --- | --- | --- |
| Correctness | PASS | Найденные ошибки исправлены; положительные и отрицательные сценарии проходят |
| Architecture | PASS | Модельный выбор отделён от schema/domain/policy и Android execution |
| Robustness | PASS | Mutex, отмена, закрытие Conversation, preflight и остановка при ошибке |
| Maintainability | PASS | Контракты и короткие пояснения; новые классы помечены 0.4 |
| Scope compliance | PASS | UI/STT не менялись; новых инструментов и зависимостей нет |

PASS относится к проверенному коду и сборке, не к отсутствующему телефонному прогону.

## /ship

READY FOR USER COMMIT

Блокеров по проверенному diff нет. Коммит и push не выполнялись. APK:
`app/build/outputs/apk/debug/app-debug.apk`. Уже существовавшие изменения других
контуров сохранены; перед коммитом следует выбрать только нужные файлы.

## Файлы текущей задачи

Пути ниже относительны корню проекта. Уже имевшиеся изменения других контуров
не входят в этот перечень и не откатывались.

- `docs/SPEC-001-routing.md`
- `app/src/main/java/com/kolesnikovprod/ksetaorch/communication/model/AGENTS.md`
- `app/src/main/java/com/kolesnikovprod/ksetaorch/communication/model/KsenaxModelFunctionRequest.kt`
- `app/src/main/java/com/kolesnikovprod/ksetaorch/communication/model/KsenaxModelSession.kt`
- `app/src/main/java/com/kolesnikovprod/ksetaorch/communication/model/internal/litert/FunctionCallAdapter.kt`
- `app/src/main/java/com/kolesnikovprod/ksetaorch/communication/model/internal/litert/LiteRtModelSessionEngine.kt`
- `app/src/main/java/com/kolesnikovprod/ksetaorch/communication/tools/builtin/alarm/AlarmOneShotToolModule.kt`
- `app/src/main/java/com/kolesnikovprod/ksetaorch/communication/tools/builtin/alarm/AlarmToolExecutor.kt`
- `app/src/main/java/com/kolesnikovprod/ksetaorch/communication/tools/builtin/calendar/CalendarEventOneShot.kt`
- `app/src/main/java/com/kolesnikovprod/ksetaorch/communication/tools/builtin/calendar/CalendarEventOneShotToolModule.kt`
- `app/src/main/java/com/kolesnikovprod/ksetaorch/communication/tools/builtin/flashlight/TorchToolModule.kt`
- `app/src/main/java/com/kolesnikovprod/ksetaorch/communication/tools/builtin/notes/ObsidianNoteOneShotToolModule.kt`
- `app/src/main/java/com/kolesnikovprod/ksetaorch/communication/tools/builtin/notes/ObsidianWriterToolExecutor.kt`
- `app/src/main/java/com/kolesnikovprod/ksetaorch/communication/tools/builtin/scheduling/LocalScheduleParser.kt`
- `app/src/main/java/com/kolesnikovprod/ksetaorch/communication/work/AGENTS.md`
- `app/src/main/java/com/kolesnikovprod/ksetaorch/communication/work/actions/KsenaxOneShotActionKit.kt`
- `app/src/main/java/com/kolesnikovprod/ksetaorch/communication/work/oneshot/ActionArgumentsValidator.kt`
- `app/src/main/java/com/kolesnikovprod/ksetaorch/communication/work/oneshot/KsenaxOneShotToolProtocol.kt`
- `app/src/main/java/com/kolesnikovprod/ksetaorch/communication/work/planning/G4PlanningPromptFactory.kt`
- `app/src/main/java/com/kolesnikovprod/ksetaorch/communication/work/planning/G4PlanningResponseParser.kt`
- `app/src/main/java/com/kolesnikovprod/ksetaorch/communication/work/routing/FastRequestConstraints.kt`
- `app/src/main/java/com/kolesnikovprod/ksetaorch/communication/work/routing/FunctionGemmaRoutingProtocol.kt`
- `app/src/main/java/com/kolesnikovprod/ksetaorch/communication/work/routing/RequestRoute.kt`
- `app/src/main/java/com/kolesnikovprod/ksetaorch/communication/work/runtime/KsenaxAgenticWorkRuntime.kt`
- `app/src/main/java/com/kolesnikovprod/ksetaorch/communication/work/runtime/KsenaxWorkDiagnostic.kt`
- `app/src/main/java/com/kolesnikovprod/ksetaorch/ui/controllers/AGENTS.md`
- `app/src/main/java/com/kolesnikovprod/ksetaorch/ui/controllers/KsenaxAgenticWorkController.kt`
- `app/src/test/java/com/kolesnikovprod/ksetaorch/communication/model/FunctionCallAdapterTest.kt`
- `app/src/test/java/com/kolesnikovprod/ksetaorch/communication/tools/builtin/scheduling/LocalScheduleParserTest.kt`
- `app/src/test/java/com/kolesnikovprod/ksetaorch/communication/tools/builtin/notes/ObsidianNoteOneShotToolModuleTest.kt`
- `app/src/test/java/com/kolesnikovprod/ksetaorch/communication/work/planning/G4PlanningResponseParserTest.kt`
- `app/src/test/java/com/kolesnikovprod/ksetaorch/communication/work/routing/FunctionGemmaRoutingProtocolTest.kt`
- `app/src/test/java/com/kolesnikovprod/ksetaorch/communication/work/runtime/KsenaxAgenticWorkRuntimeTest.kt`

Источник native API: [официальная документация LiteRT-LM](https://developers.google.com/edge/litert-lm/android).
Сигнатуры отдельно сверены с локальным AAR 0.13.1, а не только с документацией latest.
