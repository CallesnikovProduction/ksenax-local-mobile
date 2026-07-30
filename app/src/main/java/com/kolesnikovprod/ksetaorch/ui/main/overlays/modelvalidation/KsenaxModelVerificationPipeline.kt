package com.kolesnikovprod.ksetaorch.ui.main.overlays.modelvalidation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kolesnikovprod.ksetaorch.R
import com.kolesnikovprod.ksetaorch.ui.main.overlays.PixelSteppedCornerFrame
import com.kolesnikovprod.ksetaorch.ui.theme.design.KsenaxFontFamily
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_CRITERIA_BORDER_COLOUR
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_CURRENT_MINT_COLOUR
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_DIVIDER_COLOUR
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_ERROR_COLOUR
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_INACTIVE_PIXEL_COLOUR
import com.kolesnikovprod.ksetaorch.ui.theme.design.OVERLAY_PRIMARY_TEXT_COLOUR
import kotlin.math.floor

private val StageRingPixels = listOf(
    3 to 0, 4 to 0, 5 to 0,
    2 to 1, 6 to 1,
    1 to 2, 7 to 2,
    0 to 3, 8 to 3,
    0 to 4, 8 to 4,
    0 to 5, 8 to 5,
    1 to 6, 7 to 6,
    2 to 7, 6 to 7,
    3 to 8, 4 to 8, 5 to 8,
)

private val StageActivePixels = listOf(
    4 to 3,
    3 to 4, 4 to 4, 5 to 4,
    4 to 5,
)

private val StageSuccessPixels = listOf(
    2 to 4,
    3 to 5,
    4 to 4,
    5 to 3,
    6 to 2,
)

private val StageFailurePixels = listOf(
    2 to 2, 3 to 3, 4 to 4, 5 to 5, 6 to 6,
    6 to 2, 5 to 3, 3 to 5, 2 to 6,
)

/**
 * Трёхточечный pipeline «наличие — целостность — достижимость».
 *
 * @since 0.3
 */
@Composable
internal fun KsenaxModelVerificationPipeline(
    stages: List<KsenaxModelVerificationStagePresentation>,
    modifier: Modifier = Modifier,
) {
    require(stages.size == 3) {
        "Model verification pipeline requires exactly three stages."
    }

    Box(
        modifier = modifier.height(62.dp),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp),
        ) {
            val centers = listOf(
                size.width / 6f,
                size.width / 2f,
                size.width * 5f / 6f,
            )
            val y = size.height / 2f
            repeat(2) { lineIndex ->
                val nextStatus = stages[lineIndex + 1].status
                val colour = if (
                    nextStatus != KsenaxModelVerificationStatus.Pending
                ) {
                    OVERLAY_CURRENT_MINT_COLOUR
                } else {
                    OVERLAY_INACTIVE_PIXEL_COLOUR
                }
                drawPixelDottedLine(
                    startX = centers[lineIndex] + 17.dp.toPx(),
                    endX = centers[lineIndex + 1] - 17.dp.toPx(),
                    y = y,
                    colour = colour,
                )
            }
        }

        Row(modifier = Modifier.fillMaxSize()) {
            stages.forEach { stage ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PixelVerificationStageNode(
                        status = stage.status,
                        modifier = Modifier.size(30.dp),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stage.pipelineLabel,
                        color = stage.status.colour,
                        fontFamily = KsenaxFontFamily.EPILEPSY_SANS,
                        fontSize = 10.sp,
                        lineHeight = 11.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawPixelDottedLine(
    startX: Float,
    endX: Float,
    y: Float,
    colour: Color,
) {
    val dashWidth = 4.dp.toPx()
    val gap = 4.dp.toPx()
    var x = startX
    while (x < endX) {
        drawRect(
            color = colour,
            topLeft = Offset(x, y),
            size = Size(
                width = (endX - x).coerceAtMost(dashWidth),
                height = 1.dp.toPx(),
            ),
        )
        x += dashWidth + gap
    }
}

@Composable
private fun PixelVerificationStageNode(
    status: KsenaxModelVerificationStatus,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val gridSize = 9
        val cell = floor(size.minDimension / gridSize)
            .coerceAtLeast(1f)
        val gridWidth = cell * gridSize
        val origin = Offset(
            x = (size.width - gridWidth) / 2f,
            y = (size.height - gridWidth) / 2f,
        )

        StageRingPixels.forEach { (x, y) ->
            drawRect(
                color = status.colour,
                topLeft = Offset(
                    x = origin.x + x * cell,
                    y = origin.y + y * cell,
                ),
                size = Size(cell, cell),
            )
        }

        val centerPixels = when (status) {
            KsenaxModelVerificationStatus.Pending -> emptyList()
            KsenaxModelVerificationStatus.Active -> StageActivePixels
            KsenaxModelVerificationStatus.Success -> StageSuccessPixels
            KsenaxModelVerificationStatus.Failure -> StageFailurePixels
        }
        centerPixels.forEach { (x, y) ->
            drawRect(
                color = status.colour,
                topLeft = Offset(
                    x = origin.x + x * cell,
                    y = origin.y + y * cell,
                ),
                size = Size(cell, cell),
            )
        }
    }
}

/**
 * Подокно с тремя фактическими критериями проверки.
 *
 * @since 0.3
 */
@Composable
internal fun KsenaxModelVerificationCriteriaPanel(
    stages: List<KsenaxModelVerificationStagePresentation>,
    modifier: Modifier = Modifier,
) {
    require(stages.size == 3) {
        "Model verification criteria panel requires exactly three stages."
    }

    Box(
        modifier = modifier.height(104.dp),
    ) {
        PixelSteppedCornerFrame(
            brush = SolidColor(OVERLAY_CRITERIA_BORDER_COLOUR),
            backgroundColor = VerificationPanelBackground,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            stages.forEachIndexed { index, stage ->
                VerificationCriterionRow(
                    stage = stage,
                    leadingIcon = when (index) {
                        0 -> R.drawable.olay_verify_criteria_file
                        1 -> R.drawable.olay_verify_criteria_continuity
                        else -> R.drawable.olay_verify_criteria_responsibility
                    },
                    modifier = Modifier.weight(1f),
                )
                if (index < stages.lastIndex) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp),
                    ) {
                        drawRect(color = OVERLAY_DIVIDER_COLOUR)
                    }
                }
            }
        }
    }
}

