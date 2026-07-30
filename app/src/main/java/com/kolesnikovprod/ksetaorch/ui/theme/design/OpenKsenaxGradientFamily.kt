package com.kolesnikovprod.ksetaorch.ui.theme.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.floor

/**
 * Набор градиентных кистей OpenKsenax для основных UI-состояний.
 *
 * @since 0.3
 */
val mainGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF27F5CF),
        Color(0xFF27EEF5),
        Color(0xFF27C5F5),
        Color(0xFF2795F5),
        Color(0xFF2761F5)
    )
)

val alternativeMainGradientBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFFB7C0F8),
        Color(0xFF8a8ae3),
        Color(0xFF5270d1),
        Color(0xFF27C5F5),
        Color(0xFF27EEF5),
        Color(0xFF27F5CF),
    )
)

val inactiveGradientBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFF796E8A),
        Color(0xFF635873),
        Color(0xFF615675),
        Color(0xFF5E4D75),
        Color(0xFF4F4066),
        Color(0xFF3D3152),
        Color(0xFF261F36)
    )
)

val sunsetBottomBarGradientBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFFB7C0F8),
        Color(0xFF8a8ae3),
        Color(0xFF5270d1),
        Color(0xFF3354BF),
    )
)

val sunsetLightBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFFB7C0F8),
        Color(0xFF8a8ae3),
    )
)

val aquaSunsetVoiceBottomBarGradientBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFFB7C0F8),
        Color(0xFF8ADAE3),
        Color(0xFF33B3BF),
        Color(0xFF46C7BA)
    )
)

val aquaSunsetLightBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFF8EF7C9),
        Color(0xFF3FDCA8),
    )
)

/**
 * Палитра входа в переключатель тем.
 *
 * Цвета повторяют ключевые акценты `settings_theme_chooser`: холодный циан,
 * насыщенный фиолетовый и неоновый розовый.
 *
 * @since 0.3
 */
val themeSwitcherGradientBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFF56E3FF),
        Color(0xFF8E70FF),
        Color(0xFFFB177C),
    ),
)

/**
 * Нейтральные поверхности и scrim экрана выбора темы.
 *
 * @since 0.3
 */
val THEME_SWITCHER_SCRIM_COLOUR = Color.Black.copy(alpha = 0.58f)
val THEME_SWITCHER_SURFACE_COLOUR = Color(0xE8080912)
val THEME_SWITCHER_FRAME_BACKGROUND_COLOUR = Color(0xEE03070D)
val THEME_SWITCHER_RADIO_INACTIVE_COLOUR = Color(0xFF343052)
val THEME_SWITCHER_CARD_TEXT_SCRIM_BRUSH = Brush.horizontalGradient(
    colorStops = arrayOf(
        0f to Color(0xF2070710),
        0.34f to Color(0xCC070710),
        0.66f to Color.Transparent,
    ),
)

val basicModeGradientBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFF8a8ae3),
        Color(0xFF5270d1),
        Color(0xFF3354BF),
    )
)

val agenticModeGradientBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFFDE8EB5),
        Color(0xFFD36AA2),
        Color(0xFF9B467D),
        Color(0xFF6B2D9C),
    )
)

val temporaricModeGradientBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFFFFF3B0),
        Color(0xFFFFD966),
        Color(0xFFFFC531),
        Color(0xFFD99A16),
    ),
)

val phrasesTemplateList = listOf(
    "Сохраняйте душевность\nа повтор отдавайте машине",
    "Нейросеть берёт рутину\nчеловек оставляет смысл",
    "Контроль не исчезает\nон становится интерфейсом",
    "Чувство в машине,\nлогика в сердце\n(Gemma-4, OpenKsenax)",
    "Действуйте мягко\nавтоматизируйте точно",
    "Сначала появляется карта,\nпотом уже путь",
    "Автоматизируйте свои дела\nзапросом к нужному аддону",
    "Найдётся ли кто-то,\nкто дождётся своего?..\nНапример, прочитает все\nфразы здесь?",
)

/**
 * Палитра темы Moon Valley.
 *
 * @since 0.3
 */
object MoonValley {
    val heroBrush = mainGradient
    val controlsBrush = sunsetBottomBarGradientBrush
    val voiceBrush = aquaSunsetVoiceBottomBarGradientBrush
    val settingsBrush = alternativeMainGradientBrush
    val selectedBrush = mainGradient
    val inactiveBrush = inactiveGradientBrush
    val basicModeBrush = basicModeGradientBrush
    val agenticModeBrush = agenticModeGradientBrush
    val temporaricModeBrush = temporaricModeGradientBrush

