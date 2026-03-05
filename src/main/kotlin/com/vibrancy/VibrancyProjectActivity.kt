package com.vibrancy

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.awt.KeyboardFocusManager
import javax.swing.JFrame

class VibrancyProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        // Wait for the frame to be fully realized after project open
        delay(800)

        val applied = withContext(Dispatchers.EDT) {
            val frame = WindowManager.getInstance().getFrame(project)
            if (frame != null) {
                MacVibrancyEffect.applyVibrancy(frame)
                true
            } else {
                false
            }
        }

        if (!applied) {
            delay(2000)
            withContext(Dispatchers.EDT) {
                val frame = WindowManager.getInstance().getFrame(project)
                    ?: (KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow as? JFrame)

                if (frame != null) {
                    MacVibrancyEffect.applyVibrancy(frame)
                } else {
                    LOG.warn("Could not find a valid JFrame for project: ${project.name}")
                }
            }
        }
    }

    companion object {
        private val LOG = Logger.getInstance(VibrancyProjectActivity::class.java)
    }
}
