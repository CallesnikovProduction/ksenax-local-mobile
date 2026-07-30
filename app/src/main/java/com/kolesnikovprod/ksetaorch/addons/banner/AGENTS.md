# Addon presentation banners

> `@since 0.3`

Этот package загружает и проверяет внешние баннеры, опубликованные в
`stable.json`.

Инварианты:

- URL и SHA-256 приходят только через валидированный catalog и registry;
- transport выполняет общий `KtorAddonRemoteFileDownloader`;
- результат передаётся UI только после SHA-256 и bitmap decode;
- допустим только точный размер `1920x576`;
- доступные баннеры лежат в `addons/temp`, установленные — рядом с локальной
  проекцией аддона; оба файла индексируются SHA-256 и проверяются при чтении;
- отсутствие, ошибка или несовместимое изображение означает безопасный
  presentation fallback, а не ошибку всего addon registry.

Presentation не обращается напрямую к Ktor, URL или filesystem.