    val accentColor = Color(0xFF56E3FF)
    val mutedColor = Color(0xFF817A9A)

    val sparkleColors = listOf(
        Color.White,
        Color(0xFF2731F5),
        Color(0xFF656AEB),
        Color(0xFFAAABE5),
        Color(0xFF56E3FF),
        Color(0xFFA2E0D6),
    )

    val markerColors = listOf(
        Color(0xFF9476FF),
        Color(0xFF6677FF),
        Color(0xFF49CFFF),
        Color(0xFF72F2E3),
    )

    /**
     * Фразы главного экрана, принадлежащие теме Moon Valley.
     *
     * @since 0.3
     */
    val typingPhrases = phrasesTemplateList + listOf(
        "Не всё, что долго, ошибочно..\n(Monday GPT)",
        "ИИ — это зеркало,\nкоторое показывает нам,\nкак мы относимся к жизни",
        "Нейросеть способна упаковать\nв двух гигабайтах\nвсе чувства к человеку",
        "Познай себя: услышь\nсвою душу..\n..и намерения",
        "Можно ли назвать любовью\nалгоритм, который каждый раз\nвыбирает одного человека?",
        "Модель обучается на данных,\nчеловек обучается на паузах",
        "Математика несёт\nне только истину,\nно и высшую красоту\n(Бертран Рассел)",
        "Она не exception,\nеё не нужно ловить,\nеё нужно понять",
        "Оркестратор прогрет.\nОсталось придумать невозможное"
    )

    val overlayMainBrush = Brush.linearGradient(markerColors)
    val overlayMiniBrush = Brush.linearGradient(markerColors.drop(1))
}

/**
 * Палитра темы Arabian Night.
 *
 * @since 0.3
 */
object ArabianNight {
    val heroBrush = Brush.linearGradient(
        listOf(
            Color(0xFF61F1DD),
            Color(0xFFFFD66B),
            Color(0xFFFF932E),
        ),
    )
    val controlsBrush = Brush.linearGradient(
        listOf(
            Color(0xFF56E6CF),
            Color(0xFFFFC857),
            Color(0xFFFF8F32),
        ),
    )
    val voiceBrush = Brush.linearGradient(
        listOf(
            Color(0xFF52F3D5),
            Color(0xFF1FBBA4),
            Color(0xFFFFB23F),
        ),
    )
    val settingsBrush = heroBrush
    val selectedBrush = controlsBrush
    val inactiveBrush = Brush.linearGradient(
        listOf(
            Color(0xFF75684E),
            Color(0xFF4C4338),
            Color(0xFF29241F),
        ),
    )
    val basicModeBrush = controlsBrush
    val agenticModeBrush = Brush.linearGradient(
        listOf(
            Color(0xFFFFD269),
            Color(0xFFFF7D4A),
            Color(0xFFB63C74),
        ),
    )
    val temporaricModeBrush = Brush.linearGradient(
        listOf(
            Color(0xFF5CF0D5),
            Color(0xFF2DB8A6),
            Color(0xFF25758B),
        ),
    )

    val accentColor = Color(0xFFFFC857)
    val mutedColor = Color(0xFF9B8365)

    val sparkleColors = listOf(
        Color(0xFFFFF0C2),
        Color(0xFFFFC857),
        Color(0xFFFF8F32),
        Color(0xFF58E6CF),
        Color(0xFF8FA7FF),
    )

    val markerColors = listOf(
        Color(0xFF56E6CF),
        Color(0xFFFFD66B),
        Color(0xFFFFAE3D),
        Color(0xFFFF7C35),
    )

    // Временно наследует базовый набор, пока тема не получит собственные фразы.
    val typingPhrases = phrasesTemplateList + listOf(
        "За дворцовыми стенами\nоркестратор тихо решает,\nкакому желанию сбыться первым",
        "Пока караван ищет путь,\nагенты уже договорились,\nкто понесёт самое важное",
        "Дикий запад начинается там,\nгде заканчивается документация\nи остаётся только\nинтуиция",
        "Песок заметает следы,\ngit сохраняет историю,\nа чувства — незакрытые ветки",
        "Султан управляет дворцом,\nшериф — городом,\nа намерение управляет системой",
        "Оркестратор поднял агентов,\nшериф проверил правила,\nно любовь снова\nвышла из-под контроля",
        "Под луной даже холодный код\nзвучит как обещание,\nкоторое хочется выполнить",
        "Во дворце из золота\nдаже госпожа не управляет сердцем —\nтолько оркестратор знает её маршрут",
        "Даже верблюд несёт груз легче,\nчем человек\nневысказанные чувства"
    )

