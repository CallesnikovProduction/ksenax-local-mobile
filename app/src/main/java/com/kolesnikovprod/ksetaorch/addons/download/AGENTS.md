# Addon download contour

## Назначение

Контур получает только registry-owned `RegisteredAddon`, потоково скачивает
APK общим Ktor client, проверяет размер, SHA-256, Android package identity,
`versionCode`, signer и management metadata, после чего создаёт непрозрачный
`VerifiedAddonApk`.

## Границы

- URL обязан быть HTTPS и принадлежать явно разрешённому release host.
- APK-кандидат загружается только в `files/addons/temp`.
- Частичный файл имеет суффикс `.part` и удаляется при ошибке или отмене.
- Системный installer не принимает обычный `File`.
- Verifier не принимает trust-решения за registry и не выдаёт capabilities.
- Проверка установленного package всё равно повторяется discovery/registry
  после завершения системной установки.

Низкоуровневый Ktor file transfer вынесен в `addons.remote` и совместно
используется APK и presentation-баннерами. `download` остаётся владельцем
только APK-специализации и Android archive verification.

Новая публичная документация этого контура относится к OpenKsenax 0.4 и
содержит `@since 0.4`.
