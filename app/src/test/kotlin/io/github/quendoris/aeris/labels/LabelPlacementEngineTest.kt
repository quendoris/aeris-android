// SPDX-FileCopyrightText: 2026 quendoris
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.quendoris.aeris.labels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelPlacementEngineTest {
    private val engine = LabelPlacementEngine(collisionCellPx = 32)

    @Test
    fun privateAnnotationBeatsCollidingCountryLabel() {
        val country = candidate(
            id = "country",
            labelClass = LabelClass.Country,
            x = 100f,
            y = 100f,
        )
        val privatePin = candidate(
            id = "private",
            labelClass = LabelClass.PrivateAnnotation,
            x = 100f,
            y = 100f,
        )

        val placed = engine.place(request(country, privatePin))

        assertEquals(listOf("private"), placed.map { it.candidate.stableId })
    }

    @Test
    fun selectedLabelRemainsVisibleAndSuppressesOrdinaryCollision() {
        val selectedPoi = candidate(
            id = "selected-poi",
            labelClass = LabelClass.Poi,
            x = 120f,
            y = 100f,
            selected = true,
        )
        val country = candidate(
            id = "country",
            labelClass = LabelClass.Country,
            x = 120f,
            y = 100f,
        )

        val placed = engine.place(request(country, selectedPoi))

        assertEquals(listOf("selected-poi"), placed.map { it.candidate.stableId })
    }

    @Test
    fun previouslyAcceptedLabelWinsWithinSamePriorityClass() {
        val previous = candidate(
            id = "previous",
            labelClass = LabelClass.City,
            x = 100f,
            y = 100f,
            importance = 1,
        )
        val challenger = candidate(
            id = "challenger",
            labelClass = LabelClass.City,
            x = 100f,
            y = 100f,
            importance = 100,
        )

        val placed = engine.place(
            request(previous, challenger).copy(
                previouslyAcceptedIds = setOf("previous"),
            ),
        )

        assertEquals(listOf("previous"), placed.map { it.candidate.stableId })
    }

    @Test
    fun zoomRangeFiltersCandidatesBeforeCollisionWork() {
        val city = candidate(
            id = "city",
            labelClass = LabelClass.City,
            x = 80f,
            y = 80f,
            minZoom = 2f,
            maxZoom = 6f,
        )

        assertTrue(engine.place(request(city).copy(zoom = 1f)).isEmpty())
        assertEquals(
            listOf("city"),
            engine.place(request(city).copy(zoom = 4f)).map { it.candidate.stableId },
        )
        assertTrue(engine.place(request(city).copy(zoom = 8f)).isEmpty())
    }

    @Test
    fun deterministicStableIdBreaksExactPriorityTie() {
        val beta = candidate(
            id = "beta",
            labelClass = LabelClass.Region,
            x = 100f,
            y = 100f,
        )
        val alpha = candidate(
            id = "alpha",
            labelClass = LabelClass.Region,
            x = 100f,
            y = 100f,
        )

        val first = engine.place(request(beta, alpha)).map { it.candidate.stableId }
        val second = engine.place(request(alpha, beta)).map { it.candidate.stableId }

        assertEquals(listOf("alpha"), first)
        assertEquals(first, second)
    }

    @Test
    fun oversizedVisibleCandidateIsClippedForGridTraversal() {
        val huge = candidate(
            id = "huge",
            labelClass = LabelClass.Water,
            x = 100f,
            y = 100f,
            width = 1_000_000f,
            height = 1_000_000f,
        )

        val placed = engine.place(request(huge))

        assertEquals(listOf("huge"), placed.map { it.candidate.stableId })
    }

    @Test
    fun maxAcceptedProvidesHardDensityBudget() {
        val labels = (0 until 20).map { index ->
            candidate(
                id = "label-$index",
                labelClass = LabelClass.Poi,
                x = 20f + index * 18f,
                y = 20f,
                width = 8f,
                height = 8f,
            )
        }

        val placed = engine.place(
            LabelPlacementRequest(
                viewportWidth = 500,
                viewportHeight = 200,
                zoom = 4f,
                candidates = labels,
                maxAccepted = 5,
            ),
        )

        assertEquals(5, placed.size)
    }

    private fun request(vararg candidates: LabelCandidate) = LabelPlacementRequest(
        viewportWidth = 320,
        viewportHeight = 240,
        zoom = 4f,
        candidates = candidates.toList(),
    )

    private fun candidate(
        id: String,
        labelClass: LabelClass,
        x: Float,
        y: Float,
        width: Float = 80f,
        height: Float = 28f,
        minZoom: Float = 0f,
        maxZoom: Float = Float.POSITIVE_INFINITY,
        importance: Int = 0,
        selected: Boolean = false,
    ) = LabelCandidate(
        stableId = id,
        labelClass = labelClass,
        anchorX = x,
        anchorY = y,
        width = width,
        height = height,
        minZoom = minZoom,
        maxZoom = maxZoom,
        importance = importance,
        selected = selected,
    )
}