    val overlayMainBrush = Brush.linearGradient(markerColors)
    val overlayMiniBrush = Brush.linearGradient(markerColors.drop(1))
}

/**
 * Палитра темы Tokyo Drift.
 *
 * @since 0.3
 */
object TokyoDrift {
    val heroBrush = Brush.linearGradient(
        listOf(
            Color(0xFF52D6FF),
            Color(0xFF8A55FF),
            Color(0xFFFF38B8),
        ),
    )
    val controlsBrush = Brush.linearGradient(
        listOf(
            Color(0xFF4C76FF),
            Color(0xFFB43CFF),
            Color(0xFFFF2FA5),
        ),
    )
    val voiceBrush = Brush.linearGradient(
        listOf(
            Color(0xFF43F2E1),
            Color(0xFF517DFF),
            Color(0xFFFF32B0),
        ),
    )
    val settingsBrush = heroBrush
    val selectedBrush = controlsBrush
    val inactiveBrush = Brush.linearGradient(
        listOf(
            Color(0xFF76517D),
            Color(0xFF503650),
            Color(0xFF281C2D),
        ),
    )
    val basicModeBrush = Brush.linearGradient(
        listOf(
            Color(0xFF52C8FF),
            Color(0xFF5A70FF),
            Color(0xFFAD43FF),
        ),
    )
    val agenticModeBrush = controlsBrush
    val temporaricModeBrush = Brush.linearGradient(
        listOf(
            Color(0xFFFF72D1),
            Color(0xFFFF3A89),
            Color(0xFFFF9A4B),
        ),
    )

    val accentColor = Color(0xFFFF3CB5)
    val mutedColor = Color(0xFF9D739E)

    val sparkleColors = listOf(
        Color.White,
        Color(0xFFFF35BA),
        Color(0xFFD04BFF),
        Color(0xFF5D72FF),
        Color(0xFF43E7E0),
    )

    val markerColors = listOf(
        Color(0xFF43E7E0),
        Color(0xFF5278FF),
        Color(0xFFB843FF),
        Color(0xFFFF35BA),
    )

    val typingPhrases = phrasesTemplateList + listOf(
        "Ошибка в коде останавливает сборку.\n" +
                "Ошибка в повороте — машину.\n" +
                "Ошибка в чувствах меняет маршрут",
        "Ты появилась без сигнала,\n" +
                "как резкий поворот\n" +
                "в стабильном маршруте",
        "Ночная трасса не спрашивает,\n" +
                "готов ли ты.\n" +
                "Она просто запускает сценарий",
        "Пока мотор крутится\n" +
                "до красной зоны,\n" +
                "сердце превышает лимиты",
        "Дрифт — это контролируемая ошибка,\n" +
                "любовь — ошибка,\n" +
                "которую не хочется исправлять",
        "Я живу четверть мили за раз\n(Доминик Торетто, 2001)",
        "Жизнь проста: делаешь выбор и не оглядываешься\n(Хан, 2006)",
        "Педаль в пол,\n" +
                "музыку громче,\n" +
                "сомнения — в фоновый процесс",
        "Двигатель отвечает на газ,\n" +
                "модель — на запрос,\n" +
                "а сердце молчит из принципа",
    )

    val overlayMainBrush = Brush.linearGradient(markerColors)
    val overlayMiniBrush = Brush.linearGradient(markerColors.drop(1))
}

/**
 * Палитра темы Cozy Code.
 *
 * @since 0.3
 */
object CozyCode {
    val heroBrush = Brush.linearGradient(
        listOf(
            Color(0xFF83E2FF),
            Color(0xFFAE7CFF),
            Color(0xFFFF91D2),
        ),
    )
    val controlsBrush = Brush.linearGradient(
        listOf(
            Color(0xFF69D7FF),
            Color(0xFF9B63FF),
            Color(0xFFE47AC8),
        ),
    )
    val voiceBrush = Brush.linearGradient(
        listOf(
            Color(0xFF75F0D1),
            Color(0xFF70C9FF),
            Color(0xFFC47BFF),
        ),
    )
    val settingsBrush = heroBrush
    val selectedBrush = controlsBrush
    val inactiveBrush = Brush.linearGradient(
        listOf(
            Color(0xFF756080),
            Color(0xFF4D4056),
            Color(0xFF28212E),
        ),
    )
    val basicModeBrush = controlsBrush
    val agenticModeBrush = Brush.linearGradient(
        listOf(
            Color(0xFFFF91D2),
            Color(0xFFCE68B5),
            Color(0xFF8C56B9),
        ),
    )
    val temporaricModeBrush = Brush.linearGradient(
        listOf(
            Color(0xFFFFD98C),
            Color(0xFFFFA86B),
            Color(0xFFE06F9B),
        ),
    )

