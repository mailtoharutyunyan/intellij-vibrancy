package com.vibrancy

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.mac.foundation.Foundation
import com.intellij.ui.mac.foundation.ID
import com.intellij.ui.mac.foundation.MacUtil
import com.sun.jna.Callback
import com.sun.jna.NativeLibrary
import com.vibrancy.settings.VibrancySettings
import javax.swing.JFrame
import javax.swing.SwingUtilities

object MacVibrancyEffect {

    private val LOG = Logger.getInstance(MacVibrancyEffect::class.java)
    private var awtWindowPatched = false
    private var effectViewId: ID = ID.NIL
    private var setupDone = false

    @Suppress("unused")
    private val returnNilIMP = object : Callback {
        @Suppress("unused")
        fun callback(self: Long, cmd: Long, arg1: Long, arg2: Long): Long = 0L
    }

    fun applyVibrancy(frame: JFrame) {
        if (!SystemInfo.isMac || setupDone) return
        val settings = VibrancySettings.instance.state
        if (!settings.enabled) return

        try {
            Foundation.executeOnMainThread(false, true) {
                try {
                    setupNativeVibrancy(frame, settings)
                } catch (e: Exception) {
                    LOG.error("Native vibrancy failed", e)
                }
            }
            // Restore Swing focus after native setup completes
            SwingUtilities.invokeLater {
                frame.toFront()
                frame.requestFocus()
            }
        } catch (e: Exception) {
            LOG.error("Failed to apply vibrancy", e)
        }
    }

    fun updateSettings(frame: JFrame) {
        val settings = VibrancySettings.instance.state
        val theme = VibrancyTheme.fromName(settings.theme)
        val material = if (theme == VibrancyTheme.CUSTOM) settings.material else theme.material
        val alpha = if (theme == VibrancyTheme.CUSTOM) computeCustomAlpha(settings.opacity) else theme.windowAlpha
        val appearance = if (theme == VibrancyTheme.CUSTOM) settings.appearance else theme.appearance

        Foundation.executeOnMainThread(false, true) {
            try {
                val nsWindow = MacUtil.getWindowFromJavaWindow(frame)
                if (nsWindow == ID.NIL) return@executeOnMainThread

                if (!settings.enabled) {
                    Foundation.invoke(nsWindow, "setOpaque:", true)
                    Foundation.invoke(nsWindow, "setAlphaValue:", 1.0)
                    LOG.info("Vibrancy disabled")
                    return@executeOnMainThread
                }

                Foundation.invoke(nsWindow, "setOpaque:", false)
                if (effectViewId != ID.NIL) {
                    Foundation.invoke(effectViewId, "setMaterial:", material.toLong())
                }
                Foundation.invoke(nsWindow, "setAlphaValue:", alpha)
                setWindowAppearance(nsWindow, appearance)
                LOG.info("Updated: theme=${theme.displayName}, material=$material, alpha=$alpha")
            } catch (e: Exception) {
                LOG.warn("Update failed: ${e.message}")
            }
        }
    }

    private fun patchAWTWindowForVibrancy(): Boolean {
        if (awtWindowPatched) return true
        try {
            val cls = Foundation.getObjcClass("AWTWindow") ?: return false
            if (cls == ID.NIL) return false
            val sel = Foundation.createSelector("windowWillReturnFieldEditor:toObject:")
            if (Foundation.invoke(cls, "instancesRespondToSelector:", sel).toLong() != 0L) {
                awtWindowPatched = true
                return true
            }
            val added = NativeLibrary.getInstance("objc").getFunction("class_addMethod")
                .invokeInt(arrayOf(cls, sel, returnNilIMP, "@@:@@"))
            awtWindowPatched = added != 0
            return awtWindowPatched
        } catch (_: Exception) {
            return false
        }
    }

