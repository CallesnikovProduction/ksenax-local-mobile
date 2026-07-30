# OpenKsenax Model Provider

## Назначение

Model Provider предоставляет доверенным addon APK
доступ к локальному inference runtime OpenKsenax.

Аддон передаёт запрос и получает результат.
Он не получает model file, Engine или путь к модели.

`supportedCapabilities` описывает стабильную реализацию Provider и участвует в
registry compatibility/grants. `availableCapabilities` является динамическим
readiness-срезом: временно пуст при отсутствующей модели или неподходящей
runtime-конфигурации. Readiness повторно проверяется перед inference; гонка
после authorization возвращается как `PROVIDER_NOT_READY`.

Синхронный readiness API 1 выполняет только быструю fail-closed проверку файла
и контекста. Он не заменяет SHA-256 validation и cold initialization engine.
Для полной диагностики нужен отдельный асинхронный descriptor/preparation
контракт следующей версии Provider API.

## Авторизация

Каждый Binder-вызов проверяется по:

- Binder calling UID;
- package name установленного APK;
- addonId;
- AddonRegistry;
- trust state;
- compatibility;
- declared capabilities.

Нельзя доверять addonId, переданному самим caller.

## Model Provider не делает

- не управляет runtime аддона;
- не открывает UI аддона;
- не устанавливает APK;
- не содержит бизнес-логику NR;
- не решает, когда аддону отвечать пользователю.

## Зависимости

modelprovider -> registry
modelprovider -> model runtime port

Registry не зависит от modelprovider.
