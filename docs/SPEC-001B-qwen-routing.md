# SPEC-001B: Qwen3-0.6B

@author Stephan Kolesnikov
@since 0.4

## Контракт и наблюдение

Модель под тестом — реальный Qwen3-0.6B mixed INT4, SHA-256
`7900eb4e7362d88c58782c6f9999bb7a129e03544aa98b8f338ea0cc5d8c22c1`.
CPU, LiteRT-LM 0.13.1, контекст 1024, greedy, `enable_thinking=false`.
FG/G4, данные и выбор папки не заменяются. UI и executors не изменяются.

| Путь | Вход и формат | Разбор |
| --- | --- | --- |
| Предыдущий native | Штатный ChatML; system с `<tools>`; user=UP; JSON в `<tool_call>` | SDK Qwen3DataProcessor, затем старый селектор одного вызова |
| Предыдущий text_choice | Каталог вместе с UP в user; ожидание одного кода | Проверка известного кода; это не native calling |
| Новый structured_json | Каталог/правила/время в system; user=исходный UP; один JSON с route/tool/arguments | `QwenRoutingProtocol`, затем существующая schema-validation |

Рендер предыдущего native подтвердил один шаблон и закрытый пустой `<think>`.
SDK сам оформляет tools как OpenAI-style JSON. OpenKsenax не добавлял FG-токены.
Строка `call:torch_on{}}` в сохранённом прогоне Qwen не обнаружена; её происхождение
не установлено, точечный repair не добавлен. Два `}` в нормальном Qwen JSON с
вложенным пустым arguments сами по себе не означают ошибку.

В `qwen-candidate-routing4-20261006-v1` ON вернул обычное «Включите фонарик.»
без tool call; SDK передал текст, а селектор отверг ноль вызовов. Native OFF
дал допустимый call. Количество и заметка не прошли. Это разные виды отказа,
не доказательство одного malformed-suffix дефекта.

## Исправляемая граница

Предыдущий тест смешал семантическую классификацию с предложением Android-операций
без аргументов: встроенный шаблон допускает ноль/несколько calls, приложение
требует один выбор. Text-вариант дополнительно смешал каталог с user input.
Новый протокол отделяет эти ответственности и получает аргументы в собственном
JSON, не интерпретируя синтаксис FunctionGemma как формат Qwen.

Парсер не исправляет текст. Он отвергает чужие форматы, лишние root-поля,
неизвестный tool, второй JSON-корень, повторные/escaped ключи, неправильный
тип arguments, глубину выше 32 и ответ больше 16384 символов. Schema/domain,
tool existence и policy остаются отдельными барьерами перед исполнением.

## Сырой ответ

`spec001b-qwen-raw4-v1` использовал low-level Session и уже rendered prompt.
SDK Session по умолчанию может дополнительно применить basic template;
точная повторная обёртка зависит от metadata файла. Этот прогон не считать
точным воспроизведением native Conversation. Его длинный `<think>` не
доказывает отказ штатного non-thinking режима.

