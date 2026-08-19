// SPDX-FileCopyrightText: 2026 quendoris
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.quendoris.aeris.routing

/**
 * Transport-data capabilities are independent. A source may provide only a
 * subset (for example current schedules without live vehicle positions).
 */
enum class TransitCapability {
    StaticNetwork,
    Stops,
    Schedule,
    LiveTripUpdates,
    LiveVehiclePositions,
    ServiceAlerts,
    Fares,
    HistoricalSnapshots,
}

enum class RealtimeKind {
    TripOrVehicle,
    ServiceAlert,
}

/**
 * User-facing quality state. `HistoricalOnly` and `Unavailable` explicitly
 * mean that AERIS must not offer the data as current service.
 */
enum class TransitAvailability {
    Live,
    ScheduledCurrent,
    ScheduledNoLive,
    HistoricalOnly,
    Stale,
    Unavailable,
    SourceError,
}

data class TransitFreshnessEvidence(
    val nowEpochSeconds: Long,
    val scheduleValidAtQueryTime: Boolean,
    val realtimeCapabilityDeclared: Boolean = false,
    val realtimeKind: RealtimeKind = RealtimeKind.TripOrVehicle,
    val realtimeSourceTimestampEpochSeconds: Long? = null,
    val lastTrustworthyScheduleUpdateEpochSeconds: Long? = null,
    val historicalSnapshotAvailable: Boolean = false,
    val sourceFailed: Boolean = false,
)

data class TransitFreshnessResult(
    val availability: TransitAvailability,
    val realtimeAgeSeconds: Long? = null,
    val currentServiceUsable: Boolean,
    val diagnostic: String,
)

/**
 * Pure classifier shared by routing UI/adapters.
 *
 * It intentionally does not fetch data and does not infer predictions. Source
 * adapters provide trustworthy timestamps/validity evidence; this class only
 * decides what AERIS is allowed to call current/live.
 */
object TransitFreshnessClassifier {
    // GTFS Realtime best-practice upper ages for user-facing realtime data.
    const val TRIP_OR_VEHICLE_MAX_AGE_SECONDS: Long = 90
    const val SERVICE_ALERT_MAX_AGE_SECONDS: Long = 10 * 60

    // Product-level hard ceiling requested for current-service claims when no
    // stronger feed/service validity exists.
    const val CURRENT_DATA_HARD_CEILING_SECONDS: Long = 365L * 24L * 60L * 60L

    // Small clock skew is tolerated; a materially future-dated source is bad
    // evidence rather than "fresh" data.
    private const val MAX_FUTURE_CLOCK_SKEW_SECONDS: Long = 30

    fun classify(evidence: TransitFreshnessEvidence): TransitFreshnessResult {
        if (evidence.nowEpochSeconds < 0) {
            return sourceError("invalid current timestamp")
        }

        val realtimeTimestamp = evidence.realtimeSourceTimestampEpochSeconds
        if (realtimeTimestamp != null) {
            if (realtimeTimestamp < 0) {
                return sourceError("invalid realtime timestamp")
            }
            val age = evidence.nowEpochSeconds - realtimeTimestamp
            if (age < -MAX_FUTURE_CLOCK_SKEW_SECONDS) {
                return sourceError("realtime source timestamp is materially in the future")
            }

            val threshold = when (evidence.realtimeKind) {
                RealtimeKind.TripOrVehicle -> TRIP_OR_VEHICLE_MAX_AGE_SECONDS
                RealtimeKind.ServiceAlert -> SERVICE_ALERT_MAX_AGE_SECONDS
            }
            if (age <= threshold) {
                return TransitFreshnessResult(
                    availability = TransitAvailability.Live,
                    realtimeAgeSeconds = age.coerceAtLeast(0),
                    currentServiceUsable = true,
                    diagnostic = "realtime evidence is within the capability freshness window",
                )
            }
        }

        if (evidence.scheduleValidAtQueryTime) {
            return TransitFreshnessResult(
                availability = if (evidence.realtimeCapabilityDeclared) {
                    TransitAvailability.ScheduledNoLive
                } else {
                    TransitAvailability.ScheduledCurrent
                },
                realtimeAgeSeconds = realtimeTimestamp?.let {
                    (evidence.nowEpochSeconds - it).coerceAtLeast(0)
                },
                currentServiceUsable = true,
                diagnostic = if (evidence.realtimeCapabilityDeclared) {
                    "current schedule is valid but realtime evidence is absent/stale"
                } else {
                    "current schedule is valid; no realtime capability is declared"
                },
            )
        }

        if (evidence.historicalSnapshotAvailable) {
            return TransitFreshnessResult(
                availability = TransitAvailability.HistoricalOnly,
                currentServiceUsable = false,
                diagnostic = "only historical transport data is available",
            )
        }

        if (evidence.sourceFailed) {
            return sourceError("transport source failed and no current fallback is valid")
        }

        val scheduleUpdate = evidence.lastTrustworthyScheduleUpdateEpochSeconds
        if (scheduleUpdate != null) {
            if (scheduleUpdate < 0 || scheduleUpdate > evidence.nowEpochSeconds + MAX_FUTURE_CLOCK_SKEW_SECONDS) {
                return sourceError("invalid schedule update timestamp")
            }
            val age = (evidence.nowEpochSeconds - scheduleUpdate).coerceAtLeast(0)
            if (age > CURRENT_DATA_HARD_CEILING_SECONDS) {
                return TransitFreshnessResult(
                    availability = TransitAvailability.Unavailable,
                    currentServiceUsable = false,
                    diagnostic = "last trustworthy transport update is older than the current-data hard ceiling",
                )
            }
            return TransitFreshnessResult(
                availability = TransitAvailability.Stale,
                currentServiceUsable = false,
                diagnostic = "transport data exists but current service validity cannot be established",
            )
        }

        return TransitFreshnessResult(
            availability = TransitAvailability.Unavailable,
            currentServiceUsable = false,
            diagnostic = "no trustworthy current transport data is available",
        )
    }

    private fun sourceError(diagnostic: String) = TransitFreshnessResult(
        availability = TransitAvailability.SourceError,
        currentServiceUsable = false,
        diagnostic = diagnostic,
    )
}
