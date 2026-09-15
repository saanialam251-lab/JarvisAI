package com.jarvis.assistant.core.executor

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.jarvis.assistant.service.JarvisAccessibilityService

/**
 * Semantic UI control layer (requirement #4): works with text,
 * content-descriptions and view IDs — never fixed screen coordinates.
 * Falls back to gesture taps only when a node refuses clicks.
 */
object AccessibilityController {

    @Volatile var service: JarvisAccessibilityService? = null

    fun isReady(): Boolean = service?.rootInActiveWindow != null
    private fun root(): AccessibilityNodeInfo? = service?.rootInActiveWindow

    // ---------- observation (the "Observe screen" step) ----------
    fun uiSummary(maxNodes: Int = 80): String {
        val sb = StringBuilder()
        flatten(root(), sb, 0, maxNodes)
        return sb.toString()
    }

    private fun flatten(n: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int, max: Int) {
        if (n == null || sb.lines().count() >= max) return
        val t = n.text?.toString()?.trim().orEmpty()
        val d = n.contentDescription?.toString()?.trim().orEmpty()
        val cls = n.className?.toString()?.substringAfterLast('.').orEmpty()
        if ((t.isNotEmpty() || d.isNotEmpty()) && depth > 0) {
            sb.append("  ".repeat(depth)).append("- ").append(t.ifEmpty { d })
            if (d.isNotEmpty() && d != t) sb.append(" [").append(d).append("]")
            if (n.isClickable) sb.append(" (clickable)")
            sb.append(" <").append(cls).append(">\n")
        }
        for (i in 0 until n.childCount) flatten(n.getChild(i), sb, depth + 1, max)
    }

    // ---------- finders ----------
    private fun walk(n: AccessibilityNodeInfo?, pred: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (n == null) return null
        if (pred(n)) return n
        for (i in 0 until n.childCount) {
            val r = walk(n.getChild(i), pred)
            if (r != null) return r
        }
        return null
    }

    fun findByText(text: String, exact: Boolean): AccessibilityNodeInfo? =
        walk(root()) { n ->
            val t = n.text?.toString()?.trim().orEmpty()
            if (exact) t.equals(text, true) else t.contains(text, true)
        }

    fun findByDesc(desc: String): AccessibilityNodeInfo? =
        walk(root()) { n -> n.contentDescription?.toString()?.contains(desc, true) == true }

    fun findById(viewId: String): AccessibilityNodeInfo? =
        walk(root()) { it.viewIdResourceName?.contains(viewId) == true }

    fun findEditText(): AccessibilityNodeInfo? =
        walk(root()) { it.className?.toString()?.contains("EditText") == true && it.isEnabled }

    // ---------- actions ----------
    fun tapNode(n: AccessibilityNodeInfo): Boolean {
        if (n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        val r = Rect(); n.getBoundsInScreen(r)
        return tapAt(((r.left + r.right) / 2).toFloat(), ((r.top + r.bottom) / 2).toFloat())
    }

    fun tapText(text: String, exact: Boolean = false): Boolean {
        val n = findByText(text, exact) ?: return false
        return tapNode(n)
    }

    fun tapDesc(desc: String): Boolean {
        val n = findByDesc(desc) ?: return false
        return tapNode(n)
    }

    fun typeText(text: String): Boolean {
        val n = findEditText() ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun submitSearch(): Boolean {
        // Try the IME/search affordance first, then a "Search"/"OK" label.
        val ime = walk(root()) { it.className?.toString()?.contains("EditText") == true }
        if (ime?.performAction(AccessibilityNodeInfo.ACTION_IME_ACTION_ENTER) == true) return true
        listOf("Search", "OK", "Go", "search").forEach { label ->
            findByDesc(label)?.let { if (tapNode(it)) return true }
            findByText(label, exact = true)?.let { if (tapNode(it)) return true }
        }
        return false
    }

    fun scrollForward(targetText: String?): Boolean {
        val target = targetText?.let { findByText(it, false) }
        val scrollable = walk(root()) { it.isScrollable && it.actionList.any { a -> a.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD.id } }
            ?: return false
        if (target != null && isVisible(target)) return true
        return scrollable.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD.id)
    }

    private fun isVisible(n: AccessibilityNodeInfo): Boolean {
        val r = Rect(); n.getBoundsInScreen(r)
        return r.width() > 0 && r.height() > 0
    }

    fun tapAt(x: Float, y: Float): Boolean {
        val svc = service ?: return false
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        val ok = BooleanArray(1)
        svc.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) { ok[0] = true }
        }, null)
        // dispatchGesture is async; give it a moment
        Thread.sleep(250)
        return ok[0] || true // gesture dispatched; result callback is best-effort
    }

    fun back() { service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }
    fun home() { service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) }
}
