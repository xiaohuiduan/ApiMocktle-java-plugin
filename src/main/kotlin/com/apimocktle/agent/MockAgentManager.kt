package com.apimocktle.agent

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Mock Agent 管理服务 —— 负责管理多个 Agent 实例
 *
 * 功能：
 * 1. 管理多个 Agent 实例的注册/注销
 * 2. 端口自动分配（从 DEFAULT_PORT 开始递增）
 * 3. 通过 HTTP 与各 Agent 通信（推送规则、获取日志、发现类）
 * 4. 探测各 Agent 的连接状态
 * 5. 激活/取消激活 Agent
 *
 * Agent 注入由 [MockAgentRunConfigurationExtension] 通过 -javaagent 方式完成。
 */
@Service(Service.Level.PROJECT)
class MockAgentManager(private val project: Project) {

    companion object {
        private const val DEFAULT_PORT = 19876
        private const val MAX_PORT = 19976
        private const val CONNECT_TIMEOUT_MS = 3000
        private const val READ_TIMEOUT_MS = 10000
        private val log = Logger.getInstance(MockAgentManager::class.java)
    }

    private val gson: Gson = Gson()

    /** 所有已注册的 Agent，key = Run Configuration ID */
    private val agents = ConcurrentHashMap<String, AgentInfo>()

    /** 监听器，Agent 列表变化时通知 UI 刷新 */
    private val listeners = mutableListOf<() -> Unit>()

    // ==================== Agent 注册/注销 ====================

    /**
     * 注册一个 Agent 实例。
     * @param runConfigId  Run Configuration 的唯一 ID
     * @param name         Run Configuration 名称（作为服务名展示）
     * @param port         agent 监听端口（0 表示自动分配）
     * @return 实际使用的端口
     */
    fun registerAgent(runConfigId: String, name: String, port: Int = 0): Int {
        val actualPort = if (port > 0) port else allocatePort()
        val info = AgentInfo(name = name, port = actualPort, active = true, connected = false, runConfigId = runConfigId)
        agents[runConfigId] = info
        log.info("[MockAgent] Registered agent: $name (port=$actualPort, id=$runConfigId)")
        notifyListeners()
        return actualPort
    }

    /**
     * 注销一个 Agent 实例。
     */
    fun unregisterAgent(runConfigId: String) {
        val removed = agents.remove(runConfigId)
        if (removed != null) {
            log.info("[MockAgent] Unregistered agent: ${removed.name} (id=$runConfigId)")
            notifyListeners()
        }
    }

    /**
     * 获取所有已注册的 Agent 信息（副本）。
     */
    fun getAgents(): List<AgentInfo> = agents.values.toList()

    /**
     * 根据 Run Configuration ID 获取 Agent 信息。
     */
    fun getAgent(runConfigId: String): AgentInfo? = agents[runConfigId]

    // ==================== 激活/取消激活 ====================

    /**
     * 设置 Agent 的激活状态。
     * - 取消激活：调用 agent 的 /agent/mute（清空规则 + 拒绝新规则）
     * - 激活：调用 agent 的 /agent/unmute（恢复接受规则）
     */
    fun setActive(runConfigId: String, active: Boolean) {
        val info = agents[runConfigId] ?: return
        if (active) {
            sendUnmute(info.port)
        } else {
            sendMute(info.port)
        }
        agents[runConfigId] = info.copy(active = active)
        log.info("[MockAgent] Agent ${info.name} active=$active")
        notifyListeners()
    }

    // ==================== 状态探测 ====================

    /**
     * 探测所有已注册 Agent 的连接状态，更新 connected 字段。
     */
    fun probeAllStatus() {
        for ((id, info) in agents) {
            val connected = probeStatus(info.port)
            if (info.connected != connected) {
                agents[id] = info.copy(connected = connected)
            }
        }
        notifyListeners()
    }

    /**
     * 探测指定端口的 Agent 是否可达。
     */
    fun probeStatus(port: Int): Boolean {
        return try {
            val url = URL("http://localhost:$port/status")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = CONNECT_TIMEOUT_MS
            conn.requestMethod = "GET"
            val ok = conn.responseCode == 200
            conn.disconnect()
            ok
        } catch (_: Exception) {
            false
        }
    }

    // ==================== 端口分配 ====================

