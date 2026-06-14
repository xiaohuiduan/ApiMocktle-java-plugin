package com.apimocktle.agent

import com.intellij.execution.RunConfigurationExtension
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import org.jdom.Element
import java.awt.BorderLayout
import java.io.File
import javax.swing.*

/**
 * RunConfigurationExtension，在 Application 类型的运行配置执行前
 * 动态注入 `-javaagent:mock-agent.jar` 参数。
 *
 * 配置持久化方式：通过 readExternal/writeExternal 直接存在 Run Configuration 的 XML 中。
 * 每个 Run Configuration 有独立的：
 * - "Enable ApiMocktle Agent" checkbox（默认勾选）
 * - Port 输入框（留空自动分配）
 */
class MockAgentRunConfigurationExtension : RunConfigurationExtension() {

    companion object {
        private const val AGENT_JAR_NAME = "mock-agent.jar"
        private const val AGENT_FLAG_PREFIX = "-javaagent:"
        private const val DEFAULT_PORT = 19876
        private val log = Logger.getInstance(MockAgentRunConfigurationExtension::class.java)

        /** 存储在 RunConfiguration UserData 中的 agent 配置 Key */
        private val AGENT_ENABLED_KEY = Key.create<Boolean>("apimocktle.agent.enabled")
        private val AGENT_PORT_KEY = Key.create<Int>("apimocktle.agent.port")

        fun findAgentJar(): File? {
            // 1. 插件安装目录 lib/
            try {
                val pluginsPath = File(PathManager.getPluginsPath(), "ApiMocktle/lib/$AGENT_JAR_NAME")
                if (pluginsPath.exists()) return pluginsPath
            } catch (_: Exception) {}

            // 2. IDEA 配置目录下的插件
            try {
                val configPath = File(PathManager.getConfigPath())
                val candidates = listOf(
                    File(configPath, "plugins/ApiMocktle/lib/$AGENT_JAR_NAME"),
                    File(configPath.parentFile, "plugins/ApiMocktle/lib/$AGENT_JAR_NAME"),
                )
                for (candidate in candidates) {
                    if (candidate.exists()) return candidate
                }
            } catch (_: Exception) {}

            // 3. CodeSource 同级目录
            try {
                val codeSource = MockAgentRunConfigurationExtension::class.java
                    .protectionDomain?.codeSource?.location
                if (codeSource != null) {
                    val libDir = File(codeSource.toURI()).parentFile
                    val agentInLib = File(libDir, AGENT_JAR_NAME)
                    if (agentInLib.exists()) return agentInLib
                }
            } catch (_: Exception) {}

            // 4. classpath 资源 -> 临时文件
            try {
                val tempDir = File(System.getProperty("java.io.tmpdir"), "apimocktle-agent")
                tempDir.mkdirs()
                val target = File(tempDir, AGENT_JAR_NAME)
                if (target.exists() && target.length() > 1000) return target

                val stream = MockAgentRunConfigurationExtension::class.java
                    .getResourceAsStream("/$AGENT_JAR_NAME")
                    ?: MockAgentRunConfigurationExtension::class.java.classLoader
                        .getResourceAsStream(AGENT_JAR_NAME)
                if (stream != null) {
                    stream.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (target.length() > 1000) return target
                }
            } catch (_: Exception) {}

            return null
        }

        fun hasAgentParameter(vmParams: List<String>): Boolean {
            return vmParams.any { it.startsWith(AGENT_FLAG_PREFIX) && it.contains(AGENT_JAR_NAME) }
        }
    }

    // ==================== 配置持久化（readExternal / writeExternal） ====================

    override fun readExternal(configuration: RunConfigurationBase<*>, element: Element) {
        val agentElement = element.getChild("apimocktle-agent")
        if (agentElement != null) {
            val enabled = agentElement.getAttributeValue("enabled")?.toBooleanStrictOrNull() ?: true
            val port = agentElement.getAttributeValue("port")?.toIntOrNull() ?: 0
            configuration.putUserData(AGENT_ENABLED_KEY, enabled)
            configuration.putUserData(AGENT_PORT_KEY, port)
        } else {
            // 没有配置时默认启用
            configuration.putUserData(AGENT_ENABLED_KEY, true)
            configuration.putUserData(AGENT_PORT_KEY, 0)
        }
    }

    override fun writeExternal(configuration: RunConfigurationBase<*>, element: Element) {
        val enabled = configuration.getUserData(AGENT_ENABLED_KEY) ?: true
        val port = configuration.getUserData(AGENT_PORT_KEY) ?: 0

        // 移除旧的子元素
        element.removeChild("apimocktle-agent")

        val agentElement = Element("apimocktle-agent")
        agentElement.setAttribute("enabled", enabled.toString())
        agentElement.setAttribute("port", port.toString())
        element.addContent(agentElement)
    }

    // ==================== Run Configuration UI ====================

    override fun <P : RunConfigurationBase<*>> createEditor(configuration: P): SettingsEditor<P>? {
        @Suppress("UNCHECKED_CAST")
        return AgentSettingsEditor() as SettingsEditor<P>
    }

