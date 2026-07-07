import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.io.path.readLines

private data class Config(
    val root: Path,
    val extensions: Set<String>,
    val excludedNames: Set<String>,
    val byFile: Boolean,
    val format: OutputFormat,
)

private enum class OutputFormat {
    Text,
    Markdown,
    Csv,
}

private enum class BlockCommentKind {
    Documentation,
    Regular,
}

private data class Metrics(
    val files: Int = 0,
    val total: Int = 0,
    val blank: Int = 0,
    val engaged: Int = 0,
    val documentation: Int = 0,
    val comments: Int = 0,
    val packageLines: Int = 0,
    val imports: Int = 0,
    val usefulCode: Int = 0,
    val declarations: Int = 0,
    val documentedDeclarations: Int = 0,
    val documentedCode: Int = 0,
) {
    operator fun plus(other: Metrics): Metrics =
        Metrics(
            files = files + other.files,
            total = total + other.total,
            blank = blank + other.blank,
            engaged = engaged + other.engaged,
            documentation = documentation + other.documentation,
            comments = comments + other.comments,
            packageLines = packageLines + other.packageLines,
            imports = imports + other.imports,
            usefulCode = usefulCode + other.usefulCode,
            declarations = declarations + other.declarations,
            documentedDeclarations = documentedDeclarations + other.documentedDeclarations,
            documentedCode = documentedCode + other.documentedCode,
        )
}

private data class FileMetrics(
    val path: Path,
    val metrics: Metrics,
)

private data class ParsedLine(
    val code: String,
    val hasDocumentation: Boolean,
    val hasRegularComment: Boolean,
)

fun main(args: Array<String>) {
    val config = parseArgs(args)
    val root = config.root.toAbsolutePath().normalize()

    if (!Files.exists(root)) {
        error("Path does not exist: $root")
    }

    val files = findSourceFiles(root, config)
    val reports = files.map { FileMetrics(root.relativize(it), analyzeFile(it)) }
    val total = reports.fold(Metrics()) { acc, report -> acc + report.metrics }

    when (config.format) {
        OutputFormat.Text -> printTextReport(root, total, reports, config.byFile)
        OutputFormat.Markdown -> printMarkdownReport(root, total, reports, config.byFile)
        OutputFormat.Csv -> printCsvReport(total, reports, config.byFile)
    }
}

private fun parseArgs(args: Array<String>): Config {
    var root = Paths.get(".")
    var extensions = setOf("kt", "kts", "java")
    var excludedNames = setOf(".git", ".gradle", ".idea", ".kotlin", ".codex", ".agents", "build", "out")
    var byFile = false
    var format = OutputFormat.Text

    for (arg in args) {
        when {
            arg == "--help" || arg == "-h" -> {
                printUsage()
                kotlin.system.exitProcess(0)
            }

            arg == "--by-file" -> byFile = true

            arg.startsWith("--format=") -> {
                format = when (val value = arg.substringAfter("=").lowercase()) {
                    "text" -> OutputFormat.Text
                    "markdown", "md" -> OutputFormat.Markdown
                    "csv" -> OutputFormat.Csv
                    else -> error("Unknown format: $value")
                }
            }

            arg.startsWith("--ext=") -> {
                extensions = arg.substringAfter("=")
                    .split(",")
                    .map { it.trim().removePrefix(".").lowercase() }
                    .filter { it.isNotEmpty() }
                    .toSet()
            }

            arg.startsWith("--exclude=") -> {
                excludedNames = excludedNames + arg.substringAfter("=")
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            }

            arg.startsWith("--") -> error("Unknown option: $arg")

            else -> root = Paths.get(arg)
        }
    }

    return Config(
        root = root,
        extensions = extensions,
        excludedNames = excludedNames,
        byFile = byFile,
        format = format,
    )
}

