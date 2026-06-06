package com.neonide.studio.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.drawerlayout.widget.DrawerLayout
import kotlin.math.sqrt

class NoSwipeDrawerLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : DrawerLayout(context, attrs, defStyleAttr) {

    private var downX = 0f
    private var downY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDrawerOpen(android.view.Gravity.START)) {
                    val dx = ev.x - downX
                    val dy = ev.y - downY
                    val distance = sqrt(dx * dx + dy * dy)
                    if (distance >= touchSlop) {
                        return false
                    }
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }
}
