package com.apimocktle.agent

import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
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
 * 每个 Agent 显示：
 * - 状态圆点（🟢 已连接 / 🔴 未连接）
 * - 服务名
 * - 地址（host:port）
 * - 操作按钮：激活/取消激活、复制地址、查看日志
 */
class AgentAddressesPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val agentsContainer = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
    }

    private val emptyLabel = JLabel("暂无已配置的 Agent", SwingConstants.CENTER).apply {
        foreground = Color(0x999999)
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
                SwingUtilities.invokeLater { refresh() }
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
                BorderFactory.createLineBorder(UIUtil.getBoundsColor() ?: Color(0xDDDDDD), 1, true),
                EmptyBorder(6, 8, 6, 8)
            )
            background = UIUtil.getListBackground()
            maximumSize = Dimension(Int.MAX_VALUE, 40)
        }

        // 左侧：状态点 + 服务名 + 地址
        val statusDot = createStatusDot(agent.connected)
        val nameLabel = JLabel(agent.name).apply {
            font = font.deriveFont(Font.BOLD)
        }
        val addressLabel = JLabel("→ ${agent.address}").apply {
            foreground = Color(0x666666)
        }

        val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            isOpaque = false
            add(statusDot)
            add(nameLabel)
            add(addressLabel)
        }

        // 右侧：操作按钮
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
            isOpaque = false
        }

        // 激活/取消激活按钮
        val activeButton = JButton(if (agent.active) "取消激活" else "激活").apply {
            font = font.deriveFont(Font.PLAIN, 11f)
            addActionListener {
                manager.setActive(agent.runConfigId ?: agent.name, !agent.active)
                refresh()
            }
        }

        // 复制地址按钮
        val copyButton = JButton("复制地址").apply {
            font = font.deriveFont(Font.PLAIN, 11f)
            addActionListener {
                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                clipboard.setContents(StringSelection(agent.address), null)
            }
        }

        // 查看日志按钮
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

        row.add(leftPanel, BorderLayout.WEST)
        row.add(buttonPanel, BorderLayout.EAST)

        return row
    }

    private fun createStatusDot(connected: Boolean): JLabel {
        return object : JLabel() {
            init {
                preferredSize = JBUI.size(10, 10)
                minimumSize = JBUI.size(10, 10)
            }

            override fun paintComponent(g: Graphics) {
                val g2 = g as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = if (connected) Color(0x2da44e) else Color(0xCF6A4C)
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

        // 初始加载日志
        refreshBtn.doClick()

        dialog.isVisible = true
    }

    fun dispose() {
        refreshTask?.cancel(false)
        scheduler.shutdown()
    }
}
