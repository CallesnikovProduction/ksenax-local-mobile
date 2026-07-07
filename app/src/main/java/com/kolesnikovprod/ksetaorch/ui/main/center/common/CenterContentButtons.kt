package com.kolesnikovprod.ksetaorch.ui.main.center.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kolesnikovprod.ksetaorch.R
import com.kolesnikovprod.ksetaorch.ui.components.GradientIcon
import com.kolesnikovprod.ksetaorch.ui.components.PixelSquareFrame
import com.kolesnikovprod.ksetaorch.ui.components.PixelWideFrame
import com.kolesnikovprod.ksetaorch.ui.theme.design.KsenaxFontFamily
import com.kolesnikovprod.ksetaorch.ui.theme.design.sunsetBottomBarGradientBrush

/**
 * Отображает компактную пиксельную кнопку перехода к разрешениям приложения.
 *
 * Компонент рисует квадратную декоративную рамку без внутренней иконки и
 * обрабатывает нажатие через переданный [onClick]. Для accessibility задаёт
 * описание и роль кнопки, чтобы элемент был корректно распознан экранными
 * читалками и другими assistive-инструментами.
 *
 * @since 0.2
 */
@Composable
internal fun EmptyPermissionsButton(
    onClick : () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(42.dp)
            .semantics {
                contentDescription = "android_permissions_button"
                role               = Role.Button
            }
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onClick,
            ),
    ) {
        PixelSquareFrame(
            brush           = sunsetBottomBarGradientBrush,
            modifier        = Modifier.matchParentSize(),
            backgroundColor = Color(0x9903070D),
        )

        GradientIcon(
            drawableId         = R.drawable.soft_ic_android_permissions,
            contentDescription = null,
            brush              = sunsetBottomBarGradientBrush,
            modifier           = Modifier
                .size(25.dp)
                .offset(x = 9.dp, y = 9.dp)
        )
    }
}

/**
 * Отображает пиксельную кнопку выбора рабочей папки агента.
 *
 * Компонент рисует широкую декоративную рамку, иконку папки и текстовую
 * подпись. Нажатие обрабатывается через [onClick], поэтому сама кнопка не
 * знает, какой именно сценарий будет запущен: открытие SAF, bottom sheet,
 * экран настроек или другой внешний обработчик.
 *
 * @since 0.2
 */
@Composable
internal fun FolderActionButton(
    onClick : () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .width(158.dp)
            .height(42.dp)
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        PixelWideFrame(
            brush           = sunsetBottomBarGradientBrush,
            modifier        = Modifier.matchParentSize(),
            backgroundColor = Color(0x9903070D),
        )

        Row(
            modifier          = Modifier
                .padding(horizontal = 13.dp)
                .semantics {
                    contentDescription = "choose_agentic_workspace_folder_button"
                    role               = Role.Button
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GradientIcon(
                drawableId         = R.drawable.folder,
                contentDescription = null,
                brush              = sunsetBottomBarGradientBrush,
                modifier           = Modifier
                    .size(35.dp)
                    .offset(x = (-2).dp, y = (-2).dp),
            )

            Spacer(modifier = Modifier.width(2.dp))

            Text(
                text       = "Рабочая папка",
                color      = Color.White, // базовая маска для наложения градиента
                fontFamily = KsenaxFontFamily.minecraftFont,
                fontSize   = 9.sp,
                lineHeight = 12.sp,
                maxLines   = 1,
                modifier   = Modifier
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithCache {
                        onDrawWithContent {
                            drawContent()
                            // градиент рисуется поверх белой иконки там, где реально иконка
                            drawRect(
                                brush     = sunsetBottomBarGradientBrush,
                                blendMode = BlendMode.SrcAtop,
                            )
                        }
                    },
            )
        }
    }
}