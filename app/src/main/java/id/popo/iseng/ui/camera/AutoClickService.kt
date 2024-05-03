package id.popo.iseng.ui.camera

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityEvent.TYPE_ANNOUNCEMENT

class AutoClickService : AccessibilityService() {

    //apparently this method is called every time a event occurs
    override fun onAccessibilityEvent(accessibilityEvent: AccessibilityEvent) {
        if(accessibilityEvent.eventType == TYPE_ANNOUNCEMENT) {
            autoClick(0, 100, 950, 581)
        }

    }


    public override fun onServiceConnected() {
        super.onServiceConnected()
        autoClick(2000, 100, 950, 581)
    }

    override fun onInterrupt() {}

    private fun autoClick(startTimeMs: Int, durationMs: Int, x: Int, y: Int) {
        val isCalled =
            dispatchGesture(gestureDescription(startTimeMs, durationMs, x, y), null, null)
        Log.e("DEBUG_MAIN", isCalled.toString())
    }

    private fun gestureDescription(startTimeMs: Int, durationMs: Int, x: Int, y: Int): GestureDescription {
        val path = Path()
        val displayMetrics = resources.displayMetrics

        val height = displayMetrics.heightPixels
        val top = (height * .25).toInt()
        val bottom = (height * .75).toInt()
        val midX = displayMetrics.widthPixels / 2
        path.moveTo(midX.toFloat(), bottom.toFloat())
        path.lineTo(midX.toFloat(), top.toFloat())
        return createGestureDescription(
            StrokeDescription(
                path,
                startTimeMs.toLong(),
                durationMs.toLong()
            )
        )
    }

    private fun createGestureDescription(vararg strokes: StrokeDescription?): GestureDescription {
        val builder = GestureDescription.Builder()
        for (stroke in strokes) {
            builder.addStroke(stroke!!)
        }
        return builder.build()
    }
}