    override fun getEditorTitle(): String = "ApiMocktle Agent"

    // ==================== 参数注入 ====================

    override fun <T : RunConfigurationBase<*>> updateJavaParameters(
        configuration: T,
        params: JavaParameters,
        runnerSettings: RunnerSettings?
    ) {
        val project = configuration.project
        val runConfigId = configuration.name

        // 从 UserData 读取配置（readExternal 已加载）
        val enabled = configuration.getUserData(AGENT_ENABLED_KEY) ?: true
        if (!enabled) return

        // 移除已有的旧 agent 参数（用户手动配置的或其他版本遗留的）
        val vmParamsList = params.vmParametersList.parameters
        val existingIndices = vmParamsList.indices.filter { i ->
            vmParamsList[i].startsWith(AGENT_FLAG_PREFIX) && vmParamsList[i].contains(AGENT_JAR_NAME)
        }
        for (i in existingIndices.reversed()) {
            params.vmParametersList.parameters.removeAt(i)
        }

        // 查找 agent JAR
        val agentJar = findAgentJar()
        if (agentJar == null) {
            log.warn("[MockAgent] mock-agent.jar not found, skipping injection")
            notifyWarning(project, "找不到 mock-agent.jar，Mock Agent 注入已跳过")
            return
        }

        // 确定端口：用户指定 > 自动分配
        val manager = project.getService(MockAgentManager::class.java)
        val userPort = configuration.getUserData(AGENT_PORT_KEY) ?: 0
        val port = if (userPort > 0) {
            if (manager.isPortOccupiedByAgent(userPort)) {
                notifyWarning(project, "端口 $userPort 已被其他 Agent 占用，请修改端口配置")
                return
            }
            userPort
        } else {
            manager.allocatePort()
        }

        // 注册 agent
        manager.registerAgent(runConfigId, configuration.name, port)

        // 构建 -javaagent 参数
        val agentPath = agentJar.absolutePath
        val agentParam = if (agentPath.contains(" ")) {
            "${AGENT_FLAG_PREFIX}\"$agentPath\"=mode=spring,port=$port"
        } else {
            "$AGENT_FLAG_PREFIX$agentPath=mode=spring,port=$port"
        }

        params.vmParametersList.add(agentParam)
        log.info("[MockAgent] Injected $agentParam into ${configuration.name}")
    }

    override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean {
        val configClassName = configuration.javaClass.name
        return configClassName.contains("ApplicationConfiguration") ||
                configClassName.contains("SpringBoot") ||
                configClassName.contains("Spring")
    }

    // ==================== 工具方法 ====================

    private fun notifyWarning(project: Project, message: String) {
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("ApiMocktle Notifications")
                .createNotification(message, NotificationType.WARNING)
                .notify(project)
        } catch (_: Exception) {
            log.warn("[MockAgent] $message")
        }
    }

    // ==================== Settings Editor ====================

    /**
     * Run Configuration 编辑器中的 Agent 配置面板。
     * 配置存储在 RunConfiguration 的 UserData 中，通过 writeExternal 持久化到 XML。
     */
    private class AgentSettingsEditor : SettingsEditor<RunConfigurationBase<*>>() {

        private val enabledCheckBox = JCheckBox("Enable ApiMocktle Agent").apply {
            isSelected = true
        }
        private val portField = JTextField().apply {
            columns = 10
        }
        private val hintLabel = JLabel("留空则自动分配空闲端口").apply {
            foreground = com.intellij.util.ui.UIUtil.getContextHelpForeground()
            font = font.deriveFont(font.size2D - 1f)
        }
        private val panel: JPanel

        init {
            val portPanel = JPanel(BorderLayout()).apply {
                add(portField, BorderLayout.NORTH)
                add(hintLabel, BorderLayout.SOUTH)
            }

            val formPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent("Port:", portPanel)
                .addComponentFillVertically(JPanel(), 0)
                .panel

            panel = JPanel(BorderLayout()).apply {
                add(enabledCheckBox, BorderLayout.NORTH)
                add(formPanel, BorderLayout.CENTER)
                border = JBUI.Borders.empty(8)
            }

            enabledCheckBox.addActionListener {
                portField.isEnabled = enabledCheckBox.isSelected
                hintLabel.isEnabled = enabledCheckBox.isSelected
            }
        }

        override fun resetEditorFrom(config: RunConfigurationBase<*>) {
            val enabled = config.getUserData(AGENT_ENABLED_KEY) ?: true
            val port = config.getUserData(AGENT_PORT_KEY) ?: 0

            enabledCheckBox.isSelected = enabled
            portField.text = if (port > 0) port.toString() else ""
            portField.isEnabled = enabled
        }

        override fun applyEditorTo(config: RunConfigurationBase<*>) {
            config.putUserData(AGENT_ENABLED_KEY, enabledCheckBox.isSelected)
            val port = portField.text.trim().toIntOrNull() ?: 0
            config.putUserData(AGENT_PORT_KEY, port)
        }

        override fun createEditor(): JComponent = panel
    }
}
