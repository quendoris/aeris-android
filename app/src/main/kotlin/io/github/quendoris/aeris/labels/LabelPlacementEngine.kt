// SPDX-FileCopyrightText: 2026 quendoris
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.quendoris.aeris.labels

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Screen-space label classes. Geographic/source semantics stay in the AERIS
 * core; this enum only expresses Android renderer placement priority.
 */
enum class LabelClass(val priority: Int) {
    PrivateAnnotation(1_000),
    Country(900),
    Capital(860),
    City(800),
    Region(760),
    MajorRoad(700),
    Road(650),
    Poi(600),
    Water(540),
    BuildingAddress(500),
}

data class LabelCandidate(
    val stableId: String,
    val labelClass: LabelClass,
    val anchorX: Float,
    val anchorY: Float,
    val width: Float,
    val height: Float,
    val minZoom: Float = 0f,
    val maxZoom: Float = Float.POSITIVE_INFINITY,
    val importance: Int = 0,
    val selected: Boolean = false,
)

data class LabelPlacementRequest(
    val viewportWidth: Int,
    val viewportHeight: Int,
    val zoom: Float,
    val candidates: List<LabelCandidate>,
    val previouslyAcceptedIds: Set<String> = emptySet(),
    val maxAccepted: Int = 180,
)

data class PlacedLabel(
    val candidate: LabelCandidate,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/**
 * Bounded deterministic collision scheduler for already-visible label
 * candidates. It never performs source/storage queries and is safe to run on a
 * background render-preparation worker.
 *
 * Candidate generation must already be bounded by viewport + LOD. This engine
 * prevents the remaining screen-space placement step from degrading into an
 * all-pairs collision scan by indexing accepted rectangles in a fixed grid.
 */
class LabelPlacementEngine(
    private val collisionCellPx: Int = 64,
) {
    init {
        require(collisionCellPx > 0) { "collision cell size must be positive" }
    }

    fun place(request: LabelPlacementRequest): List<PlacedLabel> {
        if (request.viewportWidth <= 0 || request.viewportHeight <= 0) return emptyList()
        if (!request.zoom.isFinite() || request.zoom < 0f) return emptyList()
        if (request.maxAccepted <= 0) return emptyList()

        val ordered = request.candidates
            .asSequence()
            .filter { candidate -> isCandidateValid(candidate, request.zoom) }
            .mapNotNull { candidate ->
                candidateBounds(candidate)?.takeIf { bounds ->
                    bounds.intersectsViewport(request.viewportWidth, request.viewportHeight)
                }?.let { bounds -> CandidateWithBounds(candidate, bounds) }
            }
            .sortedWith(
                compareByDescending<CandidateWithBounds> { it.candidate.selected }
                    .thenByDescending { it.candidate.labelClass.priority }
                    .thenByDescending {
                        request.previouslyAcceptedIds.contains(it.candidate.stableId)
                    }
                    .thenByDescending { it.candidate.importance }
                    .thenBy { it.candidate.stableId },
            )
            .toList()

        val accepted = ArrayList<PlacedLabel>(min(request.maxAccepted, ordered.size))
        val collisionGrid = HashMap<Long, MutableList<Int>>()

        for (item in ordered) {
            if (accepted.size >= request.maxAccepted) break

            val candidate = item.candidate
            val bounds = item.bounds
            val collidingIndices = candidateCollisionIndices(bounds, collisionGrid)
            val collides = if (candidate.selected) {
                // Selected objects must remain visible. They still occupy the
                // grid so lower-priority labels cannot render over them.
                false
            } else {
                collidingIndices.any { index -> bounds.intersects(accepted[index].toBounds()) }
            }
            if (collides) continue

            val placement = PlacedLabel(
                candidate = candidate,
                left = bounds.left,
                top = bounds.top,
                right = bounds.right,
                bottom = bounds.bottom,
            )
            val acceptedIndex = accepted.size
            accepted += placement
            occupy(bounds, acceptedIndex, collisionGrid)
        }

        return accepted
    }

    private fun isCandidateValid(candidate: LabelCandidate, zoom: Float): Boolean {
        if (candidate.stableId.isEmpty()) return false
        if (!candidate.anchorX.isFinite() || !candidate.anchorY.isFinite()) return false
        if (!candidate.width.isFinite() || !candidate.height.isFinite()) return false
        if (candidate.width <= 0f || candidate.height <= 0f) return false
        if (!candidate.minZoom.isFinite() || candidate.minZoom < 0f) return false
        if (candidate.maxZoom.isNaN() || candidate.maxZoom < candidate.minZoom) return false
        return zoom >= candidate.minZoom && zoom <= candidate.maxZoom
    }

    private fun candidateBounds(candidate: LabelCandidate): Bounds? {
        val halfWidth = candidate.width * 0.5f
        val halfHeight = candidate.height * 0.5f
        val bounds = Bounds(
            left = candidate.anchorX - halfWidth,
            top = candidate.anchorY - halfHeight,
            right = candidate.anchorX + halfWidth,
            bottom = candidate.anchorY + halfHeight,
        )
        return bounds.takeIf { it.isFiniteAndOrdered() }
    }

    private fun candidateCollisionIndices(
        bounds: Bounds,
        collisionGrid: Map<Long, List<Int>>,
    ): Set<Int> {
        val indices = LinkedHashSet<Int>()
        forEachCell(bounds) { cellX, cellY ->
            collisionGrid[cellKey(cellX, cellY)]?.let(indices::addAll)
        }
        return indices
    }

    private fun occupy(
        bounds: Bounds,
        acceptedIndex: Int,
        collisionGrid: MutableMap<Long, MutableList<Int>>,
    ) {
        forEachCell(bounds) { cellX, cellY ->
            collisionGrid.getOrPut(cellKey(cellX, cellY)) { ArrayList(2) }
                .add(acceptedIndex)
        }
    }

    private inline fun forEachCell(bounds: Bounds, block: (Int, Int) -> Unit) {
        val firstX = floor(bounds.left / collisionCellPx).toInt()
        val lastX = floor(bounds.right / collisionCellPx).toInt()
        val firstY = floor(bounds.top / collisionCellPx).toInt()
        val lastY = floor(bounds.bottom / collisionCellPx).toInt()
        for (cellY in firstY..lastY) {
            for (cellX in firstX..lastX) block(cellX, cellY)
        }
    }

    private fun cellKey(cellX: Int, cellY: Int): Long =
        (cellX.toLong() shl 32) xor (cellY.toLong() and 0xffff_ffffL)

    private data class CandidateWithBounds(
        val candidate: LabelCandidate,
        val bounds: Bounds,
    )

    private data class Bounds(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) {
        fun isFiniteAndOrdered(): Boolean =
            left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite() &&
                right >= left && bottom >= top

        fun intersects(other: Bounds): Boolean =
            left < other.right && right > other.left && top < other.bottom && bottom > other.top

        fun intersectsViewport(width: Int, height: Int): Boolean =
            right > 0f && bottom > 0f && left < width.toFloat() && top < height.toFloat()
    }

    private fun PlacedLabel.toBounds(): Bounds = Bounds(left, top, right, bottom)
}