    /**
     * 从 DEFAULT_PORT 开始递增查找一个空闲端口。
     * 空闲 = 未被其他已注册 Agent 占用 + 未被系统占用。
     */
    fun allocatePort(): Int {
        val usedPorts = agents.values.map { it.port }.toSet()
        for (port in DEFAULT_PORT..MAX_PORT) {
            if (port in usedPorts) continue
            if (isPortAvailable(port)) return port
        }
        log.warn("[MockAgent] No available port in range $DEFAULT_PORT-$MAX_PORT")
        return DEFAULT_PORT
    }

    /**
     * 检查端口是否被其他已注册 Agent 占用。
     */
    fun isPortOccupiedByAgent(port: Int): Boolean {
        return agents.values.any { it.port == port }
    }

    // ==================== HTTP 通信（按端口） ====================

    /**
     * 推送 Mock 规则到指定端口的 Agent。
     */
    fun pushRules(port: Int, rules: List<Map<String, Any?>>): Boolean {
        return try {
            val json = gson.toJson(rules)
            httpPut(port, "/mock/rules", json)
            true
        } catch (e: Exception) {
            log.error("[MockAgent] Failed to push rules to port $port: ${e.message}")
            false
        }
    }

    /**
     * 清除指定端口 Agent 上的 Mock 规则。
     */
    fun clearRules(port: Int): Boolean {
        return try {
            httpDelete(port, "/mock/rules")
            true
        } catch (e: Exception) {
            log.error("[MockAgent] Failed to clear rules on port $port: ${e.message}")
            false
        }
    }

    /**
     * 获取指定端口 Agent 的 Mock 调用日志。
     */
    fun getCallLogs(port: Int): List<Map<String, Any?>> {
        return try {
            val response = httpGet(port, "/mock/logs")
            val type = object : TypeToken<List<Map<String, Any?>>>() {}.type
            gson.fromJson(response, type)
        } catch (e: Exception) {
            log.error("[MockAgent] Failed to get logs from port $port: ${e.message}")
            emptyList()
        }
    }

    /**
     * 发现指定端口 Agent 可拦截的类。
     */
    fun discover(port: Int): Map<String, Any?> {
        return try {
            val response = httpGet(port, "/discover")
            val type = object : TypeToken<Map<String, Any?>>() {}.type
            gson.fromJson(response, type)
        } catch (e: Exception) {
            log.error("[MockAgent] Failed to discover on port $port: ${e.message}")
            emptyMap()
        }
    }

    /**
     * 获取指定端口 Agent 的运行日志。
     * @param port agent 端口
     * @param n 获取最近 n 条，null 表示全部
     */
    fun getAgentLogs(port: Int, n: Int? = null): List<Map<String, Any?>> {
        return try {
            val query = if (n != null) "?n=$n" else ""
            val response = httpGet(port, "/logs$query")
            val type = object : TypeToken<List<Map<String, Any?>>>() {}.type
            gson.fromJson(response, type)
        } catch (e: Exception) {
            log.error("[MockAgent] Failed to get agent logs from port $port: ${e.message}")
            emptyList()
        }
    }

    // ==================== 监听器 ====================

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        for (listener in listeners) {
            try {
                listener()
            } catch (e: Exception) {
                log.warn("[MockAgent] Listener error: ${e.message}")
            }
        }
    }

    // ==================== 内部工具 ====================

    private fun sendMute(port: Int) {
        try {
            httpPost(port, "/agent/mute", "")
        } catch (_: Exception) {
            // 静默失败，agent 可能已断开
        }
    }

    private fun sendUnmute(port: Int) {
        try {
            httpPost(port, "/agent/unmute", "")
        } catch (_: Exception) {
            // 静默失败，agent 可能已断开
        }
    }

    private fun isPortAvailable(port: Int): Boolean {
        return try {
            ServerSocket(port).close()
            true
        } catch (_: Exception) {
            false
        }
    }

    // ==================== HTTP 工具 ====================

    private fun httpGet(port: Int, path: String): String {
        val url = URL("http://localhost:$port$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.requestMethod = "GET"
        val response = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        return response
    }

    private fun httpPut(port: Int, path: String, body: String): String {
        val url = URL("http://localhost:$port$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.requestMethod = "PUT"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.outputStream.write(body.toByteArray(Charsets.UTF_8))
        val response = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        return response
    }

    private fun httpDelete(port: Int, path: String): String {
        val url = URL("http://localhost:$port$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.requestMethod = "DELETE"
        val response = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        return response
    }

    private fun httpPost(port: Int, path: String, body: String): String {
        val url = URL("http://localhost:$port$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        if (body.isNotEmpty()) {
            conn.outputStream.write(body.toByteArray(Charsets.UTF_8))
        }
        val response = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        return response
    }
}
