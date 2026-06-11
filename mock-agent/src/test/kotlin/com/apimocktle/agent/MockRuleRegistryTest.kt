package com.apimocktle.agent

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * MockRuleRegistry 单元测试 —— 核心规则匹配逻辑
 */
class MockRuleRegistryTest {

    @Before
    fun setup() {
        MockRuleRegistry.reset()
    }

    @After
    fun teardown() {
        MockRuleRegistry.reset()
    }

    // ---- 基本匹配 ----

    @Test
    fun `findMatch - 类名和方法名完全匹配`() {
        val rule = makeRule("com.example.OrderClient", "createOrder")
        MockRuleRegistry.update(listOf(rule))

        val result = MockRuleRegistry.findMatch("com.example.OrderClient", "createOrder", emptyArray())
        assertNotNull(result)
        assertEquals(rule.id, result!!.id)
    }

    @Test
    fun `findMatch - 类名不匹配返回 null`() {
        MockRuleRegistry.update(listOf(makeRule("com.example.OrderClient", "createOrder")))

        val result = MockRuleRegistry.findMatch("com.example.OtherClient", "createOrder", emptyArray())
        assertNull(result)
    }

    @Test
    fun `findMatch - 方法名不匹配返回 null`() {
        MockRuleRegistry.update(listOf(makeRule("com.example.OrderClient", "createOrder")))

        val result = MockRuleRegistry.findMatch("com.example.OrderClient", "cancelOrder", emptyArray())
        assertNull(result)
    }

    @Test
    fun `findMatch - 无规则返回 null`() {
        val result = MockRuleRegistry.findMatch("com.example.A", "foo", emptyArray())
        assertNull(result)
    }

    // ---- 参数类型匹配 ----

    @Test
    fun `findMatch - paramTypes 匹配成功`() {
        val rule = makeRule("com.example.A", "foo", paramTypes = listOf("java.lang.String"))
        MockRuleRegistry.update(listOf(rule))

        val result = MockRuleRegistry.findMatch("com.example.A", "foo", arrayOf("hello"))
        assertNotNull(result)
    }

    @Test
    fun `findMatch - paramTypes 数量不匹配返回 null`() {
        val rule = makeRule("com.example.A", "foo", paramTypes = listOf("java.lang.String", "java.lang.Integer"))
        MockRuleRegistry.update(listOf(rule))

        val result = MockRuleRegistry.findMatch("com.example.A", "foo", arrayOf("hello"))
        assertNull(result)
    }

    @Test
    fun `findMatch - null 参数匹配任何类型`() {
        val rule = makeRule("com.example.A", "foo", paramTypes = listOf("java.lang.String"))
        MockRuleRegistry.update(listOf(rule))

        val result = MockRuleRegistry.findMatch("com.example.A", "foo", arrayOf(null))
        assertNotNull(result)
    }

    // ---- maxTimes 限制 ----

    @Test
    fun `findMatch - maxTimes 限制生效`() {
        val rule = makeRule("com.example.A", "foo", maxTimes = 2)
        MockRuleRegistry.update(listOf(rule))

        // 前 2 次匹配
        assertNotNull(MockRuleRegistry.findMatch("com.example.A", "foo", emptyArray()))
        assertNotNull(MockRuleRegistry.findMatch("com.example.A", "foo", emptyArray()))
        // 第 3 次超限
        assertNull(MockRuleRegistry.findMatch("com.example.A", "foo", emptyArray()))
    }

    @Test
    fun `findMatch - maxTimes 为 null 时无限匹配`() {
        val rule = makeRule("com.example.A", "foo", maxTimes = null)
        MockRuleRegistry.update(listOf(rule))

        repeat(100) {
            assertNotNull(MockRuleRegistry.findMatch("com.example.A", "foo", emptyArray()))
        }
    }

    // ---- 多规则优先级 ----

    @Test
    fun `findMatch - 多规则时返回第一个匹配`() {
        val rule1 = makeRule("com.example.A", "foo", id = "r1")
        val rule2 = makeRule("com.example.A", "foo", id = "r2")
        MockRuleRegistry.update(listOf(rule1, rule2))

        val result = MockRuleRegistry.findMatch("com.example.A", "foo", emptyArray())
        assertEquals("r1", result!!.id)
    }

    // ---- update / clear ----

    @Test
    fun `update - 替换全部规则`() {
        MockRuleRegistry.update(listOf(makeRule("com.example.A", "foo")))
        assertEquals(1, MockRuleRegistry.getAll().size)

        MockRuleRegistry.update(listOf(makeRule("com.example.B", "bar"), makeRule("com.example.C", "baz")))
        assertEquals(2, MockRuleRegistry.getAll().size)
    }

    @Test
    fun `clear - 清除全部规则`() {
        MockRuleRegistry.update(listOf(makeRule("com.example.A", "foo")))
        MockRuleRegistry.clear()
        assertTrue(MockRuleRegistry.getAll().isEmpty())
    }

    @Test
    fun `update - 重置 maxTimes 计数`() {
        val rule = makeRule("com.example.A", "foo", maxTimes = 1)
        MockRuleRegistry.update(listOf(rule))
        assertNotNull(MockRuleRegistry.findMatch("com.example.A", "foo", emptyArray()))
        assertNull(MockRuleRegistry.findMatch("com.example.A", "foo", emptyArray()))

        // 重新 update 后计数重置
        MockRuleRegistry.update(listOf(rule))
        assertNotNull(MockRuleRegistry.findMatch("com.example.A", "foo", emptyArray()))
    }

    // ---- 辅助 ----

    private fun makeRule(
        className: String,
        methodName: String,
        id: String = "test-rule",
        paramTypes: List<String>? = null,
        maxTimes: Int? = null,
    ) = MockRule(
        id = id,
        className = className,
        methodName = methodName,
        paramTypes = paramTypes,
        responseTemplate = """{"code":200}""",
        responseDelay = null,
        maxTimes = maxTimes,
    )
}
