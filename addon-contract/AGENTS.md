# OpenKsenax Add-on Contract

`@since 0.3`

Этот Gradle-модуль является единственной публикуемой Android ABI-границей
между OpenKsenax и автономными addon APK.

## Содержимое

- стабильные identifiers, manifest actions и version constants;
- management AIDL и Parcelable wire-типы;
- Model Provider AIDL и Parcelable wire-типы.

## Запреты

Модуль не зависит от `:app` и не содержит registry, PackageManager, Ktor,
downloader, installer, Compose, model runtime или код конкретного аддона.
Публичные package names, AIDL descriptors и строковые значения нельзя менять
после выпуска без новой версии protocol/API.

Артефакт публикуется как
`dev.openksenax:openksenax-addon-contract:0.3.0`. Потребитель подключает AAR
через `implementation`, потому что Binder и Parcelable классы нужны в его APK.
