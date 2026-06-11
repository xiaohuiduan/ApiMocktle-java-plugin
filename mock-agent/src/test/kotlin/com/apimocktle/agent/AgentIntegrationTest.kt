package com.apimocktle.agent

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
 * Mock Agent 集成测试 —— 验证完整的 Agent ↔ ApiMocktle 通信链路
 *
 * 测试流程：
 * 1. 启动 Agent HTTP Server（随机端口）
 * 2. 通过 HTTP 推送 Mock 规则
 * 3. 模拟 Feign/Mapper 方法调用，验证规则匹配
 * 4. 验证调用日志被正确收集
 * 5. 通过 HTTP 拉取日志
 * 6. 验证规则清除
 */
class AgentIntegrationTest {

    private var port: Int = 0
    private val mapper = jacksonObjectMapper()

    @Before
    fun setup() {
        // 找一个可用端口
        port = ServerSocket(0).use { it.localPort }

        // 启动 Agent HTTP Server（不注入真实 Instrumentation）
        MockRuleRegistry.reset()
        CallLogCollector.clear()
        AgentHttpServer.start(port, createDummyInstrumentation())
    }

    @After
    fun teardown() {
        AgentHttpServer.stop()
        MockRuleRegistry.reset()
        CallLogCollector.clear()
    }

    // ==================== 端到端流程测试 ====================

    @Test
    fun `完整流程 - 推送规则 → 模拟调用 → 拉取日志 → 清除规则`() {
        // Step 1: 推送 Mock 规则
        val rules = listOf(
            mapOf(
                "id" to "rule-feign-1",
                "className" to "com.example.feign.OrderClient",
                "methodName" to "createOrder",
                "responseTemplate" to """{"code":200,"data":{"orderId":"MOCK_001"}}""",
            ),
            mapOf(
                "id" to "rule-mapper-1",
                "className" to "com.example.mapper.UserMapper",
                "methodName" to "selectById",
                "paramTypes" to listOf("java.lang.Long"),
                "responseTemplate" to """{"id":1,"name":"测试用户","role":"admin"}""",
            )
        )
        val pushResponse = httpPut("/mock/rules", mapper.writeValueAsString(rules))
        val pushResult: Map<String, Any?> = mapper.readValue(pushResponse)
        assertEquals(true, pushResult["ok"])
        assertEquals(2, pushResult["count"])

        // Step 2: 验证规则已生效（通过 Registry 直接验证）
        val feignRule = MockRuleRegistry.findMatch(
            "com.example.feign.OrderClient", "createOrder", emptyArray()
        )
        assertNotNull("Feign 规则应匹配", feignRule)
        assertEquals("rule-feign-1", feignRule!!.id)

        val mapperRule = MockRuleRegistry.findMatch(
            "com.example.mapper.UserMapper", "selectById", arrayOf(1L)
        )
        assertNotNull("Mapper 规则应匹配", mapperRule)
        assertEquals("rule-mapper-1", mapperRule!!.id)

        // Step 3: 模拟 Feign 拦截调用
        CallLogCollector.record(
            "com.example.feign.OrderClient", "createOrder",
            arrayOf(mapOf("userId" to "1")),
            feignRule, mapOf("code" to 200, "data" to mapOf("orderId" to "MOCK_001")),
            5L
        )

        // Step 4: 模拟 Mapper 拦截调用
        CallLogCollector.record(
            "com.example.mapper.UserMapper", "selectById",
            arrayOf(1L),
            mapperRule, mapOf("id" to 1, "name" to "测试用户"),
            2L
        )

        // Step 5: 通过 HTTP 拉取日志
        val logsResponse = httpGet("/mock/logs")
        val logs: List<Map<String, Any?>> = mapper.readValue(logsResponse)
        assertEquals(2, logs.size)

        // 验证日志内容
        val feignLog = logs.find { it["className"].toString().contains("OrderClient") }
        assertNotNull("应有 Feign 调用日志", feignLog)
        assertEquals("rule-feign-1", feignLog!!["matchedRuleId"])
        assertEquals(5L, (feignLog["durationMs"] as Int).toLong())

        val mapperLog = logs.find { it["className"].toString().contains("UserMapper") }
        assertNotNull("应有 Mapper 调用日志", mapperLog)
        assertEquals("rule-mapper-1", mapperLog!!["matchedRuleId"])

        // Step 6: 清除规则
        val clearResponse = httpDelete("/mock/rules")
        val clearResult: Map<String, Any?> = mapper.readValue(clearResponse)
        assertEquals(true, clearResult["ok"])

        // 验证规则已清除
        assertNull(MockRuleRegistry.findMatch("com.example.feign.OrderClient", "createOrder", emptyArray()))

        // 验证日志也已清空
        val emptyLogs: List<Map<String, Any?>> = mapper.readValue(httpGet("/mock/logs"))
        assertTrue(emptyLogs.isEmpty())
    }

