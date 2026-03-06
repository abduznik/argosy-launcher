package com.nendo.argosy.util

import android.app.ActivityOptions
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class SecondaryDisplayType { NONE, BUILT_IN, EXTERNAL }

@Singleton
class DisplayAffinityHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    private val physicalDisplays: Array<Display>
        get() = displayManager.displays.filter { !it.isVirtualDisplay() }.toTypedArray()

    val hasSecondaryDisplay: Boolean
        get() = physicalDisplays.size > 1

    val secondaryDisplayType: SecondaryDisplayType
        get() {
            val secondary = physicalDisplays.getOrNull(1) ?: return SecondaryDisplayType.NONE
            val type = secondary.displayType()
            return when {
                type == DISPLAY_TYPE_EXTERNAL -> SecondaryDisplayType.EXTERNAL
                type == DISPLAY_TYPE_BUILT_IN -> SecondaryDisplayType.BUILT_IN
                secondary.flags and Display.FLAG_PRESENTATION != 0 -> SecondaryDisplayType.EXTERNAL
                else -> SecondaryDisplayType.BUILT_IN
            }
        }

    private val secondaryDisplayId: Int?
        get() = physicalDisplays.getOrNull(1)?.displayId

    fun registerDisplayListener(
        listener: DisplayManager.DisplayListener,
        handler: android.os.Handler? = null
    ) {
        displayManager.registerDisplayListener(listener, handler)
    }

    fun unregisterDisplayListener(listener: DisplayManager.DisplayListener) {
        displayManager.unregisterDisplayListener(listener)
    }

    fun getCompanionLaunchOptions(): Bundle? {
        val displayId = secondaryDisplayId ?: return null
        return ActivityOptions.makeBasic()
            .setLaunchDisplayId(displayId)
            .toBundle()
    }

    fun getEmulatorDisplayId(rolesSwapped: Boolean): Int {
        return if (rolesSwapped) secondaryDisplayId ?: Display.DEFAULT_DISPLAY
        else Display.DEFAULT_DISPLAY
    }

    fun getActivityOptions(forEmulator: Boolean, rolesSwapped: Boolean = false): Bundle? {
        if (!hasSecondaryDisplay) return null

        val targetDisplayId = if (forEmulator) {
            if (rolesSwapped) secondaryDisplayId ?: return null
            else Display.DEFAULT_DISPLAY
        } else {
            secondaryDisplayId ?: return null
        }

        return ActivityOptions.makeBasic()
            .setLaunchDisplayId(targetDisplayId)
            .toBundle()
    }

    fun isPhysicalDisplay(displayId: Int): Boolean {
        val display = displayManager.getDisplay(displayId) ?: return false
        return !display.isVirtualDisplay()
    }

    companion object {
        private const val DISPLAY_TYPE_BUILT_IN = 1
        private const val DISPLAY_TYPE_EXTERNAL = 2
        private const val DISPLAY_TYPE_VIRTUAL = 5

        private fun Display.displayType(): Int? = try {
            Display::class.java.getMethod("getType").invoke(this) as? Int
        } catch (_: Exception) { null }

        private fun Display.isVirtualDisplay(): Boolean {
            val type = displayType()
            if (type == DISPLAY_TYPE_VIRTUAL) return true
            if (type != null) return false
            return flags and Display.FLAG_PRIVATE != 0
        }
    }
}
