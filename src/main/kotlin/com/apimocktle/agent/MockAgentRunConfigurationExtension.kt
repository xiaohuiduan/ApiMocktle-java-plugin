package com.apimocktle.agent

import com.intellij.execution.JavaRunConfigurationExtensionManager
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
import com.apimocktle.settings.SettingBinder
import java.io.File
import javax.swing.JComponent

/**
 * RunConfigurationExtension，在 Application 类型的运行配置执行前
 * 动态注入 `-javaagent:mock-agent.jar` 参数。
 *
 * 仅当用户在插件设置中开启「自动注入 Mock Agent」时生效。
 * 注入失败时静默跳过，不影响应用正常启动。
 */
class MockAgentRunConfigurationExtension : RunConfigurationExtension() {

    companion object {
        private const val AGENT_JAR_NAME = "mock-agent.jar"
        private const val AGENT_FLAG_PREFIX = "-javaagent:"
        private val log = Logger.getInstance(MockAgentRunConfigurationExtension::class.java)

        /**
         * 查找 mock-agent.jar 文件，按优先级搜索多个位置。
         */
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

        /**
         * 检查指定的 JVM 参数列表中是否已包含 mock-agent 的 -javaagent 参数。
         */
        fun hasAgentParameter(vmParams: List<String>): Boolean {
            return vmParams.any { it.startsWith(AGENT_FLAG_PREFIX) && it.contains(AGENT_JAR_NAME) }
        }
    }

    override fun <T : RunConfigurationBase<*>> updateJavaParameters(
        configuration: T,
        params: JavaParameters,
        runnerSettings: RunnerSettings?
    ) {
        val project = configuration.project
        val settings = SettingBinder.getInstance(project).tryRead() ?: return

        // 检查全局开关
        if (!settings.autoInjectAgent) return

        // 避免重复注入
        val vmParamsList = params.vmParametersList.parameters
        if (hasAgentParameter(vmParamsList)) return

        // 查找 agent JAR
        val agentJar = findAgentJar()
        if (agentJar == null) {
            log.warn("[MockAgent] mock-agent.jar not found, skipping injection")
            notifyWarning(project, "找不到 mock-agent.jar，Mock Agent 注入已跳过")
            return
        }

        // 注入 -javaagent 参数
        // 如果路径包含空格，需要用引号包裹
        val agentPath = agentJar.absolutePath
        val agentParam = if (agentPath.contains(" ")) {
            "${AGENT_FLAG_PREFIX}\"$agentPath\""
        } else {
            "$AGENT_FLAG_PREFIX$agentPath"
        }

        params.vmParametersList.add(agentParam)
        log.info("[MockAgent] Injected $agentParam into ${configuration.name}")
    }

    override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean {
        // 仅对 Application 类型的 RunConfiguration 生效
        // 通过检查类名来判断，避免硬依赖 ApplicationConfiguration 类
        val configClassName = configuration.javaClass.name
        return configClassName.contains("ApplicationConfiguration") ||
                configClassName.contains("SpringBoot") ||
                configClassName.contains("Spring")
    }

    override fun getEditorTitle(): String = "Mock Agent"

    override fun <P : RunConfigurationBase<*>> createEditor(configuration: P): SettingsEditor<P>? {
        // 不在 RunConfiguration 编辑器中显示额外的 tab
        return null
    }

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
}
