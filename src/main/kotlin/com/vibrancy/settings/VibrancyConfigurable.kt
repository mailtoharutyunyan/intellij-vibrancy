package com.vibrancy.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.ComponentPredicate
import com.vibrancy.MacVibrancyEffect
import com.vibrancy.VibrancyTheme
import java.awt.Window
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.UIManager

/**
 * Settings page for the Vibrancy plugin, accessible at **Settings | Appearance | Vibrancy**.
 *
 * Provides UI controls for:
 * - Enabling/disabling the vibrancy effect
 * - Selecting a [VibrancyTheme] preset
 * - Configuring custom material, intensity, and appearance (visible only when the **Custom** theme is selected)
 *
 * When the user clicks **Apply** or **OK**, [apply] persists the settings via [VibrancySettings]
 * and immediately pushes the changes to all open IDE frames through [MacVibrancyEffect.updateSettings].
 *
 * Registered in `plugin.xml` under the `appearance` configurable group.
 *
 * @see VibrancySettings
 * @see MacVibrancyEffect.updateSettings
 * @since 1.0.0
 */
class VibrancyConfigurable : BoundConfigurable("Vibrancy") {

    /**
     * Builds the settings panel using the IntelliJ Kotlin UI DSL.
     *
     * The panel layout:
     * 1. **Enable** checkbox — bound to [VibrancySettings.State.enabled]
     * 2. **Theme** combo box — lists all [VibrancyTheme] entries
     * 3. **Description** label — updates dynamically when the theme selection changes
     * 4. **Custom Settings** group (conditionally visible) — intensity slider, material combo, appearance combo
     *
     * @return the constructed [DialogPanel] ready for display
     */
    override fun createPanel(): DialogPanel {
        val state = VibrancySettings.instance.state
        lateinit var themeCombo: ComboBox<String>
        lateinit var descriptionLabel: JLabel

        return panel {
            row {
                checkBox("Enable Vibrancy Effect")
                    .bindSelected(state::enabled)
            }

            separator()

            row("Theme:") {
                themeCombo = comboBox(VibrancyTheme.entries.map { it.displayName })
                    .bindItem(
                        getter = { state.theme },
                        setter = { state.theme = it ?: DEFAULT_THEME }
                    )
                    .applyToComponent {
                        addActionListener {
                            val theme = VibrancyTheme.fromName(selectedItem as? String ?: DEFAULT_THEME)
                            descriptionLabel.text = theme.description
                        }
                    }
                    .component
            }

            row {
                descriptionLabel = label(VibrancyTheme.fromName(state.theme).description)
                    .applyToComponent {
                        foreground = UIManager.getColor("Label.disabledForeground")
                    }
                    .component
            }

            val isCustomTheme = object : ComponentPredicate() {
                override fun invoke(): Boolean =
                    themeCombo.selectedItem == VibrancyTheme.CUSTOM.displayName

                override fun addListener(listener: (Boolean) -> Unit) {
                    themeCombo.addActionListener { listener(invoke()) }
                }
            }

            group("Custom Settings") {
                row("Intensity:") {
                    slider(0, 100, 10, 25)
                        .bindValue(state::opacity)
                }
                row("Material:") {
                    comboBox(MATERIAL_NAMES)
                        .bindItem(
                            getter = { materialNameFor(state.material) },
                            setter = { state.material = MATERIALS[it] ?: DEFAULT_MATERIAL }
                        )
                }
                row("Appearance:") {
                    comboBox(APPEARANCE_NAMES)
                        .bindItem(
                            getter = { APPEARANCE_NAMES.getOrElse(state.appearance) { APPEARANCE_NAMES[0] } },
                            setter = { state.appearance = APPEARANCE_NAMES.indexOf(it).coerceAtLeast(0) }
                        )
                }
            }.visibleIf(isCustomTheme)

            separator()

            row {
                comment("Changes apply when you click <b>Apply</b> or <b>OK</b>.")
            }
        }
    }

    /**
     * Persists the current UI state and applies the vibrancy changes to all open IDE windows.
     *
     * For non-custom themes, the [appearance][VibrancySettings.State.appearance] is automatically
     * synced from the selected [VibrancyTheme] to ensure consistent rendering.
     */
    override fun apply() {
        super.apply()
        val state = VibrancySettings.instance.state
        val theme = VibrancyTheme.fromName(state.theme)
        if (theme != VibrancyTheme.CUSTOM) {
            state.appearance = theme.appearance
        }
        Window.getWindows().filterIsInstance<JFrame>().forEach {
            MacVibrancyEffect.updateSettings(it)
        }
    }

    companion object {
        /** Fallback theme name used when the combo box selection is null. */
        private const val DEFAULT_THEME = "Default Dark"

        /** Fallback `NSVisualEffectMaterial` value (UnderWindowBackground). */
        private const val DEFAULT_MATERIAL = 12

        /**
         * Ordered mapping of `NSVisualEffectMaterial` display names to their raw integer values.
         *
         * Uses [LinkedHashMap] to preserve insertion order for the combo box display.
         */
        private val MATERIALS = linkedMapOf(
            "Titlebar" to 0,
            "Selection" to 1,
            "Menu" to 2,
            "Popover" to 3,
            "Sidebar" to 4,
            "HeaderView" to 5,
            "Sheet" to 6,
            "WindowBackground" to 7,
            "HUDWindow" to 8,
            "FullScreenUI" to 9,
            "ToolTip" to 10,
            "ContentBackground" to 11,
            "UnderWindowBackground" to 12
        )

        /** Display names derived from [MATERIALS] keys, used to populate the material combo box. */
        private val MATERIAL_NAMES = MATERIALS.keys.toList()

        /** Display names for the appearance combo box, indexed to match [VibrancySettings.State.appearance]. */
        private val APPEARANCE_NAMES = listOf("Auto", "Vibrant Light", "Vibrant Dark")

        /**
         * Resolves the display name for a given `NSVisualEffectMaterial` raw [value].
         *
         * @param value the integer material value
         * @return the corresponding display name, or `"UnderWindowBackground"` if not found
         */
        private fun materialNameFor(value: Int): String =
            MATERIALS.entries.find { it.value == value }?.key ?: "UnderWindowBackground"
    }
}
