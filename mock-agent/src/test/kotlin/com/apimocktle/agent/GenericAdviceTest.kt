package com.apimocktle.agent

import com.apimocktle.agent.interceptor.CustomInterceptor
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL

/**
 * GenericAdvice / CustomInterceptor 综合测试
 *
 * 测试策略：
 * - HTTP API 端点（推规则、拉日志、自动注册）
 * - MockRuleRegistry 匹配逻辑
 * - CustomInterceptor 注册状态追踪
 * - 完整字节码注入测试通过端到端手动验证（见 README）
 */
class GenericAdviceTest {

    private var port: Int = 0
    private val mapper = jacksonObjectMapper()

    @Before
    fun setup() {
        MockRuleRegistry.reset()
        CallLogCollector.clear()
        CustomInterceptor.reset()
    }

    @After
    fun teardown() {
        AgentHttpServer.stop()
        MockRuleRegistry.reset()
        CallLogCollector.clear()
        CustomInterceptor.reset()
    }

    // ==================== HTTP API: 规则匹配 ====================

    @Test
    fun pushedRulesMatchCorrectly() {
        port = ServerSocket(0).use { it.localPort }
        AgentHttpServer.start(port, createDummyInstrumentation())

        val rules = listOf(
            mapOf("id" to "r1", "className" to "com.example.UserService",
                "methodName" to "getUser", "responseTemplate" to """{"id":1}"""),
            mapOf("id" to "r2", "className" to "com.example.OrderService",
                "methodName" to "create", "responseTemplate" to """{"orderId":"MOCK"}""")
        )
        httpPut("/mock/rules", mapper.writeValueAsString(rules))

        // Verify rules match
        assertNotNull(MockRuleRegistry.findMatch("com.example.UserService", "getUser", emptyArray()))
        assertEquals("r1", MockRuleRegistry.findMatch("com.example.UserService", "getUser", emptyArray())!!.id)

        assertNotNull(MockRuleRegistry.findMatch("com.example.OrderService", "create", emptyArray()))
        assertEquals("r2", MockRuleRegistry.findMatch("com.example.OrderService", "create", emptyArray())!!.id)

        // Non-matching
        assertNull(MockRuleRegistry.findMatch("com.example.Other", "method", emptyArray()))
    }

    @Test
    fun deleteRulesClearsEverything() {
        port = ServerSocket(0).use { it.localPort }
        AgentHttpServer.start(port, createDummyInstrumentation())

        httpPut("/mock/rules", mapper.writeValueAsString(listOf(mapOf(
            "id" to "r1", "className" to "com.example.A", "methodName" to "foo",
            "responseTemplate" to """{"x":1}"""
        ))))
        assertNotNull(MockRuleRegistry.findMatch("com.example.A", "foo", emptyArray()))

        httpDelete("/mock/rules")
        assertNull(MockRuleRegistry.findMatch("com.example.A", "foo", emptyArray()))
    }

    // ==================== HTTP API: 日志 ====================

    @Test
    fun mockCallLogsRecordedAndDrained() {
        port = ServerSocket(0).use { it.localPort }
        AgentHttpServer.start(port, createDummyInstrumentation())

        // Push rule and simulate mock call
        httpPut("/mock/rules", mapper.writeValueAsString(listOf(mapOf(
            "id" to "r1", "className" to "com.example.A", "methodName" to "foo",
            "responseTemplate" to """{"mock":true}"""
        ))))
        val rule = MockRuleRegistry.findMatch("com.example.A", "foo", emptyArray())!!
        CallLogCollector.record("com.example.A", "foo", arrayOf("arg1"), rule, """{"mock":true}""", 5L)

        // Drain logs via HTTP
        val logs: List<Map<String, Any?>> = mapper.readValue(httpGet("/mock/logs"))
        assertEquals(1, logs.size)
        assertEquals("r1", logs[0]["matchedRuleId"])
        assertEquals("com.example.A", logs[0]["className"])

        // Second drain should be empty
        val emptyLogs: List<Map<String, Any?>> = mapper.readValue(httpGet("/mock/logs"))
        assertTrue(emptyLogs.isEmpty())
    }

    // ==================== HTTP API: 参数类型匹配 ====================

