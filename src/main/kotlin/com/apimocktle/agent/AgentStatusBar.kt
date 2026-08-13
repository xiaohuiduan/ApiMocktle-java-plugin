package com.apimocktle.agent

import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import com.apimocktle.ide.ui.AgentStatusColors
import com.apimocktle.settings.SettingBinder
import java.awt.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * Dashboard 中的 Agent 状态指示器，显示连接状态和端口信息。
 * 每 5 秒自动刷新一次状态。
 *
 * 多 Agent 模式下显示已连接数量和端口列表。
 */
class AgentStatusBar(private val project: Project) : JPanel(BorderLayout()) {

    private val statusLabel = JLabel()
    private val portLabel = JLabel()
    private val modeLabel = JLabel()

    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "AgentStatusBar-refresh").apply { isDaemon = true }
    }
    private var refreshTask: ScheduledFuture<*>? = null

    private var lastConnected = false

    init {
        border = EmptyBorder(2, 8, 2, 8)
        background = UIManager.getColor("Panel.background")

        // 状态指示圆点 + 文字
        val statusPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            isOpaque = false
            add(createStatusDot())
            add(statusLabel)
        }

        // 端口 + 模式
        val infoPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            isOpaque = false
            add(JLabel("端口:").apply { foreground = UIManager.getColor("Label.infoForeground") })
            add(portLabel)
            add(Box.createHorizontalStrut(8))
            add(modeLabel)
        }

        add(statusPanel, BorderLayout.WEST)
        add(infoPanel, BorderLayout.EAST)

        refresh()
        startAutoRefresh()
    }

    private fun createStatusDot(): JLabel {
        return object : JLabel() {
            init {
                preferredSize = JBUI.size(10, 10)
                minimumSize = JBUI.size(10, 10)
            }

            override fun paintComponent(g: Graphics) {
                val g2 = g as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = if (lastConnected) AgentStatusColors.Ready else AgentStatusColors.Muted
                g2.fillOval(1, 1, 8, 8)
            }
        }.also { dot ->
            // 刷新时重绘圆点
            statusLabel.putClientProperty("statusDot", dot)
        }
    }

    private fun startAutoRefresh() {
        refreshTask = scheduler.scheduleWithFixedDelay({
            try {
                val tw = com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
                    .getToolWindow("API Dashboard")
                if (tw?.isVisible == true) {
                    SwingUtilities.invokeLater { refresh() }
                }
            } catch (_: Exception) {}
        }, 5, 5, TimeUnit.SECONDS)
    }

    private fun refresh() {
        try {
            val manager = project.getService(MockAgentManager::class.java)
            manager.probeAllStatus()

            val agents = manager.getAgents()
            val connectedCount = agents.count { it.connected }
            lastConnected = connectedCount > 0

            if (agents.isEmpty()) {
                statusLabel.text = "Mock Agent 未配置"
                statusLabel.foreground = AgentStatusColors.Muted
                portLabel.text = "--"
            } else {
                statusLabel.text = "Mock Agent: $connectedCount/${agents.size} 已连接"
                statusLabel.foreground = if (lastConnected) AgentStatusColors.Ready else AgentStatusColors.Muted
                portLabel.text = agents.joinToString(", ") { "${it.name}:${it.port}" }
                portLabel.foreground = if (lastConnected) UIManager.getColor("Label.foreground") else AgentStatusColors.Muted
            }

            val settings = SettingBinder.getInstance(project).tryRead()
            val autoMode = settings?.autoInjectAgent ?: false
            modeLabel.text = if (autoMode) "自动注入: 开启" else "自动注入: 关闭"
            modeLabel.foreground = if (autoMode) AgentStatusColors.Ready else AgentStatusColors.Muted

            // 重绘状态圆点
            repaint()
        } catch (_: Exception) {
            statusLabel.text = "Mock Agent 状态未知"
            statusLabel.foreground = AgentStatusColors.Muted
            portLabel.text = "--"
            modeLabel.text = ""
        }
    }

    fun dispose() {
        refreshTask?.cancel(false)
        scheduler.shutdown()
    }
}
