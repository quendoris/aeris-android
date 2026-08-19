// SPDX-FileCopyrightText: 2026 quendoris
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.quendoris.aeris.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingPrivacyPolicyTest {
    @Test
    fun browsingDoesNotRequestLocation() {
        val decision = RoutingLocationPolicy.permissionDecision(LocationTrigger.None)

        assertFalse(decision.requestForegroundSessionLocation)
        assertFalse(decision.requestPreciseLocation)
        assertFalse(decision.requestBackgroundLocation)
    }

    @Test
    fun useMyLocationRequestsForegroundSessionOnly() {
        val decision = RoutingLocationPolicy.permissionDecision(LocationTrigger.UseMyLocation)

        assertTrue(decision.requestForegroundSessionLocation)
        assertTrue(decision.acceptApproximateLocation)
        assertFalse(decision.requestPreciseLocation)
        assertFalse(decision.requestBackgroundLocation)
    }

    @Test
    fun preciseLocationRequiresUserOptInAndFeatureBenefit() {
        val noOptIn = RoutingLocationPolicy.permissionDecision(
            trigger = LocationTrigger.StartActiveNavigation,
            userOptedIntoPrecise = false,
            activeFeatureBenefitsFromPrecise = true,
        )
        val noBenefit = RoutingLocationPolicy.permissionDecision(
            trigger = LocationTrigger.StartActiveNavigation,
            userOptedIntoPrecise = true,
            activeFeatureBenefitsFromPrecise = false,
        )
        val allowed = RoutingLocationPolicy.permissionDecision(
            trigger = LocationTrigger.StartActiveNavigation,
            userOptedIntoPrecise = true,
            activeFeatureBenefitsFromPrecise = true,
        )

        assertFalse(noOptIn.requestPreciseLocation)
        assertFalse(noBenefit.requestPreciseLocation)
        assertTrue(allowed.requestPreciseLocation)
        assertFalse(allowed.requestBackgroundLocation)
    }

    @Test
    fun historyNeverFallsBackToPlaintextWhenVaultUnavailable() {
        val retention = RouteHistoryRetention(RouteHistoryRetentionKind.KeepUntilDeleted)

        assertEquals(
            RouteHistoryStorageDecision.VolatileOnly,
            RouteHistoryPrivacyPolicy.storageDecision(
                retention = retention,
                privateVaultAvailable = false,
            ),
        )
        assertFalse(
            RouteHistoryPrivacyPolicy.mayPersistLocationDerivedEndpoint(
                userRequestedSave = true,
                retention = retention,
                privateVaultAvailable = false,
            ),
        )
    }

    @Test
    fun historyPersistsOnlyInPrivateVault() {
        val retention = RouteHistoryRetention(RouteHistoryRetentionKind.KeepUntilDeleted)

        assertEquals(
            RouteHistoryStorageDecision.PersistInPrivateVault,
            RouteHistoryPrivacyPolicy.storageDecision(
                retention = retention,
                privateVaultAvailable = true,
            ),
        )
        assertTrue(
            RouteHistoryPrivacyPolicy.mayPersistLocationDerivedEndpoint(
                userRequestedSave = true,
                retention = retention,
                privateVaultAvailable = true,
            ),
        )
    }

    @Test
    fun disabledHistoryNeverPersists() {
        val retention = RouteHistoryRetention(RouteHistoryRetentionKind.Disabled)

        assertEquals(
            RouteHistoryStorageDecision.DoNotPersist,
            RouteHistoryPrivacyPolicy.storageDecision(
                retention = retention,
                privateVaultAvailable = true,
            ),
        )
        assertFalse(
            RouteHistoryPrivacyPolicy.mayPersistLocationDerivedEndpoint(
                userRequestedSave = true,
                retention = retention,
                privateVaultAvailable = true,
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun finiteRetentionRequiresPositiveDays() {
        RouteHistoryRetention(
            kind = RouteHistoryRetentionKind.DeleteAfterDays,
            days = 0,
        )
    }
}
