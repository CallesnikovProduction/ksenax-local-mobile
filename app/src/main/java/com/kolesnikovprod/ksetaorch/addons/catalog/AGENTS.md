# Addon Catalog

Версия архитектурного контура: `@since 0.3`.

## Назначение

`addons.catalog` предоставляет OpenKsenax список аддонов,
доступных для установки.

Каталог может загружаться из настроенного внешнего JSON-реестра,
проверяется, преобразуется во внутренние модели и кэшируется.
До явной настройки endpoint удалённый источник отсутствует: контур
может прочитать валидный кэш, но не подставляет фиктивный URL.

## Ответственность

Модуль отвечает за:

- загрузку registry JSON;
- файловый кэш;
- проверку структуры и обязательных полей;
- преобразование внешнего JSON во внутренние модели;
- выбор между сетью и кэшем.
- валидацию optional-пары `presentation.bannerUrl` +
  `presentation.bannerSha256`.

## Не отвечает за

Модуль не отвечает за:

- проверку установленных APK;
- PackageManager;
- установку и обновление APK;
- Binder/AIDL;
- запуск аддонов;
- состояние foreground service;
- доверие к уже установленному APK;
- UI.

Эти обязанности принадлежат `addon.registry`,
`addon.discovery` и `addon.coordination`.

## Поток данных

Configured HTTPS registry
↓
KtorAddonCatalogRemoteSource
↓
AddonCatalogDecoder
↓
AddonCatalogValidator
↓
AddonCatalogMapper
↓
AddonCatalogSnapshot
↓
AddonRegistry

## Главная граница

`AddonCatalogEntry` описывает аддон, опубликованный в экосистеме.

Он не описывает фактически установленный APK.

## HTTP ownership

`KtorAddonCatalogRemoteSource` получает общий `HttpClient` из корневой
композиции и не закрывает его. Engine, таймауты и redirect policy принадлежат
корневой композиции. Catalog задаёт только HTTPS endpoint, request headers,
допустимые HTTP-статусы и максимальный размер ответа.

Transport failure и отклонённый remote response различаются: DNS/timeout/
connection failure означает `NetworkUnavailable`, а HTTP error, unsafe
redirect, oversized body или невалидный UTF-8 — `RemoteResponseRejected`.
Presentation может показывать «Нет подключения к Интернету» только для первой
категории.

Catalog не скачивает banner asset. Он только валидирует и передаёт descriptor.
Загрузкой, SHA-256 и проверкой `1920x576` занимается `addons.banner`.