private fun printUsage() {
    println(
        """
        CodeMetrics [path] [--by-file] [--format=text|markdown|csv] [--ext=kt,kts,java] [--exclude=build,out]

        Counts Kotlin/Java source lines:
          documentation            KDoc/Javadoc lines: /** ... */
          comments                 regular // and /* ... */ comments
          usefulCode               non-empty code without package/import/comment-only/doc-only lines
          engaged                  all non-empty lines
          documentedCode           useful code inside declarations preceded by KDoc/Javadoc
        """.trimIndent(),
    )
}

private fun findSourceFiles(root: Path, config: Config): List<Path> =
    Files.walk(root)
        .use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { it.extension.lowercase() in config.extensions }
                .filter { path -> path.none { it.name in config.excludedNames } }
                .sorted()
                .toList()
        }

private fun analyzeFile(path: Path): Metrics {
    val lines = path.readLines()
    var total = 0
    var blank = 0
    var documentation = 0
    var comments = 0
    var packageLines = 0
    var imports = 0
    var usefulCode = 0
    var declarations = 0
    var documentedDeclarations = 0
    var documentedCode = 0

    var blockCommentKind: BlockCommentKind? = null
    var pendingDocumentation = false
    var braceDepth = 0
    val documentedScopeEndDepths = ArrayDeque<Int>()

    for (rawLine in lines) {
        total += 1

        if (rawLine.isBlank()) {
            blank += 1
            continue
        }

        val parsed = parseLine(rawLine, blockCommentKind)
        blockCommentKind = nextBlockCommentKind(rawLine, blockCommentKind)

        if (parsed.hasDocumentation) {
            documentation += 1
        }
        if (parsed.hasRegularComment) {
            comments += 1
        }

        val code = parsed.code.trim()
        if (parsed.hasDocumentation && code.isEmpty() && blockCommentKind == null) {
            pendingDocumentation = true
        }

        if (code.isEmpty()) {
            continue
        }

        val isPackageLine = code.startsWith("package ")
        val isImportLine = code.startsWith("import ")

        when {
            isPackageLine -> packageLines += 1
            isImportLine -> imports += 1
            else -> {
                usefulCode += 1
                if (documentedScopeEndDepths.isNotEmpty()) {
                    documentedCode += 1
                }
            }
        }

        val isDeclaration = !isPackageLine && !isImportLine && looksLikeDeclaration(code)
        if (isDeclaration) {
            declarations += 1
        }

        if (isDeclaration && pendingDocumentation) {
            documentedDeclarations += 1
            if (documentedScopeEndDepths.isEmpty()) {
                documentedCode += 1
            }

            val braceDelta = countBraceDelta(code)
            if (braceDelta > 0 || code.contains("{")) {
                documentedScopeEndDepths.addLast(braceDepth)
            }
        }

        if (!isAnnotationLine(code) && !isDeclaration) {
            pendingDocumentation = false
        }

        braceDepth += countBraceDelta(code)
        while (documentedScopeEndDepths.isNotEmpty() && braceDepth <= documentedScopeEndDepths.last()) {
            documentedScopeEndDepths.removeLast()
        }
    }

    return Metrics(
        files = 1,
        total = total,
        blank = blank,
        engaged = total - blank,
        documentation = documentation,
        comments = comments,
        packageLines = packageLines,
        imports = imports,
        usefulCode = usefulCode,
        declarations = declarations,
        documentedDeclarations = documentedDeclarations,
        documentedCode = documentedCode,
    )
}

