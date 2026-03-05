package com.vibrancy.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "VibrancySettings", storages = [Storage("VibrancySettings.xml")])
class VibrancySettings : PersistentStateComponent<VibrancySettings.State> {

    class State {
        // Theme preset name
        var theme: String = "Default Dark"

        // Custom settings (used when theme is "Custom")
        var opacity: Int = 85        // 0-100: window alpha mapped to 0.75-1.0
        var material: Int = 12       // NSVisualEffectMaterial
        var appearance: Int = 0      // 0=auto, 1=light, 2=dark

        // General settings
        var enabled: Boolean = true
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    companion object {
        val instance: VibrancySettings
            get() = ApplicationManager.getApplication().getService(VibrancySettings::class.java)
    }
}
