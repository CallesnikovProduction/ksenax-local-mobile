package com.kolesnikovprod.ksetaorch.addons.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kolesnikovprod.ksetaorch.R
import com.kolesnikovprod.ksetaorch.addons.banner.AddonBannerContract
import com.kolesnikovprod.ksetaorch.addons.download.AddonInstallStage
import com.kolesnikovprod.ksetaorch.ui.components.KsenaxBackArrowButton
import com.kolesnikovprod.ksetaorch.ui.components.KsenaxPressableBox
import com.kolesnikovprod.ksetaorch.ui.components.PixelGradientSpinner
import com.kolesnikovprod.ksetaorch.ui.components.whileKsenaxPressed
import com.kolesnikovprod.ksetaorch.ui.theme.KsenaxThemeVisuals
import com.kolesnikovprod.ksetaorch.ui.theme.design.KsenaxFontFamily
import dev.openksenax.addons.contract.AddonId
import java.text.DateFormat
import java.util.Date
import kotlin.math.ceil
import kotlinx.coroutines.isActive

private val AddonsBackground = Color(0xFF07070D)
private val AddonsSurface = Color(0xFF111019)
private val AddonsBorder = Color(0xFF302A39)
private val AddonsMuted = Color(0xFF9F96A8)
private val AddonsGreen = Color(0xFF47E77B)
private val AddonsWarning = Color(0xFFFFB65C)
private val AddonsDanger = Color(0xFFFF6B73)
private val AddonsGold = Color(0xFFFFC857)
private val AddonsLink = Color(0xFF69B7FF)
private val PixelWaveRows = intArrayOf(0, -1, -1, -1, 0, 1, 1, 1)
private const val REFRESH_HALF_TURN_DEGREES = 180f
private const val REFRESH_HALF_TURN_MILLIS = 160
private const val REFRESH_SETTLE_MILLIS = 180

/**
 * Полноэкранная host-витрина аддонов, выезжающая справа.
 *
 * Экран показывает каталог и системные команды, но не рисует рабочий UI
 * аддона: тот открывается в отдельном addon APK.
 *
 * @since 0.3
 */
@Composable
internal fun AddonCatalogScreen(
    state: AddonCatalogUiState,
    theme: KsenaxThemeVisuals,
    revealProgress: Float,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onSelectAddon: (AddonId) -> Unit,
    onCloseManagement: () -> Unit,
    onOpenAddon: (AddonId) -> Unit,
    onInstallAddon: (AddonId) -> Unit,
    onDismissInstallConfirmation: () -> Unit,
    onShowInfo: (AddonId) -> Unit,
    onCheckForUpdates: (AddonId) -> Unit,
    onUninstallAddon: (AddonId) -> Unit,
    onDismissInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = revealProgress.coerceIn(0f, 1f)

    BackHandler(enabled = progress > 0f) {
        when {
            state.infoOverlay != null -> onDismissInfo()
            state.selectedCard != null -> onCloseManagement()
            state.pendingInstallConfirmationId != null ->
                onDismissInstallConfirmation()
            else -> onDismiss()
        }
    }

    if (progress <= 0f) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = size.width * (1f - progress)
            }
            .background(AddonsBackground),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AddonsTopBar(
                theme = theme,
                isRefreshing = state.isRefreshing,
                onDismiss = onDismiss,
                onRefresh = onRefresh,
            )

            AddonCatalogContent(
                state = state,
                theme = theme,
                onSelectAddon = onSelectAddon,
                onOpenAddon = onOpenAddon,
                onInstallAddon = onInstallAddon,
                onDismissInstallConfirmation =
                    onDismissInstallConfirmation,
                onShowInfo = onShowInfo,
                onCheckForUpdates = onCheckForUpdates,
                onUninstallAddon = onUninstallAddon,
                modifier = Modifier.weight(1f),
            )
            AddonsEarlyTestingBottomBar()
        }

        state.infoOverlay?.let { info ->
            AddonInfoOverlay(
                info = info,
                onDismiss = onDismissInfo,
            )
        }
    }
}

