# Model validation overlay

## Назначение

Пакет содержит самостоятельный presentation-модуль оверлея проверки локальной
модели. Его публичная поверхность:

```text
KsenaxModelVerificationOverlay
KsenaxModelVerificationOverlayHost
KsenaxModelVerificationUiState
KsenaxModelVerificationStatus
```

Модуль рисует три стадии:

```text
наличие -> целостность -> достижимость
```

Он не запускает файловую проверку, не создаёт model engine, не хранит
foreground-session и не выполняет навигацию.

## Граница

Правильный поток:

```text
application state
    -> adapter в вызывающем presentation-контуре
    -> KsenaxModelVerificationUiState
    -> KsenaxModelVerificationOverlay
```

Оверлей не должен импортировать chat ViewModel, `KsenaxBasicModelGateState`,
download use case, model session, `NavController` или Android lifecycle.

Текущий chat-адаптер находится в:

```text
ui/main/chat/KsenaxModelVerificationUiMapper.kt
```

Файловая проверка и foreground-кэш находятся отдельно:

```text
ui/controllers/modelvalidation
```

## Правила изменений

- сохранять UI-контракт независимым от конкретного чата;
- на failure останавливать все бесконечные анимации;
- единственное интерактивное действие внутри карточки — `onCancel`;
- нажатие вне карточки сворачивает только presentation через `onMinimize`;
- сворачивание не отменяет и не перезапускает validation job;
- не добавлять в пакет filesystem, checksum, engine или navigation-логику;
- новые публичные контракты документировать с `@since 0.3`.
