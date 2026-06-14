package com.apimocktle.agent

import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

/**
 * 监听 Run Configuration 的执行生命周期。
 * 当进程结束时，自动注销对应的 Agent。
 */
class AgentProcessListener : StartupActivity {

    override fun runActivity(project: Project) {
        val connection = project.messageBus.connect()
        connection.subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {
            override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
                val runConfigId = env.runProfile.name
                val manager = project.getService(MockAgentManager::class.java)
                val agent = manager.getAgent(runConfigId)

                if (agent != null) {
                    // 进程启动后，等待 agent HTTP 服务器就绪，然后更新连接状态
                    Thread {
                        Thread.sleep(2000) // 等待 agent 启动
                        manager.probeAllStatus()
                    }.start()
                }
            }

            override fun processTerminated(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int) {
                val runConfigId = env.runProfile.name
                val manager = project.getService(MockAgentManager::class.java)
                val agent = manager.getAgent(runConfigId)

                if (agent != null) {
                    manager.unregisterAgent(runConfigId)
                    log.info("[MockAgent] Process terminated, unregistered agent: ${agent.name} (exitCode=$exitCode)")
                }
            }
        })
    }

    companion object {
        private val log = Logger.getInstance(AgentProcessListener::class.java)
    }
}