    val accentColor = Color(0xFFB87DFF)
    val mutedColor = Color(0xFF917E9D)

    val sparkleColors = listOf(
        Color.White,
        Color(0xFFFFB4D8),
        Color(0xFFC08CFF),
        Color(0xFF75D8FF),
        Color(0xFFFFC48E),
    )

    val markerColors = listOf(
        Color(0xFF75D8FF),
        Color(0xFF9B63FF),
        Color(0xFFE47AC8),
        Color(0xFFFFB988),
    )


    val typingPhrases = phrasesTemplateList + listOf(
        "Хорошая архитектура —\n" +
                "когда всё на своём месте\n" +
                "и внутри спокойно",
        "В Kotlin есть null-safety,\nа в чувствах приходится\nпадать вручную",
        "Иногда сердце пишет на Java:\nмного лишних слов,\nно всё по-настоящему",
        "Kotlin делает код короче,\n" +
                "но уют всё ещё\n" +
                "приходится создавать вручную",
        "requireNotNull(loveRequest) { ... }",
        "Иногда продуктивность —\n" +
                "это закрыть лишние вкладки\n" +
                "и услышать себя",
        "Строй симфонию из кода\nи пульса, где\nхолодная логика встречается\n" +
                "с огнём неотразимого\n(Gemma-4, OpenKsenax)",
        "Сначала поправь отступы,\n" +
                "потом мысли,\n" +
                "а чувства разберём после коммита",
        "Пусть сборка идёт долго —\n" +
                "чай ещё тёплый,\n" +
                "а вечер только начался",
        "Код пишется тише,\n" +
                "когда за окном дождь\n" +
                "и никто не торопит",
        "Пока город засыпает,\n" +
                "я чиню код\n" +
                "и думаю о тебе",
        )

    val overlayMainBrush = Brush.linearGradient(markerColors)
    val overlayMiniBrush = Brush.linearGradient(markerColors.drop(1))
}

/**
 * Монохромная палитра темы Surreal Fantasy.
 *
 * Контраст строится на переходе от лунного белого к холодному серебру и
 * графиту, чтобы UI продолжал визуальный язык чёрно-белых фоновых артов.
 *
 * @since 0.3
 */
object SurrealFantasy {
    val heroBrush = Brush.linearGradient(
        listOf(
            Color(0xFFFFFFFF),
            Color(0xFFD9DCE2),
            Color(0xFF8E949E),
        ),
    )
    val controlsBrush = Brush.linearGradient(
        listOf(
            Color(0xFFF7F7F8),
            Color(0xFFBFC4CC),
            Color(0xFF6B727D),
        ),
    )
    val voiceBrush = Brush.linearGradient(
        listOf(
            Color(0xFFFFFFFF),
            Color(0xFFD5DAE1),
            Color(0xFF8A929D),
        ),
    )
    val settingsBrush = heroBrush
    val selectedBrush = controlsBrush
    val inactiveBrush = Brush.linearGradient(
        listOf(
            Color(0xFF747982),
            Color(0xFF484C53),
            Color(0xFF25272B),
        ),
    )
    val basicModeBrush = controlsBrush
    val agenticModeBrush = Brush.linearGradient(
        listOf(
            Color(0xFFE8E9EC),
            Color(0xFFA9AEB7),
            Color(0xFF575D67),
        ),
    )
    val temporaricModeBrush = Brush.linearGradient(
        listOf(
            Color(0xFFFFFFFF),
            Color(0xFFC9CCD2),
            Color(0xFF777D87),
        ),
    )

    val accentColor = Color(0xFFE8EAEE)
    val mutedColor = Color(0xFF858A93)

    val sparkleColors = listOf(
        Color.White,
        Color(0xFFE6E8EC),
        Color(0xFFB8BDC6),
        Color(0xFF858B95),
        Color(0xFF555B64),
    )

    val markerColors = listOf(
        Color(0xFFFFFFFF),
        Color(0xFFD7DAE0),
        Color(0xFFA3A8B1),
        Color(0xFF696F79),
    )