private fun parseLine(line: String, initialBlockKind: BlockCommentKind?): ParsedLine {
    val code = StringBuilder()
    var index = 0
    var blockKind = initialBlockKind
    var hasDocumentation = false
    var hasRegularComment = false
    var inString: Char? = null
    var escaped = false

    while (index < line.length) {
        if (blockKind != null) {
            when (blockKind) {
                BlockCommentKind.Documentation -> hasDocumentation = true
                BlockCommentKind.Regular -> hasRegularComment = true
            }

            val end = line.indexOf("*/", startIndex = index)
            if (end == -1) {
                break
            }
            index = end + 2
            blockKind = null
            continue
        }

        val current = line[index]
        val next = line.getOrNull(index + 1)

        if (inString != null) {
            code.append(current)
            when {
                escaped -> escaped = false
                current == '\\' -> escaped = true
                current == inString -> inString = null
            }
            index += 1
            continue
        }

        when {
            current == '"' || current == '\'' -> {
                inString = current
                code.append(current)
                index += 1
            }

            current == '/' && next == '/' -> {
                hasRegularComment = true
                break
            }

            current == '/' && next == '*' -> {
                val kind = if (line.getOrNull(index + 2) == '*') {
                    BlockCommentKind.Documentation
                } else {
                    BlockCommentKind.Regular
                }

                when (kind) {
                    BlockCommentKind.Documentation -> hasDocumentation = true
                    BlockCommentKind.Regular -> hasRegularComment = true
                }

                val end = line.indexOf("*/", startIndex = index + 2)
                if (end == -1) {
                    blockKind = kind
                    break
                }
                index = end + 2
            }

            else -> {
                code.append(current)
                index += 1
            }
        }
    }

    return ParsedLine(
        code = code.toString(),
        hasDocumentation = hasDocumentation,
        hasRegularComment = hasRegularComment,
    )
}

private fun nextBlockCommentKind(line: String, initialBlockKind: BlockCommentKind?): BlockCommentKind? {
    var index = 0
    var blockKind = initialBlockKind
    var inString: Char? = null
    var escaped = false

    while (index < line.length) {
        if (blockKind != null) {
            val end = line.indexOf("*/", startIndex = index)
            if (end == -1) {
                return blockKind
            }
            index = end + 2
            blockKind = null
            continue
        }

        val current = line[index]
        val next = line.getOrNull(index + 1)

        if (inString != null) {
            when {
                escaped -> escaped = false
                current == '\\' -> escaped = true
                current == inString -> inString = null
            }
            index += 1
            continue
        }

        when {
            current == '"' || current == '\'' -> {
                inString = current
                index += 1
            }

            current == '/' && next == '/' -> return null

            current == '/' && next == '*' -> {
                val kind = if (line.getOrNull(index + 2) == '*') {
                    BlockCommentKind.Documentation
                } else {
                    BlockCommentKind.Regular
                }
                val end = line.indexOf("*/", startIndex = index + 2)
                if (end == -1) {
                    return kind
                }
                index = end + 2
            }

            else -> index += 1
        }
    }

    return null
}

private fun looksLikeDeclaration(code: String): Boolean {
    val normalized = code
        .removePrefix("public ")
        .removePrefix("private ")
        .removePrefix("protected ")
        .removePrefix("internal ")
        .removePrefix("open ")
        .removePrefix("abstract ")
        .removePrefix("final ")
        .removePrefix("sealed ")
        .removePrefix("data ")
        .removePrefix("value ")
        .removePrefix("inline ")
        .removePrefix("companion ")
        .trimStart()

    if (normalized.startsWith("class ") ||
        normalized.startsWith("interface ") ||
        normalized.startsWith("object ") ||
        normalized.startsWith("enum class ") ||
        normalized.startsWith("fun ") ||
        normalized.startsWith("suspend fun ") ||
        normalized.startsWith("val ") ||
        normalized.startsWith("var ")
    ) {
        return true
    }

    val javaMethod = Regex("""^[\w<>\[\], ?]+?\s+\w+\s*\([^)]*\)\s*(\{|throws\s+.+\{|;)?$""")
    val javaField = Regex("""^[\w<>\[\], ?]+\s+\w+\s*(=.+)?;${'$'}""")
    return javaMethod.matches(normalized) || javaField.matches(normalized)
}

private fun isAnnotationLine(code: String): Boolean = code.startsWith("@")

private fun countBraceDelta(code: String): Int {
    var delta = 0
    var inString: Char? = null
    var escaped = false

    for (current in code) {
        if (inString != null) {
            when {
                escaped -> escaped = false
                current == '\\' -> escaped = true
                current == inString -> inString = null
            }
            continue
        }

        when (current) {
            '"', '\'' -> inString = current
            '{' -> delta += 1
            '}' -> delta -= 1
        }
    }

    return delta
}

