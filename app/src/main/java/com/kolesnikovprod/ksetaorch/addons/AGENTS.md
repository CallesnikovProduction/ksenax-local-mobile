# Addon infrastructure

## Назначение

Этот package содержит host-инфраструктуру автономных addon APK OpenKsenax 0.3.
Аддон работает отдельным Android-приложением; OKx не загружает его DEX в свой
процесс и не отображает переданные им Compose-классы.

## Контуры и зависимости

```text
contract
  ↑
  ├── catalog ─────┐
  ├── discovery ───┼── registry ─── coordination
  ├── registry ────┘       ├─────── modelprovider
  ├── download ────────────┘
  ├── coordination
  └── modelprovider

remote ───┬── download
          └── banner

registry + coordination + banner ─── presentation
registry ─── storage
```

Правила направлений:

- `contract` не импортирует остальные addon-contours;
- `catalog` не знает о PackageManager, installed APK, registry и UI;
- `discovery` не знает о remote catalog, trust, registry и UI;
- `registry` объединяет catalog, discovery и host environment, но не запускает
  installer, Binder-команды или inference;
- `coordination` работает с registry-owned endpoint и verdict, а не с raw
  discovery/catalog DTO;
- `modelprovider` проверяет Binder caller через registry и не зависит от
  coordination;
- `presentation` читает registry/coordinator и не обращается напрямую к HTTP,
  JSON, filesystem или PackageManager.
- `remote` предоставляет один общий Ktor file-transfer механизм;
- `banner` накладывает SHA-256 и bitmap-dimensions verification поверх
  `remote`, после чего отдаёт presentation только проверенный bitmap.
- `storage` материализует только PackageManager-подтверждённые установки в
  `files/addons/<package-leaf>` и не принимает download за установку.

Общая composition root остаётся в `KsenaxAndroidApplication`. Внутри этого
package допустим один process-owned `OpenKsenaxAddonGraph`, но не глобальные
mutable singleton и не отдельные factory для каждого контура.

## Trust и capabilities

Manifest установленного APK является заявлением, а не источником доверия.
Registry вычисляет структурный grant один раз:

```text
granted capabilities = catalog declaration
                     ∩ installed manifest declaration
                     ∩ capabilities, реализованные текущим host
```

Несовпадение catalog и manifest, неизвестный сертификат, неподдерживаемый
protocol/API либо отсутствие catalog snapshot в пределах trust TTL должны
давать fail-closed состояние. Ошибка нового remote refresh не отзывает ещё
действующий проверенный snapshot до истечения TTL. Coordinator и Model Provider
используют готовое решение и не копируют certificate/trust/compatibility
проверки.

Временная runtime-готовность модели не входит в структурный registry grant:
иначе аддон, обнаруженный до установки модели, останется без capability до
полного refresh registry. Model Provider отдельно проверяет динамический
readiness при выдаче available capabilities и повторно перед inference.

## Сеть и установка

Ktor-клиент принадлежит addon graph и переиспользуется process-wide. Catalog
кэширует исходный JSON только после успешных decode + validation. URL registry
задаётся конфигурацией; production URL не должен быть скрытым default.

APK flow разделён на Ktor downloader, Android archive verifier и system installer.
Installer получает только проверенный файл, использует существующий
`FileProvider` и всегда оставляет финальное подтверждение Android-пользователю.

## IPC и Model Provider

AIDL/Parcelable — публичная межпроцессная ABI-граница. Канонические `.aidl`
файлы должны лежать в Android AIDL source set. Provider:

- фиксирует `Binder.getCallingUid()` до ухода в coroutine;
- сопоставляет UID с package и registry entry;
- разрешает только `grantedHostCapabilities`;
- ограничивает очередь и размер Binder payload;
- корректно обрабатывает cancellation и смерть callback;
- использует существующую process-level model session, не создавая второй
  LiteRT engine.

Публичная ABI живёт в `:addon-contract` и публикуется целиком как один AAR.
Внутренние registry/download/UI-типы в этот артефакт не входят.

## Документация и проверки

Учебная карта классов, границ и end-to-end потоков находится в
[`notes/ADDON-ARCHITECTURE-GUIDE.md`](notes/ADDON-ARCHITECTURE-GUIDE.md).
Контракт внешней шапки `stable.json` описан в
[`notes/BANNER-SPECIFICATION.md`](notes/BANNER-SPECIFICATION.md).
Граница download/system installer/PackageManager описана в
[`notes/INSTALLATION-LIFECYCLE.md`](notes/INSTALLATION-LIFECYCLE.md).

Новые public-типы и стабильные wire-контракты получают KDoc с `@since 0.4`.
Минимальные проверки: catalog validation/fallback, registry merge/trust/grants,
discovery metadata parsing, coordinator decisions и provider request/auth rules.
