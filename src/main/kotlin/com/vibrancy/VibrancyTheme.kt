package com.vibrancy

enum class VibrancyTheme(
    val displayName: String,
    val material: Int,
    val windowAlpha: Double,
    val appearance: Int,         // 0=auto, 1=vibrant light, 2=vibrant dark
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
        fun fromName(name: String): VibrancyTheme =
            entries.find { it.displayName == name } ?: DEFAULT_DARK
    }
}
