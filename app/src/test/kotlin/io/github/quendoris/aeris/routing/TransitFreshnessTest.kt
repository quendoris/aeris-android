// SPDX-FileCopyrightText: 2026 quendoris
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.quendoris.aeris.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitFreshnessTest {
    private val now = 2_000_000_000L

    @Test
    fun freshVehicleEvidenceIsLive() {
        val result = TransitFreshnessClassifier.classify(
            TransitFreshnessEvidence(
                nowEpochSeconds = now,
                scheduleValidAtQueryTime = true,
                realtimeCapabilityDeclared = true,
                realtimeKind = RealtimeKind.TripOrVehicle,
                realtimeSourceTimestampEpochSeconds = now - 45,
            ),
        )

        assertEquals(TransitAvailability.Live, result.availability)
        assertEquals(45L, result.realtimeAgeSeconds)
        assertTrue(result.currentServiceUsable)
    }

    @Test
    fun staleRealtimeFallsBackToValidSchedule() {
        val result = TransitFreshnessClassifier.classify(
            TransitFreshnessEvidence(
                nowEpochSeconds = now,
                scheduleValidAtQueryTime = true,
                realtimeCapabilityDeclared = true,
                realtimeSourceTimestampEpochSeconds =
                    now - TransitFreshnessClassifier.TRIP_OR_VEHICLE_MAX_AGE_SECONDS - 1,
            ),
        )

        assertEquals(TransitAvailability.ScheduledNoLive, result.availability)
        assertTrue(result.currentServiceUsable)
    }

    @Test
    fun scheduleWithoutRealtimeCapabilityIsCurrentScheduled() {
        val result = TransitFreshnessClassifier.classify(
            TransitFreshnessEvidence(
                nowEpochSeconds = now,
                scheduleValidAtQueryTime = true,
            ),
        )

        assertEquals(TransitAvailability.ScheduledCurrent, result.availability)
        assertTrue(result.currentServiceUsable)
    }

    @Test
    fun historicalSnapshotNeverMasqueradesAsCurrent() {
        val result = TransitFreshnessClassifier.classify(
            TransitFreshnessEvidence(
                nowEpochSeconds = now,
                scheduleValidAtQueryTime = false,
                historicalSnapshotAvailable = true,
            ),
        )

        assertEquals(TransitAvailability.HistoricalOnly, result.availability)
        assertFalse(result.currentServiceUsable)
    }

    @Test
    fun ancientUnvalidatedScheduleBecomesUnavailable() {
        val result = TransitFreshnessClassifier.classify(
            TransitFreshnessEvidence(
                nowEpochSeconds = now,
                scheduleValidAtQueryTime = false,
                lastTrustworthyScheduleUpdateEpochSeconds =
                    now - TransitFreshnessClassifier.CURRENT_DATA_HARD_CEILING_SECONDS - 1,
            ),
        )

        assertEquals(TransitAvailability.Unavailable, result.availability)
        assertFalse(result.currentServiceUsable)
    }

    @Test
    fun recentButUnvalidatedScheduleIsStaleNotCurrent() {
        val result = TransitFreshnessClassifier.classify(
            TransitFreshnessEvidence(
                nowEpochSeconds = now,
                scheduleValidAtQueryTime = false,
                lastTrustworthyScheduleUpdateEpochSeconds = now - 86_400,
            ),
        )

        assertEquals(TransitAvailability.Stale, result.availability)
        assertFalse(result.currentServiceUsable)
    }

    @Test
    fun materiallyFutureRealtimeTimestampFailsClosed() {
        val result = TransitFreshnessClassifier.classify(
            TransitFreshnessEvidence(
                nowEpochSeconds = now,
                scheduleValidAtQueryTime = true,
                realtimeCapabilityDeclared = true,
                realtimeSourceTimestampEpochSeconds = now + 31,
            ),
        )

        assertEquals(TransitAvailability.SourceError, result.availability)
        assertFalse(result.currentServiceUsable)
    }
}