private fun printTextReport(root: Path, total: Metrics, reports: List<FileMetrics>, byFile: Boolean) {
    println("Code metrics for $root")
    println()
    printTextRow("files", total.files)
    printTextRow("total", total.total)
    printTextRow("blank", total.blank)
    printTextRow("engaged", total.engaged)
    printTextRow("documentation", total.documentation)
    printTextRow("comments", total.comments)
    printTextRow("package", total.packageLines)
    printTextRow("imports", total.imports)
    printTextRow("usefulCode", total.usefulCode)
    printTextRow("declarations", total.declarations)
    printTextRow("documentedDeclarations", total.documentedDeclarations)
    printTextRow("documentedCode", total.documentedCode)

    if (byFile) {
        println()
        println("Per file:")
        reports.forEach { report ->
            val metrics = report.metrics
            println(
                "${report.path.pathString}: usefulCode=${metrics.usefulCode}, " +
                    "documentation=${metrics.documentation}, comments=${metrics.comments}, " +
                    "imports=${metrics.imports}, engaged=${metrics.engaged}, documentedCode=${metrics.documentedCode}",
            )
        }
    }
}

private fun printTextRow(name: String, value: Int) {
    println(name.padEnd(24) + value)
}

private fun printMarkdownReport(root: Path, total: Metrics, reports: List<FileMetrics>, byFile: Boolean) {
    println("# Code metrics")
    println()
    println("Root: `${root.pathString}`")
    println()
    println("| metric | value |")
    println("| --- | ---: |")
    printMarkdownRow("files", total.files)
    printMarkdownRow("total", total.total)
    printMarkdownRow("blank", total.blank)
    printMarkdownRow("engaged", total.engaged)
    printMarkdownRow("documentation", total.documentation)
    printMarkdownRow("comments", total.comments)
    printMarkdownRow("package", total.packageLines)
    printMarkdownRow("imports", total.imports)
    printMarkdownRow("usefulCode", total.usefulCode)
    printMarkdownRow("declarations", total.declarations)
    printMarkdownRow("documentedDeclarations", total.documentedDeclarations)
    printMarkdownRow("documentedCode", total.documentedCode)

    if (byFile) {
        println()
        println("| file | usefulCode | documentation | comments | imports | engaged | documentedCode |")
        println("| --- | ---: | ---: | ---: | ---: | ---: | ---: |")
        reports.forEach { report ->
            val metrics = report.metrics
            println(
                "| `${report.path.pathString}` | ${metrics.usefulCode} | ${metrics.documentation} | " +
                    "${metrics.comments} | ${metrics.imports} | ${metrics.engaged} | ${metrics.documentedCode} |",
            )
        }
    }
}

private fun printMarkdownRow(name: String, value: Int) {
    println("| `$name` | $value |")
}

private fun printCsvReport(total: Metrics, reports: List<FileMetrics>, byFile: Boolean) {
    println("path,files,total,blank,engaged,documentation,comments,package,imports,usefulCode,declarations,documentedDeclarations,documentedCode")
    println(csvRow("TOTAL", total))

    if (byFile) {
        reports.forEach { report ->
            println(csvRow(report.path.pathString, report.metrics))
        }
    }
}

private fun csvRow(path: String, metrics: Metrics): String =
    listOf(
        path,
        metrics.files,
        metrics.total,
        metrics.blank,
        metrics.engaged,
        metrics.documentation,
        metrics.comments,
        metrics.packageLines,
        metrics.imports,
        metrics.usefulCode,
        metrics.declarations,
        metrics.documentedDeclarations,
        metrics.documentedCode,
    ).joinToString(",") { value ->
        val text = value.toString()
        if (text.any { it == ',' || it == '"' || it == '\n' }) {
            "\"" + text.replace("\"", "\"\"") + "\""
        } else {
            text
        }
    }
