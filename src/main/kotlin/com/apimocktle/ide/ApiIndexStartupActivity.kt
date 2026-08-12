package com.apimocktle.ide

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.apimocktle.cache.ApiFileChangeListener
import com.apimocktle.cache.ApiIndexManager
import com.apimocktle.cache.VcsBranchChangeListener
import com.apimocktle.config.ConfigSyncService
import com.apimocktle.core.threading.backgroundAsync
import com.apimocktle.settings.SettingBinder
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * Project activity that initializes the API index services.
 *
 * Migrated from StartupActivity to ProjectActivity for better startup performance
 * and modern coroutine-based initialization.
 *
 * Uses [backgroundAsync] to ensure all downstream PSI operations run on clean
 * background threads without inherited EDT context.
 *
 * @see ApiIndexManager
 * @see ApiFileChangeListener
 */
class ApiIndexStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        backgroundAsync {
            DumbModeHelper.waitForSmartMode(project)

            delay(5.seconds)

            ConfigSyncService.getInstance(project).start()
            ApiFileChangeListener.getInstance(project).start()
            VcsBranchChangeListener.getInstance(project).start()

            val settings = SettingBinder.getInstance(project).read()
            val autoScan = settings.autoScanEnabled

            ApiIndexManager.getInstance(project).start(triggerInitialScan = autoScan)
        }
    }
}