@Composable
private fun AddonsTopBar(
    theme: KsenaxThemeVisuals,
    isRefreshing: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
) {
    val refreshRotation = remember {
        Animatable(0f)
    }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            while (isActive) {
                refreshRotation.animateTo(
                    targetValue =
                        refreshRotation.value +
                            REFRESH_HALF_TURN_DEGREES,
                    animationSpec = tween(
                        durationMillis = REFRESH_HALF_TURN_MILLIS,
                        easing = LinearEasing,
                    ),
                )
                if (refreshRotation.value >= 3_600f) {
                    refreshRotation.snapTo(
                        refreshRotation.value % 360f,
                    )
                }
            }
        } else {
            val restingRotation =
                ceil(
                    refreshRotation.value /
                        REFRESH_HALF_TURN_DEGREES,
                ).toFloat() * REFRESH_HALF_TURN_DEGREES
            refreshRotation.animateTo(
                targetValue = restingRotation,
                animationSpec = tween(
                    durationMillis = REFRESH_SETTLE_MILLIS,
                ),
            )
            refreshRotation.snapTo(restingRotation % 360f)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 28.dp)
            .padding(top = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        KsenaxBackArrowButton(
            brush = theme.controlsBrush,
            onClick = onDismiss,
            pointsLeft = true,
            contentDescription = "Закрыть панель аддонов",
            modifier = Modifier.size(44.dp),
        )

        Text(
            text = "OKx add-ons",
            color = Color.White,
            fontFamily =
                KsenaxFontFamily.LOGOS_AND_HEADLINES_JERSEY_10_REGULAR,
            fontSize = 30.sp,
            lineHeight = 32.sp,
            modifier = Modifier.weight(1f),
        )

        KsenaxPressableBox(
            onClick = onRefresh,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) { pressed ->
            Icon(
                painter = painterResource(
                    R.drawable.sidepanel_right_refreshaddons,
                ),
                contentDescription = "Найти новые аддоны",
                tint = (
                    if (isRefreshing) {
                        theme.accentColor
                    } else {
                        theme.mutedColor
                    }
                    ).whileKsenaxPressed(pressed),
                modifier = Modifier
                    .size(36.dp)
                    .graphicsLayer {
                        rotationZ = refreshRotation.value
                    },
            )
        }
    }
}

@Composable
private fun AddonCatalogContent(
    state: AddonCatalogUiState,
    theme: KsenaxThemeVisuals,
    onSelectAddon: (AddonId) -> Unit,
    onOpenAddon: (AddonId) -> Unit,
    onInstallAddon: (AddonId) -> Unit,
    onDismissInstallConfirmation: () -> Unit,
    onShowInfo: (AddonId) -> Unit,
    onCheckForUpdates: (AddonId) -> Unit,
    onUninstallAddon: (AddonId) -> Unit,
    modifier: Modifier = Modifier,
) {
    var installedExpanded by rememberSaveable {
        mutableStateOf(true)
    }
    val backgroundInteractionSource = remember {
        MutableInteractionSource()
    }
    val installed = state.cards.filter(AddonCardUiModel::isInstalled)
    val available = state.cards.filterNot(AddonCardUiModel::isInstalled)

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                enabled =
                    state.pendingInstallConfirmationId != null,
                interactionSource = backgroundInteractionSource,
                indication = null,
                onClick = onDismissInstallConfirmation,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AddonCatalogSections(
                state = state,
                theme = theme,
                installed = installed,
                available = available,
                installedExpanded = installedExpanded,
                onInstalledExpandedChange = { expanded ->
                    installedExpanded = expanded
                },
                onSelectAddon = onSelectAddon,
                onOpenAddon = onOpenAddon,
                onInstallAddon = onInstallAddon,
                onShowInfo = onShowInfo,
                onCheckForUpdates = onCheckForUpdates,
                onUninstallAddon = onUninstallAddon,
            )
        }
    }
}

