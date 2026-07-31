# Подпись релизов OpenKsenax

`@since 0.3`

## Канонический ключ

- alias: `openksenax-release-v1`;
- алгоритм: RSA, 4096 бит;
- сертификат: SHA-256
  `19fe9425a623098c4f22c7156c19e8d28468e2db467ab6bb6f1f3b0cc22d9714`;
- локальное рекомендуемое расположение:
  `~/.openksenax/signing/openksenax-release-v1.p12`.

Сам keystore, его пароли и `keystore.properties` не входят в Git. Потеря ключа
делает невозможной установку будущих версий поверх уже опубликованного APK,
поэтому keystore и файл свойств нужно хранить в отдельном защищённом бэкапе.

## Локальная конфигурация

Скопировать `keystore.properties.example` в игнорируемый
`keystore.properties` и заполнить четыре значения:

```properties
storeFile=C:/absolute/path/openksenax-release-v1.p12
storePassword=...
keyAlias=openksenax-release-v1
keyPassword=...
```

В CI те же значения можно передать без файла:

- `OKX_RELEASE_STORE_FILE`;
- `OKX_RELEASE_STORE_PASSWORD`;
- `OKX_RELEASE_KEY_ALIAS`;
- `OKX_RELEASE_KEY_PASSWORD`.

Release-задачи завершаются ошибкой, если полная конфигурация подписи
отсутствует. Debug-сборка от неё не зависит.

## Проверка перед публикацией

```powershell
.\gradlew.bat testDebugUnitTest lint assembleRelease
```

После сборки необходимо проверить `app-release.apk` через `apksigner verify
--verbose --print-certs` и убедиться, что SHA-256 сертификата совпадает с
каноническим значением выше. APK и checksum публикуются как GitHub Release
assets и не коммитятся в репозиторий.