Исправленный захват: Qwen renderer → identity Conversation; проверка байтового
равенства входа; tools/channels пусты, grammar/automatic calling выключены.
При пустых tools Qwen processor передаёт текст без tool-JSON parsing.
Renderer и inference не делят историю; глобальный template восстанавливается
сразу после создания. В `spec001b-qwen-stage1-v3` prefill совпал с native:
693 токена. Сырой ответ ON — «Включите фонарик.», 9 decode-токенов,
7,714 с. Длинного reasoning нет; отсутствует именно вызов функции.
Источники: [Session defaults](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.13.1/runtime/engine/engine_settings.h#L311),
[Conversation override](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.13.1/runtime/conversation/conversation.cc#L102),
[Qwen parser](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.13.1/runtime/conversation/model_data_processor/qwen3_data_processor.cc#L60).

## Проверки на телефоне, 6 октября 2026

После переподключения оба APK установлены через `-r -t`, без удаления данных.
Приложение: `36F14C7CA8823AEBA924E64F6359990B038A4582C3EE083E2718C29843752707`;
тестовый APK: `6C56AFF11F478356397CF1E7B8DE0ECE703501D6C34D26BBD69D7A15465DFDCD`.
В тестах работает отдельный реальный Qwen. Маршрутизатор приложения по умолчанию
не заменён: новый адаптер не подключается до успешного live-gate.

Первый structured ON (`stage1-v3`) вернул Markdown-обёртку, лишние корневые
поля и count для безаргументного фонарика. Общее правило промпта уточнено:
ровно три поля FAST_TOOL; count только у объявляющих его инструментов.

| Прогон | Сценарии | Допустимый контракт | Наблюдение |
| --- | --- | --- | --- |
| `spec001b-qwen-zeroargs3-v4` | ON, новая формулировка ON, OFF | 0/3 | Markdown, лишние поля; OFF также дублирует ключи |
| `spec001b-qwen-cross5-v5` | Будильник, уточнение, календарь, заметка, неподдерживаемое действие | 0/5 | Помимо формата, неверные инструменты и маршруты |

Cross5 — диагностика масштаба отказа, не прохождение следующих ступеней:
первый gate остаётся провален. Будильник на 07:30 выбран как torch_on;
будильник без времени получил выдуманное 19:00; календарь — alarm_at_time;
заметка — неизвестный route `today`; звонок в доставку — снова alarm_at_time.
Все восемь ответов после уточнения промпта отвергнуты до исполнения.
Автоматическая семантическая оценка — NOT_EVALUATED, поскольку JSON-контракт
не пройден; перечисленные ошибки смысла видны непосредственно в сыром тексте.
Снятие Markdown-обёртки не устранит их или нарушения схемы.

Время ответа: 14,151–23,559 с для этих восьми случаев. Prefill + decode
не превышает 820 токенов при контексте 1024: исчерпание контекста не наблюдалось.
Каждый запрос использовал новую Conversation. G4 и Android-executors в этих
диагностических прогонах не запускались; физическое выполнение не проверено.
JSONL сохранены в `app/build/reports/live-routing/` под именами прогонов.

Полный повтор `:app:testDebugUnitTest` после последнего изменения:
225 тестов, 0 failures/errors. Обе сборки успешны. Независимый review ранее
нашёл потерю title-фактов в оценщике календаря; fragments теперь проверяются
только после inference, не передаются модели. Commit/push нет.

## Продолжение диагностики

Изменения только в тестах: общий проверенный путь файла/SHA/cache,
контроль двух языков, выбор sampler, отдельные варианты контракта.
Новый тестовый APK: `0CAD95EC52A40A60A50109393976F0C12DEC312CBEF8212BA517CDA9B0AD8AEA`.
Основной APK и рабочая маршрутизация не изменились.

| Прогон | Контроль | Результат |
| --- | --- | --- |
| `sampler3-v6` | Тот же русский JSON; topK=20, topP=0.8, temperature=0.7 | 0/3, лишние поля и неверный tool будильника |
| `basic4-v7` | Вопрос о столице Франции без tools, EN/RU × два sampler | EN 2/2 Paris; RU 0/2: Мюнхен/Париаге |
| `contracts8-v8` | Английский JSON и полные native schemas, контекст 1024 | JSON 0/4; native 4/4 не дошли до decode: вход 1163–1172 токена |
| `native3-v9` | Полные native schemas, диагностический контекст 2048 | 0/3: объяснения вместо вызовов, 15,643–27,704 с |
| `envelope3-v10` | Одна native-функция route_request, контекст 1024 | 0/3: неизвестные функции или обычный текст |
| `prefill6-v12` | Исправленный английский контракт, без/с assistant-prefix | 0/6: неполное решение или неверные аргументы, 3,595–5,397 с |
| `gpu-init-v13` | Тот же файл на GPU | BLOCKED: ошибка Engine.initialize, 0 ответов |

Полные имена JSONL начинаются с `spec001b-qwen-`.
В v8 прежний оценщик записал нехватку контекста как FAIL; это **BLOCKED до
генерации**, не четыре плохих ответа Qwen. Теперь INFERENCE/METRICS ошибки
отделены от VALIDATION, неожиданный сбой прерывает suite как incomplete.
GPU-ошибка сохранена отдельным engine_error; failed initialize не вызывает
запрещённый SDK close. В native SDK возможна потеря повторных JSON-ключей;
эти прогоны не доказывают строгую валидность исходного native JSON.

Английский v8 содержал CODE-заполнитель, который модель копировала.
Заполнитель убран для v12: повторный тест всё равно не прошёл.
Prefix задаёт только `{"route":"`; значение маршрута генерирует Qwen.
Исходный render проходит byte-equality проверку identity-replay;
prefix и настоящий rawDecode сохраняются отдельно. Repair ответа не добавлен.

Автоматическая грамматика из tools для Qwen в SDK 0.13.1 не реализована:
[Qwen processor](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.13.1/runtime/conversation/model_data_processor/qwen3_data_processor.h)
не переопределяет CreateConstraint; [базовый processor](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.13.1/runtime/conversation/model_data_processor/model_data_processor.h)
возвращает Unimplemented, а [Conversation](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.13.1/runtime/conversation/conversation.cc#L259)
пропускает этот результат. Переключение общего флага не гарантирует формат.

Независимый review последнего изменения: ложного PASS, потери отмены или
исполнения вне policy не найдено. Повтор unit-пакета: 225, 0 failures/errors;
main/test APK собираются, git diff --check пройден. Android-действий в этих
прогонах нет; UI/voice, рабочая папка, FG/G4 не менялись. Commit/push нет.

Следующий предлагаемый контроль — отдельный dynamic INT8 экспорт того же Qwen:
`Qwen3-0.6B.litertlm`, 614236160 байт,
SHA `555579ff2f4fd13379abe69c1c3ab5200f7338bc92471557f1d6614a6e5ab0b4`,
та же pinned revision. [Описание экспортов](https://huggingface.co/litert-community/Qwen3-0.6B#conversion-notes).
По явному согласию пользователя файл скачан отдельно; размер и SHA подтверждены
на компьютере и телефоне. Mixed INT4, FG/G4 и рабочая маршрутизация сохранены.

## Удержание экрана и повтор для наблюдения

По просьбе пользователя на телефоне включено удержание экрана при USB-зарядке:
`stay_on_while_plugged_in`: исходное 0, тестовое 2; dumpsys подтвердил mStayOn=true.
Это настройка устройства, не изменение UI приложения. На время дальнейших
тестов оставлена 2; после тестирования вернуть 0. Исходное значение сохранено
в `app/build/reports/live-routing/device-screen-setting.json`.

`spec001b-qwen-visible2-v14`, тот же реальный mixed INT4, CPU/1024,
structured_english, рекомендованный non-thinking sampler:

- «Включи фонарик»: `{"route":"FAST_TOOL"}`, 5317 мс — нет tool/arguments.
- «Поставь будильник на 07:30»: `{"route":"FAST_TOOL","tool":"BUDIEN","arguments":{"time":"7:30"}}`,
  6300 мс — неизвестное имя инструмента.

Оба FAIL до исполнения; 2/2 ответа сохранены, Android-действий нет.
Ответы и причины отказа показаны пользователю в обновлениях работы.

## INT8: metadata и реальная проверка адаптера

В локальном INT8 LlmMetadata занимает 152 байта с offset 16384, поля только 1–5:
нет model type (6), Jinja (7), channels (8). SDK определяет этот файл как Generic:
[type inference](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.13.1/runtime/util/model_type_utils.cc#L83),
[Generic processor](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.13.1/runtime/conversation/model_data_processor/generic_data_processor.cc#L51).
Встроенный basic-template не публикует tools и игнорирует enable_thinking.
Generic processor также не выдаёт SDK toolCalls.

В тестах исправлена граница: эталонный
[Qwen Jinja SDK 0.13.1](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.13.1/runtime/components/testdata/Qwen-Qwen3-0.6B.jinja)
публикует реальные схемы; raw Hermes проходит `parseHermesResponse` до DTO.
Один блок, известное имя, объект arguments; лишний текст, два вызова,
повторные ключи и «ремонт» ответа не допускаются. Вход без опубликованных
сигнатур блокируется до inference. Temporary template override — только тесты,
последовательное создание Conversations, восстановление флага в finally.

| Прогон (`spec001b-qwen-` + имя) | Результат |
| --- | --- |
| `int8-basic4-v15` | Окончательные ответы фактически Paris/Париж, но 65–97 decode-токенов с thinking; короткий формат 0/4, 16,930–24,048 с |
| `int8-nothink4-v16` | Без thinking: EN 2/2; RU 0/2 (лишняя фраза/Мюнхен), 2,729–6,046 с |
| `int8-torch6-v17` | Собственный JSON, два системных промпта: 1/6 полный PASS; «Зажги фонарь», 18,252 с. Остальные — неверное действие/схема/маршрут |
| `int8-native3-v18` | Старый metadata-путь не передал каталог; 127–128 prefill. Исторические FAIL не считать оценкой модели: интеграция BLOCKED |
| `int8-hermes3-v19` | Исправленный вход, 1163–1164 prefill; 0/3: голые коды вместо Hermes, 22,903–23,185 с |
| `int8-thinking1-v20` | Official template + SDK thinking channel + рекомендованный sampler: корректный Hermes, но torch_toggle вместо torch_on; FAIL, 58,217 с, 172 decode-токена |
| `int8-declaration-guard-v21` | Повтор старого неполного входа: BLOCKED/DECLARATION_RENDER до decode, suite incomplete. Ожидаемый отказ защиты, не ответ модели |

Это разные конфигурации, не одна агрегированная accuracy. Правильность формата
и даже schema PASS не доказывают правильность действия. Ошибка интеграции INT8
установлена и обойдена явным тестовым адаптером; после этого модель всё ещё
не прошла первый gate. Быстрый режим ненадёжен; thinking не дал корректного
результата и превысил приемлемое время атомарного действия.

Полный unit-пакет после parser-а: 227 тестов, 0 failures/errors; обе сборки
успешны. Независимый review узкой границы — PASS. Рабочий runtime не переключён,
Android-executors не вызывались. Дальнейшее расширение корпуса/физические
действия не имеют основания до устойчивого ON/unseen/OFF.
Последние APK: main `6E6E2CE510AD1CF618F652EA7E09596AE67B9CD0EAF95A5A3F938E1A55808E8F`,
test `49D92F454DBB4C22C44AF938973705B8E9C495DAC357219B331262EBDC1F8C0B`.
Установлены с сохранением данных. После прогонов снова открыт OpenKsenax;
USB stay-on оставлен включённым по просьбе пользователя. Commit/push нет.

**LIVE VALIDATION FAILED — NOT READY FOR USER COMMIT**

Qwen в проверенной конфигурации не удовлетворяет контракту Fast Router.
Отказ наблюдается уже в выводе модели, не только в Android-интеграции.
Это не доказательство невозможности исправить модель и не разрешение
подменять её эвристиками. Полный корпус, повторы стабильности и физический
путь исполнения отложены до прохождения узкого gate.
