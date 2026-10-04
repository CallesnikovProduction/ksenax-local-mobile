# Agentic work

## Назначение

`communication.work` выполняет один независимый Agentic-запрос. Модельные
сессии принадлежат `communication.model`, Android-действия — `tools/builtin`.

```text
UP -> FunctionGemma native selection
   -> fast: известный код -> FG аргументы (если нужны) -> schema/domain -> policy -> executor
   -> planned: G4 JSON plan -> preflight всех входов и FG calls -> policy -> executors по порядку
   -> clarification / unsupported: существующий результат turn, без side effects
```

## Инварианты

- FunctionGemma — единственный модельный выход к executor-у.
- Keywords не участвуют в runtime-маршрутизации. `supportsFastPath` публикует
  функции kit-а в семантическом каталоге; их выбирает FG.
- G4 видит кодовые имена и входные контракты, но не Android-реализации.
- Первый FG-вызов выбирает код без параметров. Только параметризованная функция
  получает второй FG-вызов со своей схемой. Так не раздувается контекст 1024.
- UP больше 256 UTF-8 байт направляется FG-функцией `route_planned_work` в G4;
  исходный текст не обрезается и не исполняется по одному фрагменту.
- Каждый шаг плана содержит одну FunctionGemma declaration. Native functions
  используют `askFunctions`, не ручной chat-template и не automatic tool calling.
- Аргументы G4 или локального нормализатора передаются executor-у отдельно от
  короткого FunctionGemma prompt, если kit отключил их публикацию.
- Шаги выполняются по порядку; первая ошибка, блокировка или подтверждение
  останавливает план.
- Все входы и ответы FG проверяются до первого executor. Hardware-операции не
  транзакционны: ошибку позднего исполнения нельзя откатить автоматически.
- Неясные числа/время означают уточнение. Составной интервал пока уточняется,
  не сокращается до первой величины. Clock ограничен ближайшими 24 часами.
- Календарь использует canonical local datetime; delay/all_day не входят в этот
  атомарный контракт. Экран сохранения события по-прежнему принадлежит Android.
- Заметка требует типизированного текста G4; отсутствующий текст не заменяется UP.
- Mutex сериализует turns одного runtime. Отмена пробрасывается, а diagnostic
  events содержат только стадию, имя функции, latency и категорию ошибки.
- Agentic-turn не использует persistent conversation.

## Папки

- `routing` — семантический выбор функции или планирования/уточнения/отказа;
- `planning` — компактный G4 prompt, DTO и строгий JSON-parser;
- `oneshot` — FunctionGemma template, declarations и parser ответа;
- `actions` — внешний контракт action kit и прямого маршрута;
- `runtime` — порядок моделей, policy и executor-ов;
- `turn` — внешний результат и стадии одного запроса.

Новые публичные типы имеют префикс `Ksenax`, краткий KDoc,
`@author Stephan Kolesnikov` и `@since 0.4`.

## Проверка

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kolesnikovprod.ksetaorch.communication.*"
.\gradlew.bat compileDebugKotlin
```