    @Test
    fun `maxTimes 限制 - 超过次数后放行`() {
        val rules = listOf(
            mapOf(
                "id" to "rule-limited",
                "className" to "com.example.A",
                "methodName" to "foo",
                "responseTemplate" to """{"mock":true}""",
                "maxTimes" to 2,
            )
        )
        httpPut("/mock/rules", mapper.writeValueAsString(rules))

        // 前 2 次匹配
        assertNotNull(MockRuleRegistry.findMatch("com.example.A", "foo", emptyArray()))
        assertNotNull(MockRuleRegistry.findMatch("com.example.A", "foo", emptyArray()))

        // 第 3 次应放行
        assertNull("超过 maxTimes 后应返回 null（放行）", MockRuleRegistry.findMatch("com.example.A", "foo", emptyArray()))
    }

    @Test
    fun `未匹配规则时返回 null - 放行真实调用`() {
        httpPut("/mock/rules", mapper.writeValueAsString(emptyList<Map<String, Any?>>()))

        val result = MockRuleRegistry.findMatch("com.example.Unknown", "method", emptyArray())
        assertNull("无匹配规则时应返回 null", result)
    }

    @Test
    fun `Agent 状态检查`() {
        val statusResponse = httpGet("/status")
        val status: Map<String, Any?> = mapper.readValue(statusResponse)
        assertEquals(true, status["connected"])
        assertNotNull(status["pid"])
        assertEquals("1.0.0", status["version"])
    }

    @Test
    fun `参数类型不匹配时放行`() {
        val rules = listOf(
            mapOf(
                "id" to "rule-typed",
                "className" to "com.example.Service",
                "methodName" to "process",
                "paramTypes" to listOf("java.lang.String"),
                "responseTemplate" to """{"ok":true}""",
            )
        )
        httpPut("/mock/rules", mapper.writeValueAsString(rules))

        // 正确类型匹配
        assertNotNull(MockRuleRegistry.findMatch("com.example.Service", "process", arrayOf("hello")))

        // 错误类型放行
        assertNull(MockRuleRegistry.findMatch("com.example.Service", "process", arrayOf(123)))
    }

    @Test
    fun `批量规则推送与日志累积`() {
        val rules = (1..10).map { i ->
            mapOf(
                "id" to "rule-$i",
                "className" to "com.example.Service$i",
                "methodName" to "method$i",
                "responseTemplate" to """{"index":$i}""",
            )
        }
        val pushResponse = httpPut("/mock/rules", mapper.writeValueAsString(rules))
        val result: Map<String, Any?> = mapper.readValue(pushResponse)
        assertEquals(10, result["count"])

        // 模拟 10 次调用
        for (i in 1..10) {
            val rule = MockRuleRegistry.findMatch("com.example.Service$i", "method$i", emptyArray())
            assertNotNull(rule)
            CallLogCollector.record(
                "com.example.Service$i", "method$i", emptyArray(),
                rule!!, mapOf("index" to i), i.toLong()
            )
        }

        val logs: List<Map<String, Any?>> = mapper.readValue(httpGet("/mock/logs"))
        assertEquals(10, logs.size)
    }

    // ==================== HTTP 工具 ====================

    private fun httpGet(path: String): String {
        val conn = URL("http://localhost:$port$path").openConnection() as HttpURLConnection
        conn.connectTimeout = 3000
        conn.readTimeout = 5000
        conn.requestMethod = "GET"
        val response = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        return response
    }

    private fun httpPut(path: String, body: String): String {
        val conn = URL("http://localhost:$port$path").openConnection() as HttpURLConnection
        conn.connectTimeout = 3000
        conn.readTimeout = 5000
        conn.requestMethod = "PUT"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.outputStream.write(body.toByteArray(Charsets.UTF_8))
        val response = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        return response
    }

    private fun httpDelete(path: String): String {
        val conn = URL("http://localhost:$port$path").openConnection() as HttpURLConnection
        conn.connectTimeout = 3000
        conn.readTimeout = 5000
        conn.requestMethod = "DELETE"
        val response = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        return response
    }

    /**
     * 创建一个假的 Instrumentation（仅用于测试 HTTP Server，不涉及字节码操作）
     */
    private fun createDummyInstrumentation(): java.lang.instrument.Instrumentation {
        return org.mockito.Mockito.mock(java.lang.instrument.Instrumentation::class.java)
    }
}