@Composable
private fun AddonCatalogSections(
    state: AddonCatalogUiState,
    theme: KsenaxThemeVisuals,
    installed: List<AddonCardUiModel>,
    available: List<AddonCardUiModel>,
    installedExpanded: Boolean,
    onInstalledExpandedChange: (Boolean) -> Unit,
    onSelectAddon: (AddonId) -> Unit,
    onOpenAddon: (AddonId) -> Unit,
    onInstallAddon: (AddonId) -> Unit,
    onShowInfo: (AddonId) -> Unit,
    onCheckForUpdates: (AddonId) -> Unit,
    onUninstallAddon: (AddonId) -> Unit,
) {
    state.actionMessage?.let { message ->
        Text(
            text = message,
            color = AddonsWarning,
            fontFamily =
                KsenaxFontFamily.STANDALONE_DEPARTURE_MONO,
            fontSize = 13.sp,
            lineHeight = 17.sp,
        )
    }

    CollapsibleSectionTitle(
        title = "Установленные",
        expanded = installedExpanded,
        onToggle = {
            onInstalledExpandedChange(!installedExpanded)
        },
    )

    AnimatedVisibility(
        visible = installedExpanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (installed.isEmpty()) {
                EmptySectionText("Установленных аддонов пока нет")
            } else {
                installed.forEach { card ->
                    InstalledAddonItem(
                        card = card,
                        expanded =
                            state.selectedAddonId == card.addonId,
                        theme = theme,
                        onToggle = {
                            onSelectAddon(card.addonId)
                        },
                        onOpen = {
                            onOpenAddon(card.addonId)
                        },
                        onInfo = {
                            onShowInfo(card.addonId)
                        },
                        onCheckForUpdates = {
                            onCheckForUpdates(card.addonId)
                        },
                        onUninstall = {
                            onUninstallAddon(card.addonId)
                        },
                    )
                }
            }
        }
    }

    SectionTitle("Доступные к скачиванию")

    if (available.isEmpty()) {
        EmptySectionText("Новых аддонов в каталоге пока нет")
    } else {
        available.forEach { card ->
            AvailableAddonItem(
                card = card,
                onInstallOrCancel = {
                    onInstallAddon(card.addonId)
                },
            )
        }
    }

    Spacer(modifier = Modifier.height(10.dp))
}

@Composable
private fun CollapsibleSectionTitle(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionTitle(title)
        KsenaxPressableBox(
            onClick = onToggle,
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) { pressed ->
            Text(
                text = if (expanded) "▼" else "▶",
                color = AddonsMuted.whileKsenaxPressed(pressed),
                fontFamily =
                    KsenaxFontFamily.STANDALONE_DEPARTURE_MONO,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        color = AddonsMuted,
        fontFamily = KsenaxFontFamily.STANDALONE_DEPARTURE_MONO,
        fontSize = 13.sp,
        lineHeight = 16.sp,
    )
}

@Composable
private fun EmptySectionText(text: String) {
    Text(
        text = text,
        color = AddonsMuted.copy(alpha = 0.72f),
        fontFamily = KsenaxFontFamily.STANDALONE_DEPARTURE_MONO,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun InstalledAddonItem(
    card: AddonCardUiModel,
    expanded: Boolean,
    theme: KsenaxThemeVisuals,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onInfo: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onUninstall: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AddonBanner(
            card = card,
            colorized = true,
            onClick = onToggle,
        )
        AddonIdentityLine(
            card = card,
            showDescription = true,
        )

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(220),
            ) + fadeIn(),
            exit = shrinkVertically(
                animationSpec = tween(180),
            ) + fadeOut(),
        ) {
            InstalledAddonActions(
                card = card,
                theme = theme,
                onOpen = onOpen,
                onInfo = onInfo,
                onCheckForUpdates = onCheckForUpdates,
                onUninstall = onUninstall,
            )
        }
    }
}

@Composable
private fun AvailableAddonItem(
    card: AddonCardUiModel,
    onInstallOrCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AddonBanner(
            card = card,
            colorized = false,
            onClick = onInstallOrCancel,
            enabled = card.canInstall || card.isInstallInProgress,
            showDownloadChrome = true,
        )
        AnimatedVisibility(
            visible = card.isInstallConfirmationVisible,
            enter = expandVertically(
                animationSpec = tween(190),
            ) + fadeIn(),
            exit = shrinkVertically(
                animationSpec = tween(150),
            ) + fadeOut(),
        ) {
            AvailableAddonDescription(card)
        }
        if (
            !card.canInstall &&
            !card.isInstallInProgress &&
            !card.hasInstallFailure
        ) {
            Text(
                text = card.compatibilityLabel,
                color = AddonsWarning,
                fontFamily =
                    KsenaxFontFamily.STANDALONE_DEPARTURE_MONO,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }
    }
}

