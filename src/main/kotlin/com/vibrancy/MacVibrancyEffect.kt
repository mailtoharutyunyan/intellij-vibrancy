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

/**
 * Core native vibrancy engine that injects macOS AppKit visual effects into IntelliJ IDE windows.
 *
 * This object manages the lifecycle of an [NSVisualEffectView](https://developer.apple.com/documentation/appkit/nsvisualeffectview)
 * that is inserted behind the Java content view inside the window's `NSThemeFrame`. It also handles
 * Liquid Glass effects on macOS Tahoe (26+) via `NSGlassContainerView` / `NSGlassEffectView`.
 *
 * ## How it works
 *
 * 1. The `NSVisualEffectView` is allocated and inserted as a sibling **behind** the `AWTView` content,
 *    providing the native blur material.
 * 2. The `NSWindow` is set to non-opaque with a `clearColor` background so the blur shows through.
 * 3. `NSWindow.alphaValue` controls overall transparency intensity per [VibrancyTheme].
 * 4. A missing `windowWillReturnFieldEditor:toObject:` selector is dynamically added to `AWTWindow`
 *    via the Objective-C runtime to prevent crashes when vibrancy triggers the delegate cascade.
 *
 * ## Thread safety
 *
 * All native AppKit operations are dispatched to the main thread via [Foundation.executeOnMainThread].
 * Swing focus is restored on the EDT after native setup completes.
 *
 * @see VibrancyTheme
 * @see VibrancySettings
 * @see VibrancyProjectActivity
 * @since 1.0.0
 */
object MacVibrancyEffect {

    private val LOG = Logger.getInstance(MacVibrancyEffect::class.java)

    /** Whether [patchAWTWindowForVibrancy] has already added the missing selector. */
    private var awtWindowPatched = false

    /** The Objective-C [ID] of the injected `NSVisualEffectView`, used for live material updates. */
    private var effectViewId: ID = ID.NIL

    /** Guard flag ensuring [setupNativeVibrancy] runs only once per IDE session. */
    private var setupDone = false

    /**
     * JNA callback implementing the `windowWillReturnFieldEditor:toObject:` selector.
     *
     * Returns `nil` (0) unconditionally. This prevents a crash that occurs when AppKit's delegate
     * cascade reaches `AWTWindow`, which does not implement this optional `NSWindowDelegate` method.
     */
    @Suppress("unused")
    private val returnNilIMP = object : Callback {
        @Suppress("unused")
        fun callback(self: Long, cmd: Long, arg1: Long, arg2: Long): Long = 0L
    }

    /**
     * Applies the native vibrancy effect to the given IDE [frame].
     *
     * This method is the primary entry point, called from [VibrancyProjectActivity] on project open.
     * It is a no-op if:
     * - The current OS is not macOS
     * - Vibrancy has already been set up ([setupDone])
     * - The effect is disabled in [VibrancySettings]
     *
     * @param frame the main IDE [JFrame] to apply vibrancy to
     * @see setupNativeVibrancy
     */
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

    /**
     * Updates the vibrancy effect on an already-configured [frame] with current [VibrancySettings].
     *
     * Called from [VibrancyConfigurable][com.vibrancy.settings.VibrancyConfigurable] when the user
     * clicks **Apply** or **OK** in the settings panel. Modifies native view properties in-place
     * (material, alpha, appearance) without restarting the IDE.
     *
     * If vibrancy is disabled in settings, the window is restored to fully opaque.
     *
     * @param frame the IDE [JFrame] whose vibrancy properties should be updated
     */
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

    /**
     * Dynamically adds the `windowWillReturnFieldEditor:toObject:` selector to `AWTWindow`.
     *
     * When an `NSVisualEffectView` is added to the window hierarchy, AppKit may invoke the
     * `NSWindowDelegate` method `windowWillReturnFieldEditor:toObject:` on the window's delegate.
     * Java's `AWTWindow` does not implement this selector, causing an `unrecognized selector` crash.
     *
     * This method uses `class_addMethod` from the Objective-C runtime to register [returnNilIMP]
     * as the implementation, which safely returns `nil`.
     *
     * @return `true` if the selector is already present or was successfully added, `false` on failure
     */
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

    /**
     * Performs the one-time native setup: creates and injects the `NSVisualEffectView`,
     * configures the window for transparency, and optionally adds Liquid Glass.
     *
     * ### View hierarchy after injection
     * ```
     * NSThemeFrame
     *   ├── NSGlassContainerView (macOS 26+ only)
     *   ├── NSVisualEffectView   ← injected blur layer
     *   └── AWTView              ← Java content (Swing)
     * ```
     *
     * The `NSVisualEffectView` is inserted via `addSubview:positioned:relativeTo:` with
     * `NSWindowBelow` (-1) relative to the content view, ensuring it sits behind all Java content.
     *
     * @param frame    the IDE [JFrame] whose native window is being configured
     * @param settings the current [VibrancySettings.State] snapshot
     */
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

    /**
     * Sets the `NSAppearance` on the given [nsWindow] to control light/dark vibrancy rendering.
     *
     * @param nsWindow   the Objective-C `NSWindow` identifier
     * @param appearance the appearance mode: `0` = auto (no override), `1` = Vibrant Light, `2` = Vibrant Dark
     */
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

    /**
     * Adds a Liquid Glass effect layer on macOS Tahoe (26+).
     *
     * Attempts to instantiate `NSGlassContainerView` first; if unavailable, falls back to
     * `NSGlassEffectView`. The glass view is inserted behind the content view, similar to the
     * `NSVisualEffectView` placement. This is a no-op on macOS versions earlier than Tahoe.
     *
     * @param themeFrame  the `NSThemeFrame` that serves as the parent view
     * @param contentView the `AWTView` content view used as the positioning anchor
     */
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

    /**
     * Converts the user-facing opacity slider value (0–100) to an `NSWindow.alphaValue` (0.75–1.0).
     *
     * Higher slider values mean more transparency (lower alpha). The result is clamped to
     * `[0.75, 1.0]` to ensure the window never becomes invisible.
     *
     * @param opacity the slider value from [VibrancySettings.State.opacity]
     * @return the computed alpha value for `NSWindow.setAlphaValue:`
     */
    private fun computeCustomAlpha(opacity: Int): Double =
        (1.0 - (opacity * 0.002)).coerceIn(0.75, 1.0)
}
