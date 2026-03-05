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

/**
 * Startup hook that applies the native vibrancy effect when a project is opened.
 *
 * Registered as a `postStartupActivity` in `plugin.xml`, this activity is invoked by the
 * IntelliJ Platform after the project frame has been created. It delegates the actual native
 * setup to [MacVibrancyEffect.applyVibrancy].
 *
 * ## Timing strategy
 *
 * The IDE frame may not be fully realized immediately after the project opens.
 * This activity uses a two-phase approach:
 *
 * 1. **First attempt** — waits 800 ms, then retrieves the frame from [WindowManager].
 * 2. **Retry** — if the frame was not available, waits an additional 2 000 ms and tries
 *    again, also checking [KeyboardFocusManager] as a fallback.
 *
 * @see MacVibrancyEffect
 * @see ProjectActivity
 * @since 1.0.0
 */
class VibrancyProjectActivity : ProjectActivity {

    /**
     * Executes the vibrancy setup for the given [project].
     *
     * This suspend function runs on the coroutine context provided by the IntelliJ Platform.
     * Frame access and vibrancy application happen on the EDT via [Dispatchers.EDT].
     *
     * @param project the project whose IDE frame should receive the vibrancy effect
     */
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