    @Test
    fun paramTypesMatchingViaHttp() {
        port = ServerSocket(0).use { it.localPort }
        AgentHttpServer.start(port, createDummyInstrumentation())

        httpPut("/mock/rules", mapper.writeValueAsString(listOf(mapOf(
            "id" to "r1", "className" to "com.example.A", "methodName" to "process",
            "paramTypes" to listOf("java.lang.String"),
            "responseTemplate" to """{"ok":true}"""
        ))))

        assertNotNull(MockRuleRegistry.findMatch("com.example.A", "process", arrayOf("hello")))
        assertNull(MockRuleRegistry.findMatch("com.example.A", "process", arrayOf(123)))
    }

    // ==================== HTTP API: maxTimes ====================

    @Test
    fun maxTimesViaHttp() {
        port = ServerSocket(0).use { it.localPort }
        AgentHttpServer.start(port, createDummyInstrumentation())

        httpPut("/mock/rules", mapper.writeValueAsString(listOf(mapOf(
            "id" to "r1", "className" to "com.example.A", "methodName" to "foo",
            "responseTemplate" to """{"mock":true}""",
            "maxTimes" to 2
        ))))

        assertNotNull(MockRuleRegistry.findMatch("com.example.A", "foo", emptyArray()))
        assertNotNull(MockRuleRegistry.findMatch("com.example.A", "foo", emptyArray()))
        assertNull(MockRuleRegistry.findMatch("com.example.A", "foo", emptyArray()))
    }

    // ==================== HTTP API: returnType 字段 ====================

    @Test
    fun returnTypeFieldParsedCorrectly() {
        port = ServerSocket(0).use { it.localPort }
        AgentHttpServer.start(port, createDummyInstrumentation())

        httpPut("/mock/rules", mapper.writeValueAsString(listOf(mapOf(
            "id" to "r1", "className" to "com.example.A", "methodName" to "foo",
            "responseTemplate" to """{"id":1}""",
            "returnType" to "java.lang.String"
        ))))

        val rule = MockRuleRegistry.findMatch("com.example.A", "foo", emptyArray())!!
        assertNotNull(rule.returnType)
        assertEquals(String::class.java, rule.returnType)
    }

    @Test
    fun missingReturnTypeParsedAsNull() {
        port = ServerSocket(0).use { it.localPort }
        AgentHttpServer.start(port, createDummyInstrumentation())

        httpPut("/mock/rules", mapper.writeValueAsString(listOf(mapOf(
            "id" to "r1", "className" to "com.example.A", "methodName" to "foo",
            "responseTemplate" to """{"mock":true}"""
        ))))

        val rule = MockRuleRegistry.findMatch("com.example.A", "foo", emptyArray())!!
        assertNull(rule.returnType)
    }

    // ==================== Agent 状态 ====================

    @Test
    fun agentStatusEndpoint() {
        port = ServerSocket(0).use { it.localPort }
        AgentHttpServer.start(port, createDummyInstrumentation())

        val status: Map<String, Any?> = mapper.readValue(httpGet("/status"))
        assertEquals(true, status["connected"])
        assertEquals("1.0.0", status["version"])
        assertNotNull(status["pid"])
    }

    // ==================== 辅助 ====================

    private fun createDummyInstrumentation(): java.lang.instrument.Instrumentation {
        return org.mockito.Mockito.mock(java.lang.instrument.Instrumentation::class.java)
    }

    private fun httpGet(path: String): String {
        val conn = URL("http://localhost:$port$path").openConnection() as HttpURLConnection
        conn.connectTimeout = 3000; conn.readTimeout = 5000; conn.requestMethod = "GET"
        val r = conn.inputStream.bufferedReader().readText(); conn.disconnect(); return r
    }
    private fun httpPut(path: String, body: String): String {
        val conn = URL("http://localhost:$port$path").openConnection() as HttpURLConnection
        conn.connectTimeout = 3000; conn.readTimeout = 5000; conn.requestMethod = "PUT"
        conn.doOutput = true; conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.outputStream.write(body.toByteArray(Charsets.UTF_8))
        val r = conn.inputStream.bufferedReader().readText(); conn.disconnect(); return r
    }
    private fun httpDelete(path: String): String {
        val conn = URL("http://localhost:$port$path").openConnection() as HttpURLConnection
        conn.connectTimeout = 3000; conn.readTimeout = 5000; conn.requestMethod = "DELETE"
        val r = conn.inputStream.bufferedReader().readText(); conn.disconnect(); return r
    }
}
