package com.apimocktle.ide.dialog

import com.intellij.openapi.project.Project
import com.apimocktle.cache.ProjectCacheRepository
import com.apimocktle.util.GsonUtils

/**
 * 导出对话框偏好设置。
 */
data class ExportDialogPreferences(
    val lastExportFormat: String? = null,
    val lastOutputDir: String? = null,
    val lastFileName: String? = null,
    val lastYapiToken: String? = null
)

/**
 * Handles persistence of export dialog preferences.
 * 
 * This class provides functionality to save and load export dialog preferences
 * to/from a JSON file in the project cache directory. Useful for preserving
 * user's last used export options across IDE sessions.
 * 
 * @param project The IntelliJ project context
 */
class ExportDialogPreferencesPersistence(project: Project) {
    private val repo = ProjectCacheRepository.getInstance(project)
    private val key = "export_dialog_preferences.json"

    /**
     * Loads the export dialog preferences.
     * 
     * @return The preferences, or default preferences if none found
     */
    fun load(): ExportDialogPreferences {
        val raw = repo.read(key) ?: return ExportDialogPreferences()
        return runCatching { GsonUtils.fromJson<ExportDialogPreferences>(raw) }.getOrNull()
            ?: ExportDialogPreferences()
    }

    /**
     * Saves the export dialog preferences.
     * 
     * @param preferences The preferences to save
     */
    fun save(preferences: ExportDialogPreferences) {
        repo.write(key, GsonUtils.toJson(preferences))
    }

    /**
     * Clears the export dialog preferences.
     */
    fun reset() {
        repo.delete(key)
    }
}
