package com.apimocktle.agent

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Agent 运行日志收集器。
 * 保留最近 MAX_LOGS 条日志，供插件通过 GET /logs 拉取。
 */
object AgentLogCollector {

    private const val MAX_LOGS = 500
    private val logs = ConcurrentLinkedDeque<AgentLogEntry>()
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun info(message: String) {
        add("INFO", message)
    }

    fun warn(message: String) {
        add("WARN", message)
    }

    fun error(message: String) {
        add("ERROR", message)
    }

    fun error(message: String, throwable: Throwable) {
        add("ERROR", "$message: ${throwable.message}")
    }

    private fun add(level: String, message: String) {
        val entry = AgentLogEntry(
            timestamp = LocalDateTime.now().format(timeFormatter),
            level = level,
            message = message
        )
        logs.addLast(entry)
        // 超过上限时移除最旧的
        while (logs.size > MAX_LOGS) {
            logs.pollFirst()
        }
        // 同时输出到 stderr，方便调试
        System.err.println("[MockAgent] [$level] $message")
    }

    /**
     * 返回所有日志（不消费，仅读取）。
     */
    fun getAll(): List<AgentLogEntry> {
        return logs.toList()
    }

    /**
     * 返回最近 n 条日志。
     */
    fun getRecent(n: Int = 100): List<AgentLogEntry> {
        val all = logs.toList()
        return if (all.size <= n) all else all.takeLast(n)
    }

    fun clear() {
        logs.clear()
    }
}

data class AgentLogEntry(
    val timestamp: String,
    val level: String,
    val message: String,
)
