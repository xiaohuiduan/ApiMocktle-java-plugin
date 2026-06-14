package com.apimocktle.dashboard

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.apimocktle.agent.AgentAddressesPanel

/**
 * Factory for creating the API Dashboard tool window in IntelliJ IDEA.
 *
 * 包含两个 tab：
 * - API Endpoints：展示所有 API 端点
 * - Agent Addresses：展示所有已注册 Agent 的地址和状态
 */
class ApiDashboardToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = toolWindow.contentManager.factory

        // Tab 1: API Endpoints
        val apiPanel = ApiDashboardPanel(project)
        val service = ApiDashboardService.getInstance(project)
        service.setDashboardPanel(apiPanel)
        val apiContent = contentFactory.createContent(apiPanel, "API Endpoints", false)
        apiContent.setDisposer(Disposable { apiPanel.dispose() })

        // Tab 2: Agent Addresses
        val agentPanel = AgentAddressesPanel(project)
        val agentContent = contentFactory.createContent(agentPanel, "Agent Addresses", false)
        agentContent.setDisposer(Disposable { agentPanel.dispose() })

        toolWindow.contentManager.addContent(apiContent)
        toolWindow.contentManager.addContent(agentContent)
    }
}
