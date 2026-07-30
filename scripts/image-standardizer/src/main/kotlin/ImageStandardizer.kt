import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.system.exitProcess

private enum class ResizeMode {
    STRETCH,
    FIT,
    COVER,
}

private enum class VerticalAnchor {
    TOP,
    CENTER,
    BOTTOM,
}

private enum class FillMode {
    TRANSPARENT,
    EDGE,
}

private data class Options(
    val input: Path,
    val output: Path,
    val width: Int,
    val height: Int,
    val mode: ResizeMode,
    val trimTransparent: Boolean,
    val alphaThreshold: Int,
    val verticalScale: Double,
    val verticalAnchor: VerticalAnchor,
    val bottomInset: Double,
    val fillMode: FillMode,
    val overwrite: Boolean,
)

private data class ImageBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

fun main(args: Array<String>) {
    if (args.isEmpty() || args.any { it == "--help" || it == "-h" }) {
        printUsage()
        return
    }

    val options = try {
        parseOptions(args)
    } catch (error: IllegalArgumentException) {
        System.err.println("Ошибка: ${error.message}")
        System.err.println()
        printUsage()
        exitProcess(2)
    }

    try {
        standardize(options)
    } catch (error: Exception) {
        System.err.println("Не удалось обработать изображение: ${error.message}")
        exitProcess(1)
    }
}

private fun parseOptions(args: Array<String>): Options {
    val values = mutableMapOf<String, String>()
    val flags = mutableSetOf<String>()
    var index = 0

    while (index < args.size) {
        val argument = args[index]
        when (argument) {
            "--trim-transparent", "--overwrite" -> {
                flags += argument
                index++
            }

            "--input",
            "--output",
            "--width",
            "--height",
            "--mode",
            "--alpha-threshold",
            "--vertical-scale",
            "--vertical-anchor",
            "--bottom-inset",
            "--fill",
            -> {
                require(index + 1 < args.size) { "После $argument требуется значение." }
                values[argument] = args[index + 1]
                index += 2
            }

            else -> throw IllegalArgumentException("Неизвестный аргумент: $argument")
        }
    }

    val input = values.requiredPath("--input").toAbsolutePath().normalize()
    val width = values.requiredPositiveInt("--width")
    val height = values.requiredPositiveInt("--height")
    val mode = when (values["--mode"]?.lowercase() ?: "stretch") {
        "stretch" -> ResizeMode.STRETCH
        "fit" -> ResizeMode.FIT
        "cover" -> ResizeMode.COVER
        else -> throw IllegalArgumentException("--mode должен быть stretch, fit или cover.")
    }
    val alphaThreshold = values["--alpha-threshold"]?.toIntOrNull() ?: 8
    require(alphaThreshold in 0..254) { "--alpha-threshold должен быть в диапазоне 0..254." }
    val verticalScale = values["--vertical-scale"]?.toDoubleOrNull() ?: 1.0
    require(verticalScale in 0.1..1.0) {
        "--vertical-scale должен быть в диапазоне 0.1..1.0."
    }
    val verticalAnchor = when (values["--vertical-anchor"]?.lowercase() ?: "bottom") {
        "top" -> VerticalAnchor.TOP
        "center" -> VerticalAnchor.CENTER
        "bottom" -> VerticalAnchor.BOTTOM
        else -> throw IllegalArgumentException(
            "--vertical-anchor должен быть top, center или bottom.",
        )
    }
    val bottomInset = values["--bottom-inset"]?.toDoubleOrNull() ?: 0.0
    require(bottomInset in 0.0..<1.0) {
        "--bottom-inset должен быть в диапазоне 0.0..<1.0."
    }
    require(bottomInset == 0.0 || verticalAnchor == VerticalAnchor.BOTTOM) {
        "--bottom-inset можно использовать только с --vertical-anchor bottom."
    }
    val fillMode = when (values["--fill"]?.lowercase() ?: "transparent") {
        "transparent" -> FillMode.TRANSPARENT
        "edge" -> FillMode.EDGE
        else -> throw IllegalArgumentException("--fill должен быть transparent или edge.")
    }

    val output = values["--output"]?.let { Path.of(it).toAbsolutePath().normalize() }
        ?: input.resolveSibling("${input.fileName.toString().substringBeforeLast('.')}_${width}x$height.png")

    return Options(
        input = input,
        output = output,
        width = width,
        height = height,
        mode = mode,
        trimTransparent = "--trim-transparent" in flags,
        alphaThreshold = alphaThreshold,
        verticalScale = verticalScale,
        verticalAnchor = verticalAnchor,
        bottomInset = bottomInset,
        fillMode = fillMode,
        overwrite = "--overwrite" in flags,
    )
}

