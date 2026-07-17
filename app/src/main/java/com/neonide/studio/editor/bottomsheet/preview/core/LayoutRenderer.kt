package com.neonide.studio.editor.bottomsheet.preview.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.HandlerThread
import android.view.View
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

data class RenderConfig(val width: Int, val height: Int)

class LayoutRenderer {

    private val renderThread = HandlerThread("LayoutPreview").apply { start() }
    private val renderHandler = Handler(renderThread.looper)

    /**
     * Renders a pre-built View hierarchy to a Bitmap.
     * The view should already have layout params and children configured.
     */
    suspend fun render(root: View, config: RenderConfig): Result<Bitmap> =
        suspendCancellableCoroutine { cont ->
            val runnable = Runnable {
                if (!cont.isActive) return@Runnable
                try {
                    val widthSpec = View.MeasureSpec.makeMeasureSpec(
                        config.width,
                        View.MeasureSpec.EXACTLY
                    )

                    // First pass — UNSPECIFIED to measure natural content height.
                    val unconstrainedHeight = View.MeasureSpec.makeMeasureSpec(
                        0,
                        View.MeasureSpec.UNSPECIFIED
                    )
                    root.measure(widthSpec, unconstrainedHeight)
                    val naturalHeight = root.measuredHeight

                    val renderHeight: Int
                    val renderBitmapHeight: Int

                    if (naturalHeight > config.height) {
                        // Tall content — render at natural height, scrollable.
                        renderHeight = naturalHeight
                        renderBitmapHeight = naturalHeight
                        root.layout(0, 0, config.width, renderHeight)
                    } else {
                        // Content fits — use EXACTLY so gravity works (FAB, centering, etc.)
                        renderHeight = config.height
                        renderBitmapHeight = config.height
                        val exactHeight = View.MeasureSpec.makeMeasureSpec(
                            config.height,
                            View.MeasureSpec.EXACTLY
                        )
                        root.measure(widthSpec, exactHeight)
                        root.layout(0, 0, config.width, renderHeight)
                    }

                    val bitmap = Bitmap.createBitmap(
                        config.width,
                        renderBitmapHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(0xFFE8E8E8.toInt())
                    root.draw(canvas)

                    if (cont.isActive) {
                        cont.resume(Result.success(bitmap))
                    } else {
                        bitmap.recycle()
                    }
                } catch (e: Exception) {
                    if (cont.isActive) {
                        cont.resume(Result.failure(e))
                    }
                }
            }
            cont.invokeOnCancellation {
                renderHandler.removeCallbacks(runnable)
            }
            renderHandler.post(runnable)
        }

    fun dispose() {
        renderThread.quitSafely()
    }
}
