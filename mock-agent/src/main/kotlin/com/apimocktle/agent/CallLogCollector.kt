package com.apimocktle.agent

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * 调用日志收集器 —— 记录所有被 Mock 拦截的调用
 *
 * 线程安全：ConcurrentLinkedQueue 无锁写入，AtomicLong 自增 ID
 */
object CallLogCollector {

    private val logs = ConcurrentLinkedQueue<MockCallLog>()

    /**
     * 记录一次 Mock 调用
     */
    fun record(className: String, methodName: String, args: Array<Any?>, rule: MockRule, response: Any?, durationMs: Long) {
        val log = MockCallLog(
            className = className,
            methodName = methodName,
            args = sanitizeArgs(args),
            response = response,
            matchedRuleId = rule.id,
            timestamp = System.currentTimeMillis(),
            durationMs = durationMs,
        )
        logs.add(log)
        System.err.println("[MockAgent-Log] recorded: ${log.className}#${log.methodName}, queueSize=${logs.size}")
    }

    /**
     * 将 args 转为可 JSON 序列化的格式
     * Method / Constructor 等反射对象转为 toString，简单类型保留原值
     */
    private fun sanitizeArgs(args: Array<Any?>): List<Any?> {
        return args.map { arg ->
            when (arg) {
                null -> null
                is java.lang.reflect.Method -> arg.toString()
                is java.lang.reflect.Constructor<*> -> arg.toString()
                is Class<*> -> arg.name
                else -> {
                    // 尝试保留简单可序列化类型
                    val name = arg.javaClass.name
                    if (name.startsWith("java.lang.") || name.startsWith("java.math.") || name.startsWith("java.util.")) {
                        arg
                    } else {
                        arg.toString()
                    }
                }
            }
        }
    }

    /**
     * 获取全部日志并清空（原子操作）
     */
    fun drainAll(): List<MockCallLog> {
        val result = mutableListOf<MockCallLog>()
        while (true) {
            val log = logs.poll() ?: break
            result.add(log)
        }
        System.err.println("[MockAgent-Log] drainAll: returned ${result.size} logs")
        return result
    }

    /**
     * 获取全部日志（不清空）
     */
    fun peekAll(): List<MockCallLog> = logs.toList()

    /**
     * 清空日志
     */
    fun clear() {
        logs.clear()
    }

    // 用于测试
    internal fun reset() {
        logs.clear()
    }
}
