// SPDX-FileCopyrightText: 2026 quendoris
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.quendoris.aeris.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewportGenerationGateTest {
    @Test
    fun stale_generation_never_reaches_renderer() {
        val gate = ViewportGenerationGate()
        val first = gate.beginViewport()
        val second = gate.beginViewport()

        assertFalse(
            gate.tryAccept(
                RenderBatchStamp(first, RenderBatchChannel.DetailGeometry, sequence = 5),
            ),
        )
        assertTrue(
            gate.tryAccept(
                RenderBatchStamp(second, RenderBatchChannel.DetailGeometry, sequence = 0),
            ),
        )
    }

    @Test
    fun channels_progress_independently_inside_one_viewport() {
        val gate = ViewportGenerationGate()
        val generation = gate.beginViewport()

        assertTrue(gate.tryAccept(RenderBatchStamp(generation, RenderBatchChannel.BaseGeometry, 0)))
        assertTrue(gate.tryAccept(RenderBatchStamp(generation, RenderBatchChannel.Labels, 0)))
        assertTrue(gate.tryAccept(RenderBatchStamp(generation, RenderBatchChannel.PrivateAnnotations, 0)))
        assertTrue(gate.tryAccept(RenderBatchStamp(generation, RenderBatchChannel.Labels, 1)))
    }

    @Test
    fun older_sequence_cannot_overwrite_newer_refinement() {
        val gate = ViewportGenerationGate()
        val generation = gate.beginViewport()

        assertTrue(gate.tryAccept(RenderBatchStamp(generation, RenderBatchChannel.Labels, 4)))
        assertFalse(gate.tryAccept(RenderBatchStamp(generation, RenderBatchChannel.Labels, 3)))
        assertFalse(gate.tryAccept(RenderBatchStamp(generation, RenderBatchChannel.Labels, 4)))
        assertTrue(gate.tryAccept(RenderBatchStamp(generation, RenderBatchChannel.Labels, 5)))
    }

    @Test
    fun project_close_invalidates_every_outstanding_batch() {
        val gate = ViewportGenerationGate()
        val active = gate.beginViewport()
        val invalidated = gate.invalidate()

        assertEquals(active + 1L, invalidated)
        assertFalse(gate.isCurrent(active))
        assertFalse(gate.tryAccept(RenderBatchStamp(active, RenderBatchChannel.BaseGeometry, 0)))
    }

    @Test
    fun malformed_stamps_fail_closed() {
        val gate = ViewportGenerationGate()
        val generation = gate.beginViewport()

        assertFalse(gate.tryAccept(RenderBatchStamp(0, RenderBatchChannel.BaseGeometry, 0)))
        assertFalse(gate.tryAccept(RenderBatchStamp(generation, RenderBatchChannel.BaseGeometry, -1)))
    }

    @Test
    fun work_budget_requires_positive_hard_limits() {
        val defaults = ViewportWorkBudget()
        assertTrue(defaults.maxGeometryVertices > 0)
        assertTrue(defaults.maxDecodedBytes > 0L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalid_work_budget_is_rejected() {
        ViewportWorkBudget(maxPrivateObjects = 0)
    }
}
