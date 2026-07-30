# Addon storage

`storage` владеет только app-specific копиями файлов и метаданных. Он не
считает APK установленным по наличию скачанного файла: источником истины
является `registry`, построенный на Android `PackageManager`.

- `addons/temp` содержит проверяемые download-кандидаты и баннеры каталога.
- `addons/<package-leaf>` создаётся только для реально обнаруженного APK.
- `installedAtEpochMillis` берётся из `PackageInfo.firstInstallTime`.
- Удаление каталога разрешено только после проверки, что путь принадлежит
  корню addon storage.
- Явный `remove(addonId, packageName)` удаляет package-каталог и только
  принадлежащие этому AddonId временные артефакты. Узкая совместимость также
  чистит старые download-файлы формата `addons/<addonId>-<version>.apk`,
  не затрагивая package-каталоги и соседние аддоны. Метод вызывается после
  того, как coordination доказал отсутствие APK через Android PackageManager.
- JSON-файл является локальной UI-проекцией, но не trust-источником.

Новые публичные архитектурные типы документируются с `@since 0.3`.
