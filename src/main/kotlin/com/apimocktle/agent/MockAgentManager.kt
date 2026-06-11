package com.apimocktle.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.net.HttpURLConnection
import java.net.URL

/**
 * Mock Agent 管理服务 —— 负责通过 HTTP 与 Agent 通信
 *
 * 功能：
 * 1. 通过 HTTP 与 Agent 通信（推送规则、获取日志、发现类）
 * 2. 管理 Agent 连接状态
 *
 * Agent 注入由 [MockAgentRunConfigurationExtension] 通过 -javaagent 方式完成。
 */
@Service(Service.Level.PROJECT)
class MockAgentManager(private val project: Project) {

    companion object {
        private const val DEFAULT_PORT = 19876
        private const val CONNECT_TIMEOUT_MS = 3000
        private const val READ_TIMEOUT_MS = 10000
        private val log = Logger.getInstance(MockAgentManager::class.java)
    }

    private val mapper: ObjectMapper = jacksonObjectMapper()
    var agentPort: Int = DEFAULT_PORT
        private set

    /**
     * 推送 Mock 规则到 Agent
     */
    fun pushRules(rules: List<Map<String, Any?>>): Boolean {
        return try {
            val json = mapper.writeValueAsString(rules)
            httpPut("/mock/rules", json)
            true
        } catch (e: Exception) {
            log.error("[MockAgent] Failed to push rules: ${e.message}")
            false
        }
    }

    /**
     * 清除 Agent 上的 Mock 规则
     */
    fun clearRules(): Boolean {
        return try {
            httpDelete("/mock/rules")
            true
        } catch (e: Exception) {
            log.error("[MockAgent] Failed to clear rules: ${e.message}")
            false
        }
    }

    /**
     * 获取 Mock 调用日志
     */
    fun getCallLogs(): List<Map<String, Any?>> {
        return try {
            val response = httpGet("/mock/logs")
            mapper.readValue(response)
        } catch (e: Exception) {
            log.error("[MockAgent] Failed to get logs: ${e.message}")
            emptyList()
        }
    }

    /**
     * 发现可拦截的类
     */
    fun discover(): Map<String, Any?> {
        return try {
            val response = httpGet("/discover")
            mapper.readValue(response)
        } catch (e: Exception) {
            log.error("[MockAgent] Failed to discover: ${e.message}")
            emptyMap()
        }
    }

    /**
     * 检查 Agent 是否可达
     */
    fun isConnected(): Boolean {
        return try {
            val url = URL("http://localhost:$agentPort/status")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = CONNECT_TIMEOUT_MS
            conn.requestMethod = "GET"
            val connected = conn.responseCode == 200
            conn.disconnect()
            connected
        } catch (_: Exception) {
            false
        }
    }

    // ==================== HTTP 工具 ====================

    private fun httpGet(path: String): String {
        val url = URL("http://localhost:$agentPort$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.requestMethod = "GET"
        val response = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        return response
    }

    private fun httpPut(path: String, body: String): String {
        val url = URL("http://localhost:$agentPort$path")
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

    private fun httpDelete(path: String): String {
        val url = URL("http://localhost:$agentPort$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.requestMethod = "DELETE"
        val response = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        return response
    }
}
