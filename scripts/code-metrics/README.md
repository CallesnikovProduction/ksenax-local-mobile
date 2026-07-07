# Code Metrics

Личная Kotlin-утилита для подсчета строк кода в проекте. Она не подключена к Android-приложению и не входит в `settings.gradle.kts` корневого проекта.

## Запуск

Из корня репозитория:

```powershell
.\gradlew.bat -q -p scripts\code-metrics run --args="..\.."
```

С детализацией по файлам:

```powershell
.\gradlew.bat -q -p scripts\code-metrics run --args="..\.. --by-file"
```

Markdown-таблица:

```powershell
.\gradlew.bat -q -p scripts\code-metrics run --args="..\.. --format=markdown"
```

CSV:

```powershell
.\gradlew.bat -q -p scripts\code-metrics run --args="..\.. --format=csv"
```

## Что считается

- `total`: все физические строки.
- `blank`: пустые строки.
- `engaged`: все непустые строки: код, документация, импорты, package, комментарии.
- `documentation`: строки KDoc/Javadoc вида `/** ... */`.
- `comments`: обычные комментарии `//` и `/* ... */`, без KDoc/Javadoc.
- `package`: строки `package ...`.
- `imports`: строки `import ...`.
- `usefulCode`: непустой код без package/import/comment-only/doc-only строк.
- `declarations`: примерное число деклараций `class`, `object`, `interface`, `fun`, `val`, `var` и Java-методов/полей.
- `documentedDeclarations`: декларации, перед которыми стоит KDoc/Javadoc.
- `documentedCode`: полезные строки кода внутри деклараций, перед которыми стоит KDoc/Javadoc. Это приближение, а не полноценный Kotlin AST.

По умолчанию сканируются файлы `kt`, `kts`, `java`. Исключаются `.git`, `.gradle`, `.idea`, `.kotlin`, `.codex`, `.agents`, `build`, `out`.

## Опции

```text
CodeMetrics [path] [--by-file] [--format=text|markdown|csv] [--ext=kt,kts,java] [--exclude=build,out]
```
