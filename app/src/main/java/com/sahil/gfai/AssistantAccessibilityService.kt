package com.sahil.gfai

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AssistantAccessibilityService : AccessibilityService() {
    companion object { @Volatile var instance: AssistantAccessibilityService? = null }

    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: android.content.Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun readScreen(): String {
        val root = rootInActiveWindow ?: return "Screen readable nahi hai."
        val sb = StringBuilder("App: ${root.packageName}\n")
        fun walk(n: AccessibilityNodeInfo?, d: Int) {
            if (n == null || sb.length > 6000) return
            val t = n.text?.toString() ?: n.contentDescription?.toString()
            if (!t.isNullOrBlank()) sb.append("  ".repeat(minOf(d, 6))).append(t)
                .append(if (n.isClickable) " [clickable]" else "").append(if (n.isEditable) " [input]" else "").append('\n')
            for (i in 0 until n.childCount) walk(n.getChild(i), d + 1)
        }
        walk(root, 0)
        return sb.toString()
    }

    fun clickText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = root.findAccessibilityNodeInfosByText(text).firstOrNull() ?: return false
        var n: AccessibilityNodeInfo? = node
        while (n != null && !n.isClickable) n = n.parent
        return (n ?: node).performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    fun tap(x: Float, y: Float): Boolean = gesture(x, y, x, y, 60)

    fun scroll(dir: String): Boolean {
        val w = resources.displayMetrics.widthPixels.toFloat(); val h = resources.displayMetrics.heightPixels.toFloat()
        return when (dir.lowercase()) {
            "up" -> gesture(w / 2, h * 0.3f, w / 2, h * 0.75f, 300)      // content moves down
            "left" -> gesture(w * 0.2f, h / 2, w * 0.8f, h / 2, 300)
            "right" -> gesture(w * 0.8f, h / 2, w * 0.2f, h / 2, 300)
            else -> gesture(w / 2, h * 0.75f, w / 2, h * 0.3f, 300)       // "down": scroll to see lower content
        }
    }

    fun typeText(text: String): Boolean {
        val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun gesture(x1: Float, y1: Float, x2: Float, y2: Float, dur: Long): Boolean {
        val p = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        return dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, dur)).build(), null, null)
    }
}
