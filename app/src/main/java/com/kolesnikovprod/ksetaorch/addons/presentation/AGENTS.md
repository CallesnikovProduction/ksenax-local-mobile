# Addon presentation

## Граница

Presentation показывает catalog/registry state и отправляет пользовательские
команды в coordinator. Здесь нельзя использовать `PackageManager`, Ktor,
filesystem, raw registry DTO или Binder stubs.

Рабочий UI автономного аддона не переносится в Compose-дерево OKx. Кнопка
«Открыть» просит coordinator запустить стандартную launcher Activity addon APK
по package name. Если аддон является headless-приложением без launcher Activity,
резервным entry point служит принадлежащий аддону `PendingIntent`.

## Состояние

- `AddonCatalogViewModel` получает process-owned registry/coordinator;
- `AddonUiMapper` переводит только registry verdict в immutable UI models;
- composable-функции не вычисляют trust, compatibility и capability grants;
- banner приходит из registry metadata, асинхронно загружается через
  `AddonBannerRepository` и не является hardcoded drawable конкретного
  аддона;
- при отсутствии или отказе banner asset UI рисует пунктирный fallback
  `1920:576` с package name из registry.

Ручной REFRESH использует `AddonRegistryRefreshMode.FORCE_REMOTE`: он обязан
реально обратиться к настроенному remote registry, а не молча принять cache
fallback за сетевой успех. Установленные карточки остаются видимыми по данным
discovery/последнего валидного snapshot. Общий refresh только обнаруживает
новые записи; проверка обновления отображается как команда конкретной
установленной карточки.

## Установка

Карточка запускает coordinator-команду по `AddonId`, показывает реальные
download/verify стадии и открывает Android system installer только после
успешной проверки артефакта. URL, файлы и PackageManager остаются за пределами
presentation. Повторное нажатие во время download отменяет Job. Карточка
переходит в «Установленные» только после PackageManager-подтверждения.

Информационный overlay читает `InstalledAddonRecord` через `AddonLocalStore`;
сам composable не знает путь и формат JSON.
