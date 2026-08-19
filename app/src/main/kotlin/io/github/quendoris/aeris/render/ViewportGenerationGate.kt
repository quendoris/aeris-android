// SPDX-FileCopyrightText: 2026 quendoris
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.quendoris.aeris.render

import java.util.EnumMap

/**
 * Render channels can complete independently. Their payloads are deliberately
 * not modeled here; this class owns only ordering/freshness semantics.
 */
enum class RenderBatchChannel {
    BaseGeometry,
    DetailGeometry,
    Labels,
    PrivateAnnotations,
    Selection,
}

data class RenderBatchStamp(
    val viewportGeneration: Long,
    val channel: RenderBatchChannel,
    val sequence: Long,
)

/**
 * Tiny thread-safe admission gate between asynchronous viewport workers and the
 * renderer.
 *
 * A new camera/query state starts a new generation. Results from older
 * generations are rejected even if disk/network/decrypt/projection work finishes
 * later. Within one generation each channel is monotonic as well, so a slower
 * early refinement cannot overwrite a newer batch from the same worker family.
 */
class ViewportGenerationGate {
    private var currentGeneration = 0L
    private val acceptedSequence = EnumMap<RenderBatchChannel, Long>(RenderBatchChannel::class.java)

    @Synchronized
    fun beginViewport(): Long {
        check(currentGeneration != Long.MAX_VALUE) {
            "viewport generation counter exhausted"
        }
        currentGeneration += 1L
        acceptedSequence.clear()
        return currentGeneration
    }

    @Synchronized
    fun current(): Long = currentGeneration

    @Synchronized
    fun isCurrent(generation: Long): Boolean =
        generation > 0L && generation == currentGeneration

    @Synchronized
    fun tryAccept(stamp: RenderBatchStamp): Boolean {
        if (stamp.viewportGeneration <= 0L || stamp.sequence < 0L) return false
        if (stamp.viewportGeneration != currentGeneration) return false

        val previous = acceptedSequence[stamp.channel]
        if (previous != null && stamp.sequence <= previous) return false

        acceptedSequence[stamp.channel] = stamp.sequence
        return true
    }

    /**
     * Invalidates all outstanding work without starting a renderable viewport.
     * Used when a project is closed or the surface loses ownership.
     */
    @Synchronized
    fun invalidate(): Long {
        check(currentGeneration != Long.MAX_VALUE) {
            "viewport generation counter exhausted"
        }
        currentGeneration += 1L
        acceptedSequence.clear()
        return currentGeneration
    }
}

data class ViewportWorkBudget(
    val maxGeometryVertices: Int = 250_000,
    val maxLabelCandidates: Int = 2_000,
    val maxPrivateObjects: Int = 2_000,
    val maxDecodedBytes: Long = 32L * 1024L * 1024L,
) {
    init {
        require(maxGeometryVertices > 0)
        require(maxLabelCandidates > 0)
        require(maxPrivateObjects > 0)
        require(maxDecodedBytes > 0L)
    }
}
