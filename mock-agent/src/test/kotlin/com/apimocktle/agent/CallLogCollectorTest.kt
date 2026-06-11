package com.apimocktle.agent

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * CallLogCollector 单元测试
 */
class CallLogCollectorTest {

    @Before
    fun setup() {
        CallLogCollector.reset()
    }

    @After
    fun teardown() {
        CallLogCollector.reset()
    }

    @Test
    fun `record - 记录一条日志`() {
        val rule = makeRule()
        CallLogCollector.record("com.example.A", "foo", arrayOf("arg1"), rule, mapOf("ok" to true), 5)

        val logs = CallLogCollector.peekAll()
        assertEquals(1, logs.size)
        assertEquals("com.example.A", logs[0].className)
        assertEquals("foo", logs[0].methodName)
        assertEquals(listOf("arg1"), logs[0].args)
        assertEquals("test-rule", logs[0].matchedRuleId)
        assertEquals(5, logs[0].durationMs)
    }

    @Test
    fun `record - 多条日志累积`() {
        val rule = makeRule()
        repeat(10) { i ->
            CallLogCollector.record("com.example.A", "foo", arrayOf(i), rule, null, i.toLong())
        }
        assertEquals(10, CallLogCollector.peekAll().size)
    }

    @Test
    fun `drainAll - 取出并清空`() {
        val rule = makeRule()
        CallLogCollector.record("com.example.A", "foo", emptyArray(), rule, "resp", 1)
        CallLogCollector.record("com.example.B", "bar", emptyArray(), rule, "resp", 2)

        val drained = CallLogCollector.drainAll()
        assertEquals(2, drained.size)
        assertTrue(CallLogCollector.peekAll().isEmpty())
    }

    @Test
    fun `drainAll - 空队列返回空列表`() {
        val drained = CallLogCollector.drainAll()
        assertTrue(drained.isEmpty())
    }

    @Test
    fun `clear - 清空日志`() {
        val rule = makeRule()
        CallLogCollector.record("com.example.A", "foo", emptyArray(), rule, null, 0)
        CallLogCollector.clear()
        assertTrue(CallLogCollector.peekAll().isEmpty())
    }

    @Test
    fun `record - args 中包含 null`() {
        val rule = makeRule()
        CallLogCollector.record("com.example.A", "foo", arrayOf(null, "valid"), rule, null, 0)

        val logs = CallLogCollector.peekAll()
        assertEquals(2, logs[0].args.size)
        assertNull(logs[0].args[0])
        assertEquals("valid", logs[0].args[1])
    }

    private fun makeRule() = MockRule(
        id = "test-rule",
        className = "com.example.A",
        methodName = "foo",
        responseTemplate = "{}",
    )
}
