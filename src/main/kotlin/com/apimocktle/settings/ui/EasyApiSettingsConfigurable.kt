package com.apimocktle.settings.ui

import com.intellij.openapi.options.Configurable
import com.apimocktle.i18n.ApiMocktleBundle
import com.apimocktle.settings.SettingBinder
import com.apimocktle.settings.ui.*
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTabbedPane

class ApiMocktleSettingsConfigurable(private val project: com.intellij.openapi.project.Project) : Configurable {
    private var panel: JPanel? = null
    private var tabs: JTabbedPane? = null

    private val settingBinder: SettingBinder by lazy {
        SettingBinder.getInstance(project)
    }

    private val apiScanPanel: SettingsPanel = ApiScanPanel(project)
    private val yapiExportPanel: SettingsPanel = YapiExportPanel(project)
    private val mockAgentPanel: SettingsPanel = MockAgentPanel(project)
    private val extensionPanel: SettingsPanel = ExtensionConfigPanel(project)
    private val advancedPanel: SettingsPanel = AdvancedSettingsPanel(project)

    override fun getDisplayName(): String = "ApiMocktle"

    override fun createComponent(): JComponent {
        if (panel == null) {
            panel = JPanel(BorderLayout())
            tabs = JTabbedPane().also { t ->
                t.addTab(ApiMocktleBundle.message("settings.section.apiScanTab"), apiScanPanel.component)
                t.addTab(ApiMocktleBundle.message("settings.section.exportToApiMocktleTab"), yapiExportPanel.component)
                t.addTab(ApiMocktleBundle.message("settings.section.mockAgentTab"), mockAgentPanel.component)
                t.addTab(ApiMocktleBundle.message("settings.section.extensionConfigTab"), extensionPanel.component)
                t.addTab(ApiMocktleBundle.message("settings.section.advancedTab"), advancedPanel.component)
            }
            panel!!.add(tabs, BorderLayout.CENTER)
        }
        reset()
        return panel!!
    }

    override fun isModified(): Boolean {
        val settings = settingBinder.read()
        return listOf(
            apiScanPanel, yapiExportPanel, mockAgentPanel, extensionPanel, advancedPanel
        ).any { it.isModified(settings) }
    }

    override fun apply() {
        val settings = settingBinder.read()
        apiScanPanel.applyTo(settings)
        yapiExportPanel.applyTo(settings)
        mockAgentPanel.applyTo(settings)
        extensionPanel.applyTo(settings)
        advancedPanel.applyTo(settings)
        settingBinder.save(settings)
    }

    override fun reset() {
        val settings = settingBinder.read()
        apiScanPanel.resetFrom(settings)
        yapiExportPanel.resetFrom(settings)
        mockAgentPanel.resetFrom(settings)
        extensionPanel.resetFrom(settings)
        advancedPanel.resetFrom(settings)
    }

    override fun disposeUIResources() {
        panel = null
        tabs = null
    }
}