private fun standardize(options: Options) {
    require(Files.isRegularFile(options.input)) { "Файл не найден: ${options.input}" }
    require(options.input.fileName.toString().endsWith(".png", ignoreCase = true)) {
        "Сейчас поддерживается только PNG: ${options.input.fileName}"
    }

    val samePath = options.input == options.output
    require(!Files.exists(options.output) || options.overwrite) {
        "Выходной файл уже существует. Добавьте --overwrite: ${options.output}"
    }
    require(!samePath || options.overwrite) {
        "Для перезаписи исходного файла нужен флаг --overwrite."
    }

    val original = ImageIO.read(options.input.toFile())
        ?: error("ImageIO не распознал PNG: ${options.input}")
    val sourceBounds = if (options.trimTransparent) {
        findVisibleBounds(original, options.alphaThreshold)
            ?: error("После проверки прозрачности изображение оказалось пустым.")
    } else {
        ImageBounds(0, 0, original.width, original.height)
    }

    val cropped = crop(original, sourceBounds)
    val resized = resize(cropped, options.width, options.height, options.mode)
    val result = applyVerticalScale(
        source = resized,
        scale = options.verticalScale,
        anchor = options.verticalAnchor,
        bottomInset = options.bottomInset,
        fillMode = options.fillMode,
    )
    writePngAtomically(result, options.output)

    println("Готово: ${options.output}")
    println("Исходник: ${original.width}x${original.height}")
    if (options.trimTransparent) {
        println(
            "После trim: ${sourceBounds.width}x${sourceBounds.height} " +
                "(x=${sourceBounds.x}, y=${sourceBounds.y})",
        )
    }
    println("Результат: ${result.width}x${result.height}, mode=${options.mode.name.lowercase()}")
    if (options.verticalScale < 1.0 || options.bottomInset > 0.0) {
        println(
            "Вертикальный масштаб: ${options.verticalScale}, " +
                "anchor=${options.verticalAnchor.name.lowercase()}, " +
                "bottomInset=${options.bottomInset}, " +
                "fill=${options.fillMode.name.lowercase()}",
        )
    }
}

private fun findVisibleBounds(image: BufferedImage, alphaThreshold: Int): ImageBounds? {
    var minX = image.width
    var minY = image.height
    var maxX = -1
    var maxY = -1

    for (y in 0 until image.height) {
        for (x in 0 until image.width) {
            val alpha = image.getRGB(x, y) ushr 24
            if (alpha > alphaThreshold) {
                minX = min(minX, x)
                minY = min(minY, y)
                maxX = max(maxX, x)
                maxY = max(maxY, y)
            }
        }
    }

    return if (maxX < minX || maxY < minY) {
        null
    } else {
        ImageBounds(
            x = minX,
            y = minY,
            width = maxX - minX + 1,
            height = maxY - minY + 1,
        )
    }
}

private fun crop(image: BufferedImage, bounds: ImageBounds): BufferedImage {
    if (bounds == ImageBounds(0, 0, image.width, image.height)) {
        return image
    }

    val result = BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB)
    val graphics = result.createGraphics()
    try {
        applyQualityHints(graphics)
        graphics.composite = AlphaComposite.Src
        graphics.drawImage(
            image,
            0,
            0,
            bounds.width,
            bounds.height,
            bounds.x,
            bounds.y,
            bounds.x + bounds.width,
            bounds.y + bounds.height,
            null,
        )
    } finally {
        graphics.dispose()
    }
    return result
}

private fun resize(
    source: BufferedImage,
    targetWidth: Int,
    targetHeight: Int,
    mode: ResizeMode,
): BufferedImage {
    val result = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB)
    val graphics = result.createGraphics()
    try {
        applyQualityHints(graphics)
        graphics.composite = AlphaComposite.Src
        graphics.color = Color(0, 0, 0, 0)
        graphics.fillRect(0, 0, targetWidth, targetHeight)

        val destination = when (mode) {
            ResizeMode.STRETCH -> ImageBounds(0, 0, targetWidth, targetHeight)
            ResizeMode.FIT, ResizeMode.COVER -> {
                val scaleX = targetWidth.toDouble() / source.width
                val scaleY = targetHeight.toDouble() / source.height
                val scale = if (mode == ResizeMode.FIT) min(scaleX, scaleY) else max(scaleX, scaleY)
                val width = (source.width * scale).roundToInt()
                val height = (source.height * scale).roundToInt()
                ImageBounds(
                    x = (targetWidth - width) / 2,
                    y = (targetHeight - height) / 2,
                    width = width,
                    height = height,
                )
            }
        }

        graphics.drawImage(
            source,
            destination.x,
            destination.y,
            destination.x + destination.width,
            destination.y + destination.height,
            0,
            0,
            source.width,
            source.height,
            null,
        )
    } finally {
        graphics.dispose()
    }
    return result
}

