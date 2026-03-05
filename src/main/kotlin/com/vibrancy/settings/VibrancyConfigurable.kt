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

class VibrancyConfigurable : BoundConfigurable("Vibrancy") {

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
        private const val DEFAULT_THEME = "Default Dark"
        private const val DEFAULT_MATERIAL = 12

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

        private val MATERIAL_NAMES = MATERIALS.keys.toList()

        private val APPEARANCE_NAMES = listOf("Auto", "Vibrant Light", "Vibrant Dark")

        private fun materialNameFor(value: Int): String =
            MATERIALS.entries.find { it.value == value }?.key ?: "UnderWindowBackground"
    }
}
