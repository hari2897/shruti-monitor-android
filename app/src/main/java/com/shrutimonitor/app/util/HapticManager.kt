package com.shrutimonitor.app.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Centralized haptic feedback manager.
 *
 * Provides semantic haptic effects for different interaction types:
 * - [heavyClick]: Strong feedback for major toggles (mic, record, tanpura)
 * - [tick]: Light feedback for selections (keys, tabs, swara toggles)
 * - [doubleClick]: Confirmation feedback (raga activation)
 * - [thud]: Deep feedback (base note selection)
 *
 * All effects respect the [isEnabled] toggle, which is bound to user settings.
 */
object HapticManager {

    /**
     * Master toggle. When false, all haptic methods become no-ops.
     * Updated from SettingsRepository's hapticEnabled flow.
     */
    @Volatile
    var isEnabled: Boolean = true

    /**
     * Strong haptic for major toggle interactions.
     * Use for: mic toggle, record start/stop, tanpura on/off.
     */
    fun heavyClick(view: View) {
        if (!isEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    /**
     * Light haptic for selection interactions.
     * Use for: keyboard keys, tab switches, swara toggles, theme changes.
     */
    fun tick(view: View) {
        if (!isEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    /**
     * Double-tap haptic for confirmation interactions.
     * Use for: raga activation.
     */
    fun doubleClick(view: View) {
        if (!isEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    /**
     * Deep thud haptic for significant selections.
     * Use for: base note (Sa) selection.
     */
    fun thud(view: View) {
        if (!isEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }
}