private fun applyVerticalScale(
    source: BufferedImage,
    scale: Double,
    anchor: VerticalAnchor,
    bottomInset: Double,
    fillMode: FillMode,
): BufferedImage {
    if (scale == 1.0 && bottomInset == 0.0) {
        return source
    }

    val scaledHeight = max(1, (source.height * scale).roundToInt())
    val bottomInsetPixels = (source.height * bottomInset).roundToInt()
    val offsetY = when (anchor) {
        VerticalAnchor.TOP -> 0
        VerticalAnchor.CENTER -> (source.height - scaledHeight) / 2
        VerticalAnchor.BOTTOM -> source.height - scaledHeight - bottomInsetPixels
    }
    val topGap = offsetY
    val bottomGap = source.height - offsetY - scaledHeight
    val result = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
    val graphics = result.createGraphics()

    try {
        applyQualityHints(graphics)
        graphics.composite = AlphaComposite.Src
        graphics.color = Color(0, 0, 0, 0)
        graphics.fillRect(0, 0, result.width, result.height)

        if (fillMode == FillMode.EDGE) {
            if (topGap > 0) {
                graphics.drawImage(
                    source,
                    0,
                    0,
                    source.width,
                    topGap,
                    0,
                    0,
                    source.width,
                    1,
                    null,
                )
            }
            if (bottomGap > 0) {
                graphics.drawImage(
                    source,
                    0,
                    offsetY + scaledHeight,
                    source.width,
                    source.height,
                    0,
                    source.height - 1,
                    source.width,
                    source.height,
                    null,
                )
            }
        }

        graphics.drawImage(
            source,
            0,
            offsetY,
            source.width,
            offsetY + scaledHeight,
            0,
            0,
            source.width,
            source.height,
            null,
        )
    } finally {
        graphics.dispose()
    }
    return result
}

private fun applyQualityHints(graphics: Graphics2D) {
    graphics.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION,
        RenderingHints.VALUE_INTERPOLATION_BICUBIC,
    )
    graphics.setRenderingHint(
        RenderingHints.KEY_RENDERING,
        RenderingHints.VALUE_RENDER_QUALITY,
    )
    graphics.setRenderingHint(
        RenderingHints.KEY_ALPHA_INTERPOLATION,
        RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY,
    )
}

private fun writePngAtomically(image: BufferedImage, output: Path) {
    val parent = output.parent ?: Path.of(".").toAbsolutePath().normalize()
    Files.createDirectories(parent)
    val temporary = Files.createTempFile(parent, ".image-standardizer-", ".png")

    try {
        check(ImageIO.write(image, "png", temporary.toFile())) {
            "В текущей Java нет PNG writer."
        }
        Files.move(
            temporary,
            output,
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    } catch (error: Exception) {
        Files.deleteIfExists(temporary)
        throw error
    }
}

private fun Map<String, String>.requiredPath(name: String): Path =
    Path.of(this[name] ?: throw IllegalArgumentException("Не указан $name."))

private fun Map<String, String>.requiredPositiveInt(name: String): Int {
    val value = this[name]?.toIntOrNull()
        ?: throw IllegalArgumentException("$name должен быть целым числом.")
    require(value > 0) { "$name должен быть больше нуля." }
    return value
}

private fun printUsage() {
    println(
        """
        Унификация PNG по точному размеру.

        Обязательные аргументы:
          --input <path>             путь к исходному PNG
          --width <px>               ширина результата
          --height <px>              высота результата

        Дополнительные аргументы:
          --output <path>            путь результата; по умолчанию *_WIDTHxHEIGHT.png
          --mode <stretch|fit|cover> способ изменения пропорций; по умолчанию stretch
          --trim-transparent         сначала убрать прозрачные поля
          --alpha-threshold <0..254> порог видимости для trim; по умолчанию 8
          --vertical-scale <0.1..1>  вертикально сжать содержимое внутри холста
          --vertical-anchor <...>    top, center или bottom; по умолчанию bottom
          --bottom-inset <0.0..<1.0> доля пустого места снизу при anchor=bottom;
                                      если места не хватает, верх будет обрезан
          --fill <transparent|edge>  заполнение освободившегося места
          --overwrite                разрешить замену существующего файла
        """.trimIndent(),
    )
}
