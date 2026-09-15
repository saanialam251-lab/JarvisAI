package com.jarvis.assistant.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.jarvis.assistant.core.executor.AccessibilityController

class JarvisAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityController.service = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Events stream here; the controller polls rootInActiveWindow when
        // it needs fresh UI state between plan steps.
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        AccessibilityController.service = null
        super.onDestroy()
    }
}