    private fun setupNativeVibrancy(frame: JFrame, settings: VibrancySettings.State) {
        val nsWindow = MacUtil.getWindowFromJavaWindow(frame)
        if (nsWindow == ID.NIL) return
        patchAWTWindowForVibrancy()

        val contentView = Foundation.invoke(nsWindow, "contentView")
        if (contentView == ID.NIL) return

        val themeFrame = Foundation.invoke(contentView, "superview")
        if (themeFrame == ID.NIL) return

        val theme = VibrancyTheme.fromName(settings.theme)
        val material = if (theme == VibrancyTheme.CUSTOM) settings.material else theme.material
        val alpha = if (theme == VibrancyTheme.CUSTOM) computeCustomAlpha(settings.opacity) else theme.windowAlpha
        val appearance = if (theme == VibrancyTheme.CUSTOM) settings.appearance else theme.appearance

        val veClass = Foundation.getObjcClass("NSVisualEffectView") ?: return
        if (veClass == ID.NIL) return

        val w = frame.width.toDouble()
        val h = frame.height.toDouble()
        val effectView = Foundation.invoke(Foundation.invoke(veClass, "alloc"),
            "initWithFrame:", 0.0, 0.0, w, h)
        if (effectView == ID.NIL) return

        Foundation.invoke(effectView, "setMaterial:", material.toLong())
        Foundation.invoke(effectView, "setBlendingMode:", 0.toLong())
        Foundation.invoke(effectView, "setState:", 1.toLong())
        Foundation.invoke(effectView, "setAutoresizingMask:", 18.toLong())

        // Insert effectView as sibling BEHIND the contentView in themeFrame
        Foundation.invoke(themeFrame, "addSubview:positioned:relativeTo:",
            effectView, (-1).toLong(), contentView)
        effectViewId = effectView

        addLiquidGlass(themeFrame, contentView)

        Foundation.invoke(nsWindow, "setOpaque:", false)
        Foundation.invoke(nsWindow, "setBackgroundColor:",
            Foundation.invoke(Foundation.getObjcClass("NSColor"), "clearColor"))
        Foundation.invoke(nsWindow, "setHasShadow:", true)
        Foundation.invoke(nsWindow, "setAlphaValue:", alpha)
        setWindowAppearance(nsWindow, appearance)

        // Re-establish window as key window after native view hierarchy changes
        Foundation.invoke(nsWindow, "makeKeyAndOrderFront:", ID.NIL)

        setupDone = true
        LOG.info("Vibrancy active: theme=${theme.displayName}, material=$material, alpha=$alpha")
    }

    private fun setWindowAppearance(nsWindow: ID, appearance: Int) {
        try {
            val name = when (appearance) {
                1 -> "NSAppearanceNameVibrantLight"
                2 -> "NSAppearanceNameVibrantDark"
                else -> return
            }
            val a = Foundation.invoke(Foundation.getObjcClass("NSAppearance"),
                "appearanceNamed:", Foundation.nsString(name))
            if (a != ID.NIL) {
                Foundation.invoke(nsWindow, "setAppearance:", a)
            }
        } catch (e: Exception) {
            LOG.debug("Failed to set appearance: ${e.message}")
        }
    }

    private fun addLiquidGlass(themeFrame: ID, contentView: ID) {
        var gc = Foundation.getObjcClass("NSGlassContainerView")
        if (gc == null || gc == ID.NIL) gc = Foundation.getObjcClass("NSGlassEffectView")
        if (gc == null || gc == ID.NIL) return
        try {
            val w = 3000.0
            val h = 2000.0
            val g = Foundation.invoke(Foundation.invoke(gc, "alloc"),
                "initWithFrame:", 0.0, 0.0, w, h)
            if (g == ID.NIL) return
            Foundation.invoke(g, "setAutoresizingMask:", 18.toLong())
            Foundation.invoke(themeFrame, "addSubview:positioned:relativeTo:",
                g, (-1).toLong(), contentView)
            LOG.info("Liquid Glass active")
        } catch (e: Exception) {
            LOG.debug("Liquid Glass not available: ${e.message}")
        }
    }

    private fun computeCustomAlpha(opacity: Int): Double =
        (1.0 - (opacity * 0.002)).coerceIn(0.75, 1.0)
}
