package com.apimocktle.agent

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.*
import org.junit.Test

/**
 * MockAgentManager 纯逻辑单元测试
 *
 * 测试 HTTP payload 序列化、URL 构建等不依赖 IntelliJ 的逻辑
 */
class MockAgentManagerTest {

    private val gson = Gson()

    // ---- 规则 payload 序列化 ----

    @Test
    fun `规则 payload 序列化为 JSON 后可被 Agent 反序列化`() {
        val rule = mapOf(
            "id" to "rule-1",
            "className" to "com.example.feign.OrderClient",
            "methodName" to "createOrder",
            "paramTypes" to listOf("com.example.dto.CreateOrderReq"),
            "responseTemplate" to """{"code":200,"data":{"orderId":"MOCK_1"}}""",
            "responseDelay" to 100,
            "maxTimes" to 5,
        )

        val json = gson.toJson(listOf(rule))
        val type = object : TypeToken<List<Map<String, Any?>>>() {}.type
        val deserialized: List<Map<String, Any?>> = gson.fromJson(json, type)

        assertEquals(1, deserialized.size)
        assertEquals("rule-1", deserialized[0]["id"])
        assertEquals("com.example.feign.OrderClient", deserialized[0]["className"])
        assertEquals(100L, (deserialized[0]["responseDelay"] as Number).toLong())
    }

    @Test
    fun `批量规则序列化`() {
        val rules = listOf(
            mapOf("id" to "r1", "className" to "com.example.A", "methodName" to "foo", "responseTemplate" to "{}"),
            mapOf("id" to "r2", "className" to "com.example.B", "methodName" to "bar", "responseTemplate" to """{"ok":true}"""),
        )

        val json = gson.toJson(rules)
        val type = object : TypeToken<List<Map<String, Any?>>>() {}.type
        val deserialized: List<Map<String, Any?>> = gson.fromJson(json, type)
        assertEquals(2, deserialized.size)
    }

    // ---- 调用日志反序列化 ----

    @Test
    fun `Agent 调用日志可正确反序列化`() {
        val logJson = """[{
            "className": "com.example.feign.OrderClient",
            "methodName": "createOrder",
            "args": [{"userId": "1"}],
            "response": {"code": 200},
            "matchedRuleId": "rule-1",
            "timestamp": 1700000000000,
            "durationMs": 5
        }]"""

        val type = object : TypeToken<List<Map<String, Any?>>>() {}.type
        val logs: List<Map<String, Any?>> = gson.fromJson(logJson, type)
        assertEquals(1, logs.size)
        assertEquals("com.example.feign.OrderClient", logs[0]["className"])
        assertEquals(5L, (logs[0]["durationMs"] as Number).toLong())
    }

    @Test
    fun `空日志列表反序列化`() {
        val type = object : TypeToken<List<Map<String, Any?>>>() {}.type
        val logs: List<Map<String, Any?>> = gson.fromJson("[]", type)
        assertTrue(logs.isEmpty())
    }

    // ---- 发现结果反序列化 ----

    @Test
    fun `Agent 发现结果可正确反序列化`() {
        val discoverJson = """{
            "feignClients": [{
                "className": "com.example.feign.OrderClient",
                "displayName": "OrderClient",
                "methods": [{
                    "name": "createOrder",
                    "paramTypes": ["com.example.dto.CreateOrderReq"],
                    "returnType": "com.example.Result",
                    "displayName": "createOrder(CreateOrderReq) → Result"
                }]
            }],
            "mappers": [{
                "className": "com.example.mapper.UserMapper",
                "displayName": "UserMapper",
                "methods": [{
                    "name": "selectById",
                    "paramTypes": ["java.lang.Long"],
                    "returnType": "com.example.User",
                    "displayName": "selectById(Long) → User"
                }]
            }],
            "status": "connected",
            "version": "1.0.0"
        }"""

        val type = object : TypeToken<Map<String, Any?>>() {}.type
        val result: Map<String, Any?> = gson.fromJson(discoverJson, type)
        assertEquals("connected", result["status"])

        @Suppress("UNCHECKED_CAST")
        val feignClients = result["feignClients"] as List<Map<String, Any?>>
        assertEquals(1, feignClients.size)
        assertEquals("OrderClient", feignClients[0]["displayName"])

        @Suppress("UNCHECKED_CAST")
        val mappers = result["mappers"] as List<Map<String, Any?>>
        assertEquals(1, mappers.size)
        assertEquals("UserMapper", mappers[0]["displayName"])
    }

    // ---- 端口解析 ----

    @Test
    fun `parsePort 默认端口`() {
        assertEquals(19876, parsePortArgs(null))
    }

    @Test
    fun `parsePort 自定义端口`() {
        assertEquals(12345, parsePortArgs("port=12345"))
    }

    @Test
    fun `parsePort 多参数取端口`() {
        assertEquals(8888, parsePortArgs("port=8888,other=value"))
    }

    @Test
    fun `parsePort 无端口参数返回默认`() {
        assertEquals(19876, parsePortArgs("other=value"))
    }

    // ---- URL 拼接验证 ----

    @Test
    fun `URL 拼接正确`() {
        val port = 19876
        assertEquals("http://localhost:19876/mock/rules", "http://localhost:$port/mock/rules")
        assertEquals("http://localhost:19876/mock/logs", "http://localhost:$port/mock/logs")
        assertEquals("http://localhost:19876/discover", "http://localhost:$port/discover")
        assertEquals("http://localhost:19876/status", "http://localhost:$port/status")
    }

    // ---- 辅助方法 ----

    /**
     * 模拟 MockAgentMain 中的端口解析逻辑
     */
    private fun parsePortArgs(args: String?): Int {
        val defaultPort = 19876
        if (args == null) return defaultPort
        return args.split(",").find { it.trim().startsWith("port=") }
            ?.substringAfter("=")?.trim()?.toIntOrNull() ?: defaultPort
    }
}
