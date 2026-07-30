# Addon Coordination

> @since 0.3

## Назначение

Coordination выполняет команды пользователя над аддонами:

- запускает подготовку опубликованного APK и передаёт доказанный артефакт
  системному installer;
- запрашивает удаление;
- создаёт системный uninstall request, получает явный Activity-result и
  независимо проверяет через PackageManager, что APK действительно исчез;
- открывает launcher UI установленного APK по registry package name и только
  при его отсутствии использует addon-owned `PendingIntent`;
- включает и выключает runtime;
- читает runtime status.

## Правило безопасности

Coordinator не принимает собственных решений о trust
и compatibility.

Он использует только RegisteredAddon из AddonRegistry.

Binder connector принимает только registry-owned `AddonManagementEndpoint`.
Coordination не импортирует `AddonCatalogEntry` или `DiscoveredAddon`.

Удаление не является частью публичного addon AIDL: APK не должен разрешать или
симулировать собственное удаление. Android package sandbox удаляет системный
uninstaller, а host-owned копии APK, banner и metadata очищает `storage` только
после подтверждённого отсутствия package.

## Не делает

- не реализует HTTP и PackageManager-проверки внутри coordinator: для этого
  используется порт `AddonArtifactPreparer`;
- не читает GitHub;
- не сканирует PackageManager;
- не выполняет model inference;
- не содержит бизнес-логику конкретного аддона.

## UI effects

Если Android требует разрешить установку из неизвестного источника,
coordination возвращает settings `Intent` как явный UI effect. Он не теряется
в installer-слое и не запускается без возможности показать объяснение.
