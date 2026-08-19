// SPDX-FileCopyrightText: 2026 quendoris
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.quendoris.aeris.routing

/**
 * Location access is always initiated by an explicit routing/navigation action.
 */
enum class LocationTrigger {
    None,
    UseMyLocation,
    StartActiveNavigation,
}

data class LocationPermissionDecision(
    val requestForegroundSessionLocation: Boolean,
    val acceptApproximateLocation: Boolean,
    val requestPreciseLocation: Boolean,
    val requestBackgroundLocation: Boolean,
)

object RoutingLocationPolicy {
    /**
     * Baseline AERIS never asks for background location. Precise location is an
     * opt-in refinement only when the active feature actually benefits from it.
     */
    fun permissionDecision(
        trigger: LocationTrigger,
        userOptedIntoPrecise: Boolean = false,
        activeFeatureBenefitsFromPrecise: Boolean = false,
    ): LocationPermissionDecision = when (trigger) {
        LocationTrigger.None -> LocationPermissionDecision(
            requestForegroundSessionLocation = false,
            acceptApproximateLocation = true,
            requestPreciseLocation = false,
            requestBackgroundLocation = false,
        )

        LocationTrigger.UseMyLocation,
        LocationTrigger.StartActiveNavigation,
        -> LocationPermissionDecision(
            requestForegroundSessionLocation = true,
            acceptApproximateLocation = true,
            requestPreciseLocation = userOptedIntoPrecise && activeFeatureBenefitsFromPrecise,
            requestBackgroundLocation = false,
        )
    }
}

enum class RouteHistoryRetentionKind {
    Disabled,
    KeepUntilDeleted,
    DeleteAfterDays,
}

data class RouteHistoryRetention(
    val kind: RouteHistoryRetentionKind,
    val days: Int? = null,
) {
    init {
        when (kind) {
            RouteHistoryRetentionKind.DeleteAfterDays -> require(days != null && days > 0) {
                "DeleteAfterDays requires a positive day count"
            }

            RouteHistoryRetentionKind.Disabled,
            RouteHistoryRetentionKind.KeepUntilDeleted,
            -> require(days == null) { "this retention mode must not carry a day count" }
        }
    }
}

enum class RouteHistoryStorageDecision {
    /** User disabled history entirely. */
    DoNotPersist,

    /** Vault is unlocked/available; sensitive history may be persisted there. */
    PersistInPrivateVault,

    /** No vault means no plaintext fallback; keep only current in-memory state. */
    VolatileOnly,
}

object RouteHistoryPrivacyPolicy {
    fun storageDecision(
        retention: RouteHistoryRetention,
        privateVaultAvailable: Boolean,
    ): RouteHistoryStorageDecision {
        if (retention.kind == RouteHistoryRetentionKind.Disabled) {
            return RouteHistoryStorageDecision.DoNotPersist
        }
        return if (privateVaultAvailable) {
            RouteHistoryStorageDecision.PersistInPrivateVault
        } else {
            RouteHistoryStorageDecision.VolatileOnly
        }
    }

    /**
     * A current-location sample is process-memory-only unless the user chooses
     * to persist a history/saved-route object and APV is available.
     */
    fun mayPersistLocationDerivedEndpoint(
        userRequestedSave: Boolean,
        retention: RouteHistoryRetention,
        privateVaultAvailable: Boolean,
    ): Boolean =
        userRequestedSave &&
            retention.kind != RouteHistoryRetentionKind.Disabled &&
            privateVaultAvailable
}
