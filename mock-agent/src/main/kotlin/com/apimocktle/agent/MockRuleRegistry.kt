package com.apimocktle.agent

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Mock 规则注册表 —— 管理规则的注册、匹配、次数追踪
 *
 * 线程安全：规则列表用 CopyOnWriteArrayList，计数用 ConcurrentHashMap + AtomicInteger
 */
object MockRuleRegistry {

    private val rules = CopyOnWriteArrayList<MockRule>()
    private val callCounts = ConcurrentHashMap<String, AtomicInteger>()

    /**
     * 更新全部规则（原子替换）
     */
    fun update(newRules: List<MockRule>) {
        rules.clear()
        rules.addAll(newRules)
        callCounts.clear()
        newRules.forEach { rule ->
            if (rule.maxTimes != null) {
                callCounts[rule.id] = AtomicInteger(0)
            }
        }
    }

    /**
     * 清除全部规则
     */
    fun clear() {
        rules.clear()
        callCounts.clear()
    }

    /**
     * 获取当前规则列表（快照）
     */
    fun getAll(): List<MockRule> = rules.toList()

    /**
     * 查找匹配的规则
     *
     * @param className  全限定类名
     * @param methodName 方法名
     * @param args       实际调用参数（用于 matchExpression 匹配）
     * @return 匹配的规则，null 表示不匹配（应放行真实调用）
     */
    fun findMatch(className: String, methodName: String, args: Array<Any?>): MockRule? {
        for (rule in rules) {
            // 类名 + 方法名匹配
            if (rule.className != className || rule.methodName != methodName) continue

            // 参数类型匹配（如果指定了）
            if (rule.paramTypes != null && rule.paramTypes.isNotEmpty()) {
                if (!matchParamTypes(rule.paramTypes, args)) continue
            }

            // 次数限制检查
            if (rule.maxTimes != null) {
                val counter = callCounts[rule.id] ?: continue
                val count = counter.get()
                if (count >= rule.maxTimes) continue
                counter.incrementAndGet()
            }

            return rule
        }
        return null
    }

    /**
     * 匹配参数类型列表
     * 规则中的 paramTypes 是全限定类名，与实际参数的运行时类型比较
     */
    private fun matchParamTypes(expectedTypes: List<String>, args: Array<Any?>): Boolean {
        if (expectedTypes.size != args.size) return false
        for (i in expectedTypes.indices) {
            val actual = args[i]
            if (actual == null) continue // null 匹配任何类型
            val actualFqn = actual.javaClass.name
            // 精确匹配 或 实际类型是期望类型的子类
            if (actualFqn != expectedTypes[i]) {
                try {
                    val expectedClass = Class.forName(expectedTypes[i])
                    if (!expectedClass.isInstance(actual)) return false
                } catch (_: ClassNotFoundException) {
                    return false
                }
            }
        }
        return true
    }

    // 用于测试
    internal fun reset() {
        rules.clear()
        callCounts.clear()
    }
}
