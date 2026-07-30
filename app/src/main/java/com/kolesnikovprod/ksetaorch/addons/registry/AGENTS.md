# Addon Registry

> @since 0.3

## Назначение

Registry объединяет удалённый каталог и локальный
PackageManager discovery.

Он является единственным местом, где определяется:

- установлен ли аддон;
- доступно ли обновление;
- совпадает ли сертификат;
- совместим ли аддон с текущим OKx;
- разрешено ли подключаться к его service;
- разрешено ли ему использовать host capabilities.

## Registry не делает

- HTTP-загрузку каталога;
- PackageManager scanning;
- установку APK;
- Binder connection;
- запуск UI;
- model inference.

## Формула

RegisteredAddon =
AddonCatalogEntry?
+ DiscoveredAddon?
+ HostEnvironment

## Public boundary

`RegisteredAddon` не отдаёт наружу source-модели catalog/discovery. UI,
coordination и modelprovider получают registry-owned metadata и обнаруженный
`AddonManagementEndpoint`.

Разрешённые host capabilities вычисляются один раз как структурная
совместимость:

`catalog required ∩ manifest required ∩ host implemented`

Несовпадение catalog/manifest является incompatibility. После неуспешного
remote refresh последний ранее проверенный catalog snapshot остаётся пригодным
для authorization только до истечения своего trust TTL. Если валидного
snapshot нет, TTL истёк или discovery недоступен, management и provider
authorization закрываются fail-closed; установленные APK при этом остаются
видимыми для UI.

Текущая готовность model runtime не должна отнимать этот grant: Provider
проверяет её отдельно через динамические `availableCapabilities` и перед
inference. Иначе готовность, изменившаяся после registry refresh, оставит
сохранённый snapshot устаревшим.
