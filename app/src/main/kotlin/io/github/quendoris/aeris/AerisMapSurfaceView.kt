// SPDX-FileCopyrightText: 2026 quendoris
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.quendoris.aeris

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min

enum class MapPresentationMode {
    Globe,
    Flat,
}

private data class MapViewportState(
    val panX: Float = 0f,
    val panY: Float = 0f,
    val zoom: Float = 1f,
    val presentationMode: MapPresentationMode = MapPresentationMode.Globe,
    val political: Boolean = true,
)

/**
 * Android-specific map surface.
 *
 * Compose owns application chrome, while this view owns the high-frequency map
 * presentation path. The placeholder renderer deliberately performs no file I/O
 * and exists only to establish the thread/lifecycle/gesture boundary before the
 * native AERIS reader and renderer are connected.
 */
class AerisMapSurfaceView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    private val state = AtomicReference(MapViewportState())
    private val renderThread = HandlerThread(
        "aeris-map-render",
        Process.THREAD_PRIORITY_DISPLAY,
    ).apply { start() }
    private val renderHandler = Handler(renderThread.looper)

    private val renderRunnable = Runnable { renderFrame() }

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true

            override fun onScroll(
                first: MotionEvent?,
                current: MotionEvent,
                distanceX: Float,
                distanceY: Float,
            ): Boolean {
                state.updateAndGet { previous ->
                    previous.copy(
                        panX = previous.panX - distanceX,
                        panY = previous.panY - distanceY,
                    )
                }
                requestRender()
                return true
            }
        },
    )

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                state.updateAndGet { previous ->
                    previous.copy(
                        zoom = (previous.zoom * detector.scaleFactor).coerceIn(0.55f, 8f),
                    )
                }
                requestRender()
                return true
            }
        },
    )

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(23, 25, 29)
        style = Paint.Style.FILL
    }
    private val oceanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(37, 44, 53)
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 132, 151, 170)
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(137, 151, 166)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(185, 190, 198)
        textSize = 34f
    }
    private val smallTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(143, 150, 160)
        textSize = 24f
    }

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    fun setPresentationMode(mode: MapPresentationMode) {
        state.updateAndGet { previous ->
            if (previous.presentationMode == mode) previous
            else previous.copy(
                presentationMode = mode,
                panX = 0f,
                panY = 0f,
            )
        }
        requestRender()
    }

    fun setPolitical(political: Boolean) {
        state.updateAndGet { previous -> previous.copy(political = political) }
        requestRender()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        requestRender()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        requestRender()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        renderHandler.removeCallbacksAndMessages(null)
    }

    override fun onDetachedFromWindow() {
        holder.removeCallback(this)
        renderHandler.removeCallbacksAndMessages(null)
        renderThread.quitSafely()
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    private fun requestRender() {
        if (!holder.surface.isValid) return
        // Camera events may arrive much faster than frames can be drawn. Keep
        // only the newest request so gestures cannot build a render backlog.
        renderHandler.removeCallbacks(renderRunnable)
        renderHandler.post(renderRunnable)
    }

    private fun renderFrame() {
        if (!holder.surface.isValid) return
        val canvas = holder.lockCanvas() ?: return
        try {
            drawPlaceholder(canvas, state.get())
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    private fun drawPlaceholder(canvas: Canvas, viewport: MapViewportState) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val centerX = width * 0.5f + viewport.panX
        val centerY = height * 0.47f + viewport.panY
        val baseRadius = min(width, height) * 0.34f
        val zoomedRadius = max(32f, baseRadius * viewport.zoom)

        if (viewport.presentationMode == MapPresentationMode.Globe) {
            canvas.drawCircle(centerX, centerY, zoomedRadius, oceanPaint)
            drawGlobeGrid(canvas, centerX, centerY, zoomedRadius)
            canvas.drawCircle(centerX, centerY, zoomedRadius, edgePaint)
        } else {
            val halfWidth = zoomedRadius * 1.55f
            val halfHeight = zoomedRadius * 0.72f
            val bounds = RectF(
                centerX - halfWidth,
                centerY - halfHeight,
                centerX + halfWidth,
                centerY + halfHeight,
            )
            canvas.drawOval(bounds, oceanPaint)
            drawFlatGrid(canvas, bounds)
            canvas.drawOval(bounds, edgePaint)
        }

        textPaint.color = if (viewport.political) {
            Color.rgb(191, 197, 205)
        } else {
            Color.rgb(201, 202, 194)
        }
        canvas.drawText("AERIS", 36f, height - 82f, textPaint)
        canvas.drawText(
            if (viewport.political) "Political · no .aeris open" else "Physical · no .aeris open",
            36f,
            height - 42f,
            smallTextPaint,
        )
    }

    private fun drawGlobeGrid(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        val clip = Path().apply { addCircle(centerX, centerY, radius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)

        for (step in -2..2) {
            val offset = radius * step / 3f
            canvas.drawLine(
                centerX - radius,
                centerY + offset,
                centerX + radius,
                centerY + offset,
                gridPaint,
            )
        }
        for (step in -2..2) {
            val x = centerX + radius * step / 3f
            val horizontalRadius = radius * (1f - 0.08f * kotlin.math.abs(step))
            canvas.drawOval(
                RectF(
                    x - horizontalRadius * 0.18f,
                    centerY - radius,
                    x + horizontalRadius * 0.18f,
                    centerY + radius,
                ),
                gridPaint,
            )
        }
        canvas.restore()
    }

    private fun drawFlatGrid(canvas: Canvas, bounds: RectF) {
        val clip = Path().apply { addOval(bounds, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)

        for (step in 1..5) {
            val fraction = step / 6f
            val y = bounds.top + bounds.height() * fraction
            canvas.drawLine(bounds.left, y, bounds.right, y, gridPaint)
        }
        for (step in 1..7) {
            val fraction = step / 8f
            val x = bounds.left + bounds.width() * fraction
            canvas.drawLine(x, bounds.top, x, bounds.bottom, gridPaint)
        }
        canvas.restore()
    }
}
