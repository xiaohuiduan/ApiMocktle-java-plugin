package com.apimocktle.agent

import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.apimocktle.ide.ui.AgentStatusColors
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * Agent Addresses 面板，展示所有已注册 Agent 的地址和状态。
 *
 * 状态指示：
 * - 🟢 绿灯：已连接 + 已激活 → 就绪，可接收 Mock 规则
 * - 🟡 黄灯：已连接 + 已暂停 → 已暂停，不接收 Mock 规则
 * - 🔴 红灯：未连接
 */
class AgentAddressesPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val agentsContainer = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
    }

    private val emptyLabel = JLabel(
        "<html><center><b>暂无已配置的 Agent</b><br>启动带 Mock Agent 的运行配置后会自动注册到这里<br>可在 设置 → Mock Agent 开启/关闭自动注入</center></html>",
        SwingConstants.CENTER
    ).apply {
        foreground = AgentStatusColors.Muted
    }

    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "AgentAddressesPanel-refresh").apply { isDaemon = true }
    }
    private var refreshTask: ScheduledFuture<*>? = null

    init {
        background = UIUtil.getPanelBackground()
        border = EmptyBorder(8, 8, 8, 8)

        val scrollPane = JScrollPane(agentsContainer).apply {
            border = BorderFactory.createEmptyBorder()
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }

        add(emptyLabel, BorderLayout.CENTER)
        add(scrollPane, BorderLayout.NORTH)

        refresh()
        startAutoRefresh()
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
        }, 3, 3, TimeUnit.SECONDS)
    }

    private fun refresh() {
        val manager = project.getService(MockAgentManager::class.java)
        manager.probeAllStatus()
        val agents = manager.getAgents()

        agentsContainer.removeAll()

        if (agents.isEmpty()) {
            emptyLabel.isVisible = true
            agentsContainer.isVisible = false
        } else {
            emptyLabel.isVisible = false
            agentsContainer.isVisible = true

            for (agent in agents) {
                agentsContainer.add(createAgentRow(agent, manager))
                agentsContainer.add(Box.createRigidArea(Dimension(0, 4)))
            }
        }

        agentsContainer.revalidate()
        agentsContainer.repaint()
    }

    private fun createAgentRow(agent: AgentInfo, manager: MockAgentManager): JPanel {
        val row = JPanel(BorderLayout()).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.border(), 1, true),
                EmptyBorder(6, 8, 6, 8)
            )
            background = UIUtil.getListBackground()
            maximumSize = Dimension(Int.MAX_VALUE, 56)
        }

        // 状态：绿灯=就绪，黄灯=已暂停，红灯=未连接
        val dotColor = when {
            !agent.connected -> AgentStatusColors.Offline
            !agent.active -> AgentStatusColors.Paused
            else -> AgentStatusColors.Ready
        }
        val statusText = when {
            !agent.connected -> "未连接"
            !agent.active -> "已暂停，不接收 Mock 规则"
            else -> "就绪，可接收 Mock 规则"
        }
        val statusTextColor = when {
            !agent.connected -> AgentStatusColors.Offline
            !agent.active -> AgentStatusColors.Paused
            else -> AgentStatusColors.Ready
        }

        val statusDot = createStatusDot(dotColor)
        val nameLabel = JLabel(agent.name).apply {
            font = font.deriveFont(Font.BOLD)
        }
        val addressLabel = JLabel("→ ${agent.address}").apply {
            foreground = AgentStatusColors.Muted
        }
        val statusLabel = JLabel(statusText).apply {
            foreground = statusTextColor
            font = font.deriveFont(Font.PLAIN, font.size2D - 1f)
        }

        // 左侧：状态点 + 名称 + 地址 + 状态文字
        val infoPanel = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }

        val topLine = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            isOpaque = false
            add(statusDot)
            add(nameLabel)
            add(addressLabel)
        }
        val bottomLine = JPanel(FlowLayout(FlowLayout.LEFT, 16, 0)).apply {
            isOpaque = false
            add(statusLabel)
        }

        infoPanel.add(topLine)
        infoPanel.add(bottomLine)

        // 右侧：操作按钮
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
            isOpaque = false
        }

        val activeButton = JButton(if (agent.active) "暂停" else "激活").apply {
            font = font.deriveFont(Font.PLAIN, 11f)
            addActionListener {
                manager.setActive(agent.runConfigId ?: agent.name, !agent.active)
                refresh()
            }
        }

        val copyButton = JButton("复制地址").apply {
            font = font.deriveFont(Font.PLAIN, 11f)
            addActionListener {
                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                clipboard.setContents(StringSelection(agent.address), null)
                text = "已复制"
                isEnabled = false
                Timer(1200) {
                    text = "复制地址"
                    isEnabled = true
                }.apply { isRepeats = false; start() }
            }
        }

        val logButton = JButton("查看日志").apply {
            font = font.deriveFont(Font.PLAIN, 11f)
            isEnabled = agent.connected
            addActionListener {
                showLogWindow(agent, manager)
            }
        }

        buttonPanel.add(activeButton)
        buttonPanel.add(copyButton)
        buttonPanel.add(logButton)

        row.add(infoPanel, BorderLayout.WEST)
        row.add(buttonPanel, BorderLayout.EAST)

        return row
    }

    private fun createStatusDot(color: Color): JLabel {
        return object : JLabel() {
            init {
                preferredSize = JBUI.size(10, 10)
                minimumSize = JBUI.size(10, 10)
            }

            override fun paintComponent(g: Graphics) {
                val g2 = g as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = color
                g2.fillOval(1, 1, 8, 8)
            }
        }
    }

    private fun showLogWindow(agent: AgentInfo, manager: MockAgentManager) {
        val logArea = JTextArea().apply {
            isEditable = false
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            rows = 20
            columns = 80
        }

        val refreshBtn = JButton("刷新").apply {
            addActionListener {
                val logs = manager.getAgentLogs(agent.port)
                logArea.text = if (logs.isEmpty()) {
                    "暂无日志"
                } else {
                    logs.joinToString("\n") { entry ->
                        val ts = entry["timestamp"] ?: ""
                        val level = entry["level"] ?: ""
                        val message = entry["message"] ?: ""
                        "[$ts] [$level] $message"
                    }
                }
                logArea.caretPosition = logArea.document.length
            }
        }

        val bottomPanel = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
            add(refreshBtn)
        }

        val content = JPanel(BorderLayout()).apply {
            add(JScrollPane(logArea), BorderLayout.CENTER)
            add(bottomPanel, BorderLayout.SOUTH)
            border = JBUI.Borders.empty(4)
        }

        val dialog = JDialog(SwingUtilities.getWindowAncestor(this), "Agent 日志 - ${agent.name}")
        dialog.contentPane = content
        dialog.setSize(700, 450)
        dialog.setLocationRelativeTo(this)
        dialog.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE

        refreshBtn.doClick()
        dialog.isVisible = true
    }

    fun dispose() {
        refreshTask?.cancel(false)
        scheduler.shutdown()
    }
}
