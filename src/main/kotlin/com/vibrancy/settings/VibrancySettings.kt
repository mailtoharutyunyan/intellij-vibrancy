package com.vibrancy.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Application-level persistent settings for the Vibrancy plugin.
 *
 * Settings are serialized to `VibrancySettings.xml` inside the IDE configuration directory
 * and survive IDE restarts. The settings UI is provided by [VibrancyConfigurable].
 *
 * ### Retrieving the current settings
 * ```kotlin
 * val state = VibrancySettings.instance.state
 * println(state.theme)   // e.g. "Default Dark"
 * println(state.enabled) // true
 * ```
 *
 * @see VibrancyConfigurable
 * @see com.vibrancy.MacVibrancyEffect
 * @since 1.0.0
 */
@State(name = "VibrancySettings", storages = [Storage("VibrancySettings.xml")])
class VibrancySettings : PersistentStateComponent<VibrancySettings.State> {

    /**
     * Serializable state holder for all vibrancy-related preferences.
     *
     * All fields use default values matching the [DEFAULT_DARK][com.vibrancy.VibrancyTheme.DEFAULT_DARK] theme.
     * The IntelliJ XML serializer requires a no-arg constructor and mutable properties.
     */
    class State {
        /** The selected [VibrancyTheme][com.vibrancy.VibrancyTheme] preset name. */
        var theme: String = "Default Dark"

        /**
         * Custom opacity slider value (0–100).
         *
         * Mapped to `NSWindow.alphaValue` in the range `[0.75, 1.0]` by
         * [MacVibrancyEffect.computeCustomAlpha][com.vibrancy.MacVibrancyEffect].
         * Only used when [theme] is `"Custom"`.
         */
        var opacity: Int = 85

        /**
         * The raw `NSVisualEffectMaterial` value. Only used when [theme] is `"Custom"`.
         *
         * @see com.vibrancy.VibrancyTheme for the full material reference table
         */
        var material: Int = 12

        /**
         * The appearance mode. Only used when [theme] is `"Custom"`.
         *
         * - `0` — Auto (no `NSAppearance` override)
         * - `1` — Vibrant Light (`NSAppearanceNameVibrantLight`)
         * - `2` — Vibrant Dark (`NSAppearanceNameVibrantDark`)
         */
        var appearance: Int = 0

        /** Whether the vibrancy effect is currently active. */
        var enabled: Boolean = true
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    companion object {
        /**
         * Returns the application-level [VibrancySettings] service instance.
         *
         * Shorthand for `ApplicationManager.getApplication().getService(VibrancySettings::class.java)`.
         */
        val instance: VibrancySettings
            get() = ApplicationManager.getApplication().getService(VibrancySettings::class.java)
    }
}
