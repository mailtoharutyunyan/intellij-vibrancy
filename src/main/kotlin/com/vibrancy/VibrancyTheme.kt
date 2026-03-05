package com.vibrancy

/**
 * Predefined vibrancy theme presets that configure the native macOS blur effect.
 *
 * Each theme specifies a combination of:
 * - An `NSVisualEffectMaterial` value controlling the blur style
 * - A window alpha value controlling overall transparency
 * - An `NSAppearance` mode (light, dark, or auto)
 *
 * The [CUSTOM] entry delegates all three settings to user-defined values in [VibrancySettings][com.vibrancy.settings.VibrancySettings].
 *
 * ## Material reference
 *
 * | Value | Name                   |
 * |-------|------------------------|
 * | 0     | Titlebar               |
 * | 1     | Selection              |
 * | 2     | Menu                   |
 * | 3     | Popover                |
 * | 4     | Sidebar                |
 * | 5     | HeaderView             |
 * | 6     | Sheet                  |
 * | 7     | WindowBackground       |
 * | 8     | HUDWindow              |
 * | 9     | FullScreenUI           |
 * | 10    | ToolTip                |
 * | 11    | ContentBackground      |
 * | 12    | UnderWindowBackground  |
 *
 * @property displayName  the human-readable name shown in the settings UI
 * @property material     the `NSVisualEffectMaterial` raw value
 * @property windowAlpha  the `NSWindow.alphaValue` (0.0–1.0) — lower means more transparent
 * @property appearance   the appearance mode: `0` = auto, `1` = Vibrant Light, `2` = Vibrant Dark
 * @property description  a short description displayed below the theme selector
 * @see MacVibrancyEffect
 * @since 1.0.0
 */
enum class VibrancyTheme(
    val displayName: String,
    val material: Int,
    val windowAlpha: Double,
    val appearance: Int,
    val description: String
) {
    DEFAULT_DARK(
        "Default Dark",
        material = 12,           // UnderWindowBackground
        windowAlpha = 0.88,
        appearance = 2,
        description = "Dark frosted glass — balanced transparency"
    ),

    DEFAULT_LIGHT(
        "Default Light",
        material = 12,
        windowAlpha = 0.90,
        appearance = 1,
        description = "Light frosted glass for bright themes"
    ),

    DARK_GLASS(
        "Dark Glass",
        material = 4,            // Sidebar
        windowAlpha = 0.82,
        appearance = 2,
        description = "Strong dark glass — more desktop visible"
    ),

    FROSTED_GLASS(
        "Frosted Glass",
        material = 8,            // HUDWindow
        windowAlpha = 0.85,
        appearance = 2,
        description = "HUD-style deep frosted glass"
    ),

    NOIR(
        "Noir",
        material = 0,            // Titlebar
        windowAlpha = 0.92,
        appearance = 2,
        description = "Minimal — subtle dark with slight transparency"
    ),

    VIBRANT_DARK(
        "Vibrant Dark",
        material = 11,           // ContentBackground
        windowAlpha = 0.80,
        appearance = 2,
        description = "Maximum vibrancy — strong transparency"
    ),

    VIBRANT_LIGHT(
        "Vibrant Light",
        material = 11,
        windowAlpha = 0.84,
        appearance = 1,
        description = "Strong light vibrancy"
    ),

    CUSTOM(
        "Custom",
        material = 12,
        windowAlpha = 0.88,
        appearance = 0,
        description = "Configure material and intensity manually"
    );

    companion object {
        /**
         * Resolves a [VibrancyTheme] by its [displayName].
         *
         * @param name the display name to look up (e.g., `"Default Dark"`, `"Custom"`)
         * @return the matching theme, or [DEFAULT_DARK] if no match is found
         */
        fun fromName(name: String): VibrancyTheme =
            entries.find { it.displayName == name } ?: DEFAULT_DARK
    }
}