@Composable
private fun AvailableAddonDescription(card: AddonCardUiModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AddonsSurface)
            .border(
                width = 1.dp,
                color = AddonsBorder,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = card.title,
            color = Color.White,
            fontFamily =
                KsenaxFontFamily
                    .LOGOS_AND_HEADLINES_JERSEY_10_REGULAR,
            fontSize = 22.sp,
            lineHeight = 24.sp,
        )
        Text(
            text = card.description,
            color = AddonsMuted,
            fontFamily =
                KsenaxFontFamily.STANDALONE_DEPARTURE_MONO,
            fontSize = 11.sp,
            lineHeight = 15.sp,
        )
    }
}

@Composable
private fun AddonsEarlyTestingBottomBar() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AddonsSurface)
            .navigationBarsPadding(),
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(AddonsBorder),
        )
        Text(
            text = "Находится на раннем тестировании, потенциально будет " +
                "развиваться для разработки извне",
            color = AddonsMuted.copy(alpha = 0.62f),
            fontFamily =
                KsenaxFontFamily.STANDALONE_DEPARTURE_MONO,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun AddonIdentityLine(
    card: AddonCardUiModel,
    showDescription: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = card.title,
                color = Color.White,
                fontFamily =
                    KsenaxFontFamily
                        .LOGOS_AND_HEADLINES_JERSEY_10_REGULAR,
                fontSize = 24.sp,
                lineHeight = 25.sp,
            )
            if (showDescription) {
                Text(
                    text = card.description,
                    color = AddonsMuted,
                    fontFamily =
                        KsenaxFontFamily.STANDALONE_DEPARTURE_MONO,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
        }
        Text(
            text = card.versionLabel,
            color = AddonsMuted,
            fontFamily =
                KsenaxFontFamily.STANDALONE_DEPARTURE_MONO,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun InstalledAddonActions(
    card: AddonCardUiModel,
    theme: KsenaxThemeVisuals,
    onOpen: () -> Unit,
    onInfo: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onUninstall: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AddonsSurface)
            .border(1.dp, AddonsBorder, RoundedCornerShape(10.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PixelActionButton(
            text = "Активировать аддон",
            color = AddonsGreen,
            enabled = card.canOpen,
            onClick = onOpen,
        )
        PixelActionButton(
            text = "Посмотреть информацию",
            onClick = onInfo,
        )
        PixelActionButton(
            text = "Проверить обновления",
            enabled = !card.isCheckingUpdate,
            trailing = if (card.isCheckingUpdate) {
                {
                    PixelGradientSpinner(
                        gradientColors = theme.markerColors
                            .ifEmpty {
                                listOf(
                                    theme.mutedColor,
                                    theme.accentColor,
                                )
                            },
                        modifier = Modifier.size(24.dp),
                    )
                }
            } else {
                null
            },
            onClick = onCheckForUpdates,
        )
        card.updateMessage?.let { message ->
            Text(
                text = message,
                color = if (
                    message.startsWith("Доступно обновление")
                ) {
                    AddonsWarning
                } else {
                    AddonsGreen
                },
                fontFamily =
                    KsenaxFontFamily.STANDALONE_DEPARTURE_MONO,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        PixelActionButton(
            text = "Удалить аддон",
            color = AddonsDanger,
            enabled = card.canUninstall,
            onClick = onUninstall,
        )
    }
}

@Composable
private fun PixelActionButton(
    text: String,
    onClick: () -> Unit,
    color: Color = Color.White,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    KsenaxPressableBox(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) { pressed ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(7.dp))
                .background(Color.Black.copy(alpha = 0.2f))
                .border(
                    1.dp,
                    AddonsBorder.whileKsenaxPressed(pressed),
                    RoundedCornerShape(7.dp),
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                color = (
                    if (enabled) color else AddonsMuted.copy(alpha = 0.5f)
                    ).whileKsenaxPressed(pressed),
                fontFamily =
                    KsenaxFontFamily.STANDALONE_DEPARTURE_MONO,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
            trailing?.invoke()
        }
    }
}

@Composable
private fun AddonBanner(
    card: AddonCardUiModel,
    colorized: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    showDownloadChrome: Boolean = false,
) {
    val saturation by animateFloatAsState(
        targetValue = if (colorized) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "addon_banner_saturation",
    )

    KsenaxPressableBox(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(AddonBannerContract.ASPECT_RATIO)
            .clip(RoundedCornerShape(12.dp)),
    ) { pressed ->
        when (val banner = card.banner) {
            is AddonBannerUiState.Ready -> {
                val image = remember(banner.bitmap) {
                    banner.bitmap.asImageBitmap()
                }
                Image(
                    bitmap = image,
                    contentDescription = "${card.title} banner",
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.colorMatrix(
                        ColorMatrix().apply {
                            setToSaturation(saturation)
                        },
                    ),
                    modifier = Modifier.fillMaxSize(),
                )
            }

            is AddonBannerUiState.Loading,
            is AddonBannerUiState.Unavailable,
            -> AddonBannerFallback(
                packageName = card.packageName,
                isLoading =
                    banner is AddonBannerUiState.Loading,
            )
        }

        if (
            pressed ||
            card.isInstallConfirmationVisible ||
            card.installState !is AddonInstallUiState.Idle
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Color.Black.copy(
                            alpha = if (
                                card.isInstallConfirmationVisible
                            ) {
                                0.84f
                            } else if (
                                card.installState !is
                                AddonInstallUiState.Idle
                            ) {
                                0.52f
                            } else {
                                0.2f
                            },
                        ),
                    ),
            )
        }

        if (card.isInstallConfirmationVisible) {
            Text(
                text = "Нажмите еще раз для установки",
                color = Color.White,
                fontFamily =
                    KsenaxFontFamily.STANDALONE_DEPARTURE_MONO,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 22.dp),
            )
        }

        if (showDownloadChrome) {
            DownloadArrow(
                color = when (val install = card.installState) {
                    is AddonInstallUiState.Active ->
                        if (
                            install.connectionQuality ==
                            AddonInstallConnectionQuality.WEAK
                        ) {
                            AddonsGold
                        } else {
                            AddonsMuted
                        }

                    is AddonInstallUiState.Failed -> AddonsDanger
                    AddonInstallUiState.Idle -> AddonsMuted
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp),
            )
        }

        when (val install = card.installState) {
            is AddonInstallUiState.Active -> {
                val isWeak =
                    install.connectionQuality ==
                        AddonInstallConnectionQuality.WEAK
                AddonInstallProgressOverlay(
                    fraction = install.progress.fraction
                        ?: if (
                            install.progress.stage ==
                            AddonInstallStage.DOWNLOADING
                        ) {
                            0f
                        } else {
                            1f
                        },
                    color = if (isWeak) AddonsGold else AddonsGreen,
                    message = if (isWeak) {
                        "Слабый Интернет"
                    } else {
                        "Аддон устанавливается"
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp),
                )
            }

            is AddonInstallUiState.Failed -> {
                when (install.reason) {
                    AddonInstallFailureUiReason.NO_INTERNET ->
                        AddonInstallProgressOverlay(
                            fraction = 1f,
                            color = AddonsDanger,
                            message =
                                "Интернета нет. Нажмите рефреш-кнопку",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 18.dp),
                        )
                }
            }

            AddonInstallUiState.Idle -> Unit
        }
    }
}

