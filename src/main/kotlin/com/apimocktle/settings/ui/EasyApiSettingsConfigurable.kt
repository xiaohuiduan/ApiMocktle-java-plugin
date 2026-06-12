package com.apimocktle.settings.ui

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
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

    companion object {
        private var initialTab: String? = null
        fun selectTab(tabName: String) { initialTab = tabName }
    }

    override fun getDisplayName(): String = "ApiMocktle"

    override fun createComponent(): JComponent {
        if (panel == null) {
            panel = JPanel(BorderLayout())
            tabs = JTabbedPane().also { t ->
                t.addTab("API 扫描", apiScanPanel.component)
                t.addTab("YAPI 导出", yapiExportPanel.component)
                t.addTab("Mock Agent", mockAgentPanel.component)
                t.addTab("扩展配置", extensionPanel.component)
                t.addTab("高级", advancedPanel.component)
            }
            panel!!.add(tabs, BorderLayout.CENTER)
        }
        reset()
        selectInitialTab()
        return panel!!
    }

    private fun selectInitialTab() {
        val tabName = initialTab
        if (tabName != null && tabs != null) {
            for (i in 0 until tabs!!.tabCount) {
                if (tabs!!.getTitleAt(i) == tabName) {
                    tabs!!.selectedIndex = i
                    break
                }
            }
            initialTab = null
        }
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

abstract class BaseApiMocktleChildConfigurable(
    private val displayName: String,
    private val panelFactory: () -> SettingsPanel
) : Configurable {
    private var panelContainer: JPanel? = null
    private val panel: SettingsPanel by lazy { panelFactory() }
    protected var project: com.intellij.openapi.project.Project? = null

    protected val settingBinder: SettingBinder? by lazy {
        project?.let { SettingBinder.getInstance(it) }
            ?: ProjectManager.getInstance().openProjects.firstOrNull()?.let { SettingBinder.getInstance(it) }
    }

    override fun getDisplayName(): String = displayName
    override fun createComponent(): JComponent {
        if (panelContainer == null) {
            panelContainer = JPanel(BorderLayout())
            panelContainer!!.add(panel.component, BorderLayout.CENTER)
        }
        reset()
        return panelContainer!!
    }
    override fun isModified(): Boolean {
        val settings = settingBinder?.read() ?: return false; return panel.isModified(settings)
    }
    override fun apply() {
        val binder = settingBinder ?: return; val settings = binder.read(); panel.applyTo(settings); binder.save(settings)
    }
    override fun reset() { panel.resetFrom(settingBinder?.read()) }
    override fun disposeUIResources() { panelContainer = null }
}

class ApiMocktleAdvancedConfigurable(project: com.intellij.openapi.project.Project) : BaseApiMocktleChildConfigurable("高级", { AdvancedSettingsPanel(project) }) { init { this.project = project } }