@Composable
private fun VerificationCriterionRow(
    stage: KsenaxModelVerificationStagePresentation,
    @DrawableRes leadingIcon: Int,
    modifier: Modifier = Modifier,
) {
    val isRuntimeIcon = leadingIcon == R.drawable.olay_verify_criteria_continuity

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelVerificationBrushIcon(
            drawableId = leadingIcon,
            brush = SolidColor(stage.status.colour),
            accessibilityDescription = null,
            modifier =
                if (isRuntimeIcon)
                    Modifier.size(22.dp)
                else Modifier.size(17.dp).offset(x = 2.dp)
        )

        Spacer(modifier = if (isRuntimeIcon) Modifier.width(4.dp) else Modifier.width(9.dp))

        Text(
            text = stage.criterionLabel,
            color = OVERLAY_PRIMARY_TEXT_COLOUR,
            fontFamily = KsenaxFontFamily.EPILEPSY_SANS,
            fontSize = 12.sp,
            lineHeight = 13.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = stage.status.statusText,
            color = stage.status.colour,
            fontFamily = KsenaxFontFamily.EPILEPSY_SANS,
            fontSize = 11.sp,
            lineHeight = 12.sp,
            textAlign = TextAlign.End,
            maxLines = 1,
        )

        Spacer(modifier = Modifier.width(9.dp))

        VerificationStatusIcon(
            status = stage.status,
            modifier = Modifier.size(15.dp),
        )
    }
}

@Composable
private fun VerificationStatusIcon(
    status: KsenaxModelVerificationStatus,
    modifier: Modifier = Modifier,
) {
    if (status == KsenaxModelVerificationStatus.Failure) {
        PixelFailureCross(modifier)
        return
    }

    PixelVerificationBrushIcon(
        drawableId = when (status) {
            KsenaxModelVerificationStatus.Pending ->
                R.drawable.olay_verify_criteria_responsibility
            KsenaxModelVerificationStatus.Active ->
                R.drawable.olay_verify_criteria_state_inprocess
            KsenaxModelVerificationStatus.Success ->
                R.drawable.olay_verify_criteria_state_success
            KsenaxModelVerificationStatus.Failure ->
                error("Failure is rendered as a pixel cross.")
        },
        brush = SolidColor(status.colour),
        accessibilityDescription = status.statusText,
        modifier = modifier,
    )
}

@Composable
private fun PixelFailureCross(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val cell = size.minDimension / 7f
        StageFailurePixels.forEach { (x, y) ->
            drawRect(
                color = OVERLAY_ERROR_COLOUR,
                topLeft = Offset(x * cell, y * cell),
                size = Size(cell, cell),
            )
        }
    }
}