@Composable
private fun DownloadArrow(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "↓",
        color = color,
        fontFamily = KsenaxFontFamily.STANDALONE_DEPARTURE_MONO,
        fontSize = 27.sp,
        modifier = modifier,
    )
}

@Composable
private fun AddonInstallProgressOverlay(
    fraction: Float,
    color: Color,
    message: String,
    modifier: Modifier = Modifier,
) {
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 160),
        label = "addon_wavy_download_progress",
    )

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val right = size.width
            val centerY = size.height * 0.74f
            val pixelSize = 3.dp.toPx()

            fun drawPixelWave(
                waveColor: Color,
                maxRight: Float,
            ) {
                var x = 0f
                var column = 0
                while (x + pixelSize <= maxRight) {
                    val row = PixelWaveRows[
                        column % PixelWaveRows.size
                    ]
                    drawRect(
                        color = waveColor,
                        topLeft = Offset(
                            x = x,
                            y = centerY +
                                row * pixelSize -
                                pixelSize / 2f,
                        ),
                        size = Size(pixelSize, pixelSize),
                    )
                    x += pixelSize
                    column += 1
                }
            }

            drawPixelWave(
                waveColor = AddonsMuted.copy(alpha = 0.72f),
                maxRight = right,
            )
            drawPixelWave(
                waveColor = color,
                maxRight = right * animatedFraction,
            )
        }

        Text(
            text = message,
            color = color,
            fontFamily =
                KsenaxFontFamily.STANDALONE_DEPARTURE_MONO,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 42.dp),
        )
    }
}

