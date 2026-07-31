package com.kolesnikovprod.ksetaorch.addons.presentation

import dev.openksenax.addons.contract.AddonId

/**
 * Полное immutable-состояние host-каталога аддонов.
 *
 * @since 0.3
 */
internal data class AddonCatalogUiState(
    val cards: List<AddonCardUiModel> = emptyList(),
    val selectedAddonId: AddonId? = null,
    val pendingInstallConfirmationId: AddonId? = null,
    val isRefreshing: Boolean = false,
    val actionMessage: String? = null,
    val infoOverlay: AddonInfoUiModel? = null,
) {
    val selectedCard: AddonCardUiModel?
        get() = cards.firstOrNull { card -> card.addonId == selectedAddonId }
}

/**
 * Данные локального metadata.json для информационного overlay.
 *
 * @since 0.3
 */
internal data class AddonInfoUiModel(
    val addonId: AddonId,
    val title: String,
    val packageName: String,
    val fullDescription: String,
    val versionLabel: String,
    val installedAtEpochMillis: Long,
    val lastUpdatedAtEpochMillis: Long,
    val requiredCapabilities: List<String>,
    val repositoryUrl: String?,
)