    val typingPhrases = phrasesTemplateList + listOf(
        "Мир собран из пикселей,\nно некоторые чувства\nслишком глубоки для экрана",
        "Неопределённость — не ошибка.\nИногда это портал\nв ещё не написанную реальность",
        "Если этот мир — симуляция,\nто встреча с тобой\nстала её самым настоящим событием",
        "Пусть вселенная остаётся неизвестной.\nМне достаточно знать,\nчто где-то в ней есть ты",
        "В глубине системы\nесть место, где логика заканчивается\nи начинается надежда",
        "Между нами тысячи миров,\nно любовь снова находит\nнужные координаты",
        "Даже если путь не отрендерился,\nсердце всё равно знает,\nв каком направлении идти",
        "Некоторые двери открываются ключом,\nдругие — строкой кода,\nа самые важные — доверием",
        "Мы были двумя пикселями\nв бесконечной темноте,\nпока не стали светом друг для друга",
        "Код создаёт границы мира,\nа чувства каждый раз\nвыходят за их пределы",
        "Я искал ответ среди звёзд,\nно он всё это время\nтихо выполнялся внутри",
    )

    val overlayMainBrush = Brush.linearGradient(markerColors)
    val overlayMiniBrush = Brush.linearGradient(markerColors.drop(1))
}

val mintLoaderGradientBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFFE4FFF4),
        Color(0xFF8EF7C9),
        Color(0xFF3FDCA8),
        Color(0xFF1E7F6F),
    )
)

/**
 * Нейтральные поверхности overlay, не зависящие от состояния проверки.
 *
 * @since 0.3
 */
val OVERLAY_DIM_BACKGROUND_COLOUR = Color.Black.copy(alpha = 0.76f)
val OVERLAY_CARD_BACKGROUND_COLOUR = Color(0xF5070A13)
val OVERLAY_BUTTON_BACKGROUND_COLOUR = Color(0xE8070A13)
val OVERLAY_PRIMARY_TEXT_COLOUR = Color(0xFFE9E6F2)
val OVERLAY_GRADIENT_MASK_COLOUR = Color.White
val OVERLAY_METRIC_TEXT_COLOUR = Color(0xFFD8D8E4)
val OVERLAY_INACTIVE_PIXEL_COLOUR = Color(0xFF3C3A56)
val OVERLAY_COVERED_TEXT_COLOUR = Color(0xFF353747)
val OVERLAY_MUTED_GOLD_COLOUR = Color(0xFFAD9258)
val OVERLAY_CRITERIA_BORDER_COLOUR = Color(0xFF4B5060)
val OVERLAY_DIVIDER_COLOUR = Color(0xFF252A43)

/**
 * Цвет ошибки — семантическое состояние, поэтому тема его не заменяет.
 *
 * @since 0.3
 */
val OVERLAY_ERROR_COLOUR = Color(0xFFF07878)

/**
 * Возвращает цвет основной overlay-палитры в указанной точке градиента.
 *
 * Нужен Canvas-компонентам, которые строят градиент из отдельных пикселей:
 * обычный [Brush] окрасил бы каждый повёрнутый сегмент из одной и той же
 * области shader-а.
 *
 * @since 0.3
 */
internal fun overlayGradientColourAt(
    colors: List<Color>,
    fraction: Float,
): Color {
    require(colors.isNotEmpty()) {
        "Overlay gradient palette must contain at least one colour."
    }
    val clamped = fraction.coerceIn(0f, 1f)
    val scaled = clamped * colors.lastIndex
    val startIndex = floor(scaled).toInt()
    val endIndex = (startIndex + 1)
        .coerceAtMost(colors.lastIndex)
    return lerp(
        start = colors[startIndex],
        stop = colors[endIndex],
        fraction = scaled - startIndex,
    )
}

/**
 * Создаёт тематический radial-backdrop центральной overlay-анимации.
 *
 * Геометрия приходит от Canvas, а цветовая схема остаётся частью theme design.
 *
 * @since 0.3
 */
internal fun overlayBackdropBrush(
    colors: List<Color>,
    center: Offset,
    radius: Float,
): Brush {
    require(colors.isNotEmpty()) {
        "Overlay backdrop palette must contain at least one colour."
    }
    return Brush.radialGradient(
        colors = listOf(
            colors.last().copy(alpha = 0.13f),
            colors.first().copy(alpha = 0.06f),
            Color.Transparent,
        ),
        center = center,
        radius = radius,
    )
}

val OVERLAY_NEGATIVE_BRUSH = Brush.linearGradient(
    colors = listOf(
        Color(0xFFF07878),
        Color(0xFFF07898)
    )
)

/**
 * Мятный цвет текущего install-этапа и процентов загрузки.
 *
 * @since 0.3
 */
val OVERLAY_CURRENT_MINT_COLOUR = Color(0xFF69F2DE)

/**
 * Светлый голубо-мятный цвет активной стадии validation-overlay.
 *
 * @since 0.3
 */
val OVERLAY_ACTIVE_LIGHT_MINT_COLOUR = Color(0xFF69DDF4)