@Composable
private fun AddonBannerFallback(
    packageName: String,
    isLoading: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AddonsSurface)
            .dashedBannerBorder(AddonsMuted),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = packageName,
            color = if (isLoading) AddonsMuted else Color.White,
            fontFamily =
                KsenaxFontFamily.STANDALONE_DEPARTURE_MONO,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}

private fun Modifier.dashedBannerBorder(
    color: Color,
): Modifier = drawWithCache {
    val strokeWidth = 1.5.dp.toPx()
    val inset = strokeWidth / 2f
    val cornerRadius = 12.dp.toPx()
    val dash = PathEffect.dashPathEffect(
        intervals = floatArrayOf(
            10.dp.toPx(),
            7.dp.toPx(),
        ),
    )

    onDrawBehind {
        drawRoundRect(
            color = color,
            topLeft = Offset(inset, inset),
            size = Size(
                width = size.width - strokeWidth,
                height = size.height - strokeWidth,
            ),
            cornerRadius = CornerRadius(
                x = cornerRadius,
                y = cornerRadius,
            ),
            style = Stroke(
                width = strokeWidth,
                pathEffect = dash,
            ),
        )
    }
}

@Composable
private fun AddonInfoOverlay(
    info: AddonInfoUiModel,
    onDismiss: () -> Unit,
) {
    val dateFormat = remember {
        DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM,
            DateFormat.SHORT,
        )
    }
    val installedAt = remember(
        info.installedAtEpochMillis,
    ) {
        dateFormat.format(Date(info.installedAtEpochMillis))
    }
    val updatedAt = remember(
        info.lastUpdatedAtEpochMillis,
    ) {
        dateFormat.format(Date(info.lastUpdatedAtEpochMillis))
    }
    val interactionSource = remember {
        MutableInteractionSource()
    }
    val uriHandler = LocalUriHandler.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onDismiss,
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(AddonsSurface)
                .border(
                    1.dp,
                    AddonsBorder,
                    RoundedCornerShape(14.dp),
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = info.title,
                color = Color.White,
                fontFamily =
                    KsenaxFontFamily
                        .LOGOS_AND_HEADLINES_JERSEY_10_REGULAR,
                fontSize = 31.sp,
                lineHeight = 33.sp,
            )
            InfoLine("PACKAGE", info.packageName)
            InfoLine("VERSION", info.versionLabel)
            InfoLine("УСТАНОВЛЕН", installedAt)
            if (
                info.lastUpdatedAtEpochMillis !=
                info.installedAtEpochMillis
            ) {
                InfoLine("ОБНОВЛЁН", updatedAt)
            }
            Text(
                text = info.fullDescription,
                color = AddonsMuted,
                fontFamily =
                    KsenaxFontFamily.STANDALONE_DEPARTURE_MONO,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            if (info.requiredCapabilities.isNotEmpty()) {
                InfoLine(
                    "CAPABILITIES",
                    info.requiredCapabilities.joinToString(),
                )
            }
            info.repositoryUrl?.let { url ->
                Text(
                    text = "REPOSITORY · $url",
                    color = AddonsLink,
                    fontFamily =
                        KsenaxFontFamily.STANDALONE_DEPARTURE_MONO,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.clickable {
                        runCatching {
                            uriHandler.openUri(url)
                        }
                    },
                )
            }
            Text(
                text = "Нажмите в любую точку, чтобы закрыть",
                color = AddonsGreen,
                fontFamily =
                    KsenaxFontFamily.STANDALONE_DEPARTURE_MONO,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Text(
        text = "$label · $value",
        color = AddonsMuted,
        fontFamily = KsenaxFontFamily.STANDALONE_DEPARTURE_MONO,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    )
}
