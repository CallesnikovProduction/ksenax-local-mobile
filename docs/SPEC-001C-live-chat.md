# Live-проверка Chat/Temporaric после SPEC-001C

@author Stephan Kolesnikov
@since 0.4

9 октября 2026. Дополнительная проверка на устройстве явно разрешена пользователем;
исходная SPEC-001C остаётся кодовым аудитом. Agentic/Qwen/STT не проверялись.

## Сборка и метод

- HONOR ELI-NX9, serial AJ4UVB4611033107; OpenKsenax 0.4/code 4,
  обновление 2026-10-09 09:51:16. Использована установленная IDEA-сборка.
- Все 23 DEX установленного APK совпали с локальным APK после C.
  SHA APK целиком различается: installed
  `20d053ac5128e01a4aa31342f2fe65299597f4ce269fa3952b42db311e4e03e1`,
  local `3bed51f5dedfd61101dace92033685f90e13550b37b33d92d2651aad705dedba`.
  Совпадение кода проверено отдельно; равенство всех ресурсов не утверждается.
- Настоящий UI, настоящая Gemma 4 E2B; без mock-ответов, build/install/pm clear.
  Создан один тестовый Basic-чат LIVE_C_1009_A; пользовательские чаты не открывались.
  Время ниже — показанная приложением длительность генерации, не полный startup.

## Результаты

| Сценарий | Наблюдение | Итог |
| --- | --- | --- |
| Basic: первый запрос | Remember cedar / Reply only READY → READY, 2,1 с; все три стадии MODEL VERIFICATION пройдены | PASS |
| Basic: следующий turn | Вопрос о запомненном слове → cedar, 10 с | PASS |
| Basic: Stop после delta | Генерация 80 предложений остановлена на середине 38-го; partial 3342 символа, 117 с; ошибки нет | PASS |
| Basic: запрос после Stop | LIVE_C_1009_C. Reply only OK. → OK, 11 с | PASS |
| Basic: завершение процесса/новый запуск | PID 8687 → 14786; чат найден в панели, OK и partial восстановлены. Текст partial посимвольно равен снимку до restart (ordinal comparison) | PASS |
| Temporaric: первый turn | LIVE_C_1009_T1. Reply only PINE. → PINE, 1,7 с; gate пройден | PASS |
| Temporaric: новый turn без истории | Recall my previous message, or reply UNKNOWN → UNKNOWN, 1,4 с; PINE остаётся только в видимой переписке | PASS |
| Temporaric: Stop | Count from 1 to 500 → остановлено на 46, partial в RAM, 25 с | PASS |
| Temporaric: повтор после Stop | LIVE_C_T3. Reply only DONE. → DONE, 1,4 с | PASS |
| Temporaric: New Chat | Старые turn-ы очищены; LIVE_C_TRESET. Reply only NEW. → NEW, 1,7 с; в новом UI только одна пара сообщений | PASS |
| Temporaric: пустая отправка | Та же одна пара; нет verification/awaiting и новых сообщений | PASS |
| Постоянная панель после временных turn-ов | Только тестовый Basic, нет Temporaric-записей | PASS |
| Завершение процесса из Temporaric | Новый PID 23866, main без временных сообщений; Basic сохранился в панели | PASS |

## Доказательства и ограничения

Снимки иерархии и экрана: `app/build/reports/live-chat-20261009/00–22`.
Это наблюдение настоящих UI/модельных ответов и сохранения Basic после уничтожения
процесса, не полный стресс-тест всех interleavings. Проверка изоляции — один
black-box пример плюс ранее проверенный кодовый контракт streamEphemeral.
Прямой SQL-аудит и системный low-memory kill не выполнялись. Холодный запуск
проверен через force-stop без удаления данных; это не гарантия автоматического
восстановления navigation back stack после любой причины process recreation.

Быстрый ADB text-input терял буквы. Последующие строки вводились с паузами и
сверялись до Send. Stop-запрос Basic фактически имел текст
`LIVE_C_1009_STOP. Write 80 nubred sentences about different trees. Do no stop early.`;
он годен для проверки отмены, но не засчитывается как тест точности ввода.
Старый XML после ошибочного UI dump не использовался для решений;
дальнейшие снимки имеют уникальные имена и проверку успешности dump.

Обычный shell Codex не запускался из-за sandbox setup error; ADB выполнялся
через разрешённый elevated shell. Один approval timeout произошёл до выполнения
команды, после чего отдельная команда успешно повторена. Это не ошибки приложения.

Код приложения/модели/настройки не менялись, новые сборки не выполнялись.
USB stay-on=2 сохранён, устройство оставлено на тестовом Basic-чате.
Тестовый чат сохранён как доказательство; чужие записи не удалялись.
Commit/push нет. Старые 247 JVM-тестов и lint-ограничение относятся к кодовому
отчёту SPEC-001C, в этом live-прогоне они не запускались повторно.

LIVE CHAT/TEMPORARIC CHECK PASSED — READY FOR USER REVIEW
