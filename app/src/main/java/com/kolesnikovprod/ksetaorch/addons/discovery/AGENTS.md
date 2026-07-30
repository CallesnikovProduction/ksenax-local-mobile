# Addon Discovery

> @since 0.3

## Назначение

`addons.discovery` обнаруживает addon APK,
установленные на текущем Android-устройстве.

Discovery использует PackageManager и публичный
Android Manifest-контракт из `addons.contract`.

## Что делает

- ищет addon services по intent action;
- читает Android Manifest meta-data;
- получает package name;
- получает versionCode/versionName;
- получает signing certificate fingerprints;
- отбрасывает некорректные кандидаты;
- возвращает `AddonDiscoverySnapshot`.

## Что не делает

Discovery не:

- загружает GitHub-каталог;
- устанавливает APK;
- определяет наличие обновления;
- принимает решение о доверии;
- определяет совместимость с OKx;
- подключается к Binder service;
- запускает аддон;
- получает runtime status.

## Главная модель

`DiscoveredAddon` означает:

"На устройстве установлен APK, который объявил
себя аддоном OpenKsenax."

Это не означает:

"OKx доверяет этому APK и разрешает его запуск."

## Зависимости

discovery -> contract

Discovery не зависит от catalog.

Будущий registry будет зависеть и от catalog,
и от discovery.

## Инварианты реализации

- PackageManager и hashing выполняются вне main thread;
- `CancellationException` никогда не превращается в rejected candidate;
- числовые manifest meta-data проверяются до сужения `Long -> Int`;
- locale-sensitive нормализация использует `Locale.ROOT`;
- composition выполняется единым addon graph приложения, а не локальным factory.
