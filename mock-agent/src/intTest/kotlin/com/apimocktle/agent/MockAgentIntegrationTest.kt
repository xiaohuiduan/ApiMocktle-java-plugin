package com.apimocktle.agent

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Integration tests: starts mock-test-app with -javaagent:mock-agent.jar,
 * then verifies Mock Agent intercepts Feign (JDK proxy) and MyBatis (JDK proxy) calls.
 *
 * Run with:
 *   ./gradlew :mock-agent:intTest -PmockTestAppJar=/path/to/mock-test-app.jar
 *
 * Prerequisites:
 *   - mock-agent.jar built via shadowJar
 *   - mock-test-app.jar pre-built at the specified path
 */
class MockAgentIntegrationTest {

    companion object {
        private val AGENT_JAR = System.getProperty("mock.agent.jar") ?: ""
        private val TEST_APP_JAR = System.getProperty("mock.test.app.jar") ?: ""
        private const val APP_PORT = 8080
        private const val AGENT_PORT = 19876
        private const val STARTUP_TIMEOUT_SECONDS = 30L

        private val mapper = ObjectMapper()
    }

    private var appProcess: Process? = null

    @Before
    fun setup() {
        assumeTrue("mock.agent.jar not set", AGENT_JAR.isNotEmpty())
        assumeTrue("mock.test.app.jar not set", TEST_APP_JAR.isNotEmpty())
        assumeTrue("mock-agent.jar not found", java.io.File(AGENT_JAR).exists())
        assumeTrue("mock-test-app.jar not found", java.io.File(TEST_APP_JAR).exists())

        killExistingJava()
        startApp()
        waitForStartup()
    }

    @After
    fun teardown() {
        appProcess?.destroyForcibly()
        appProcess = null
    }

    // ==================== Feign Client Mock (JDK Proxy) ====================

    @Test
    fun feignClientMock_userClient() {
        // Push mock rules for both Feign Clients (createOrder calls both)
        val rules = listOf(
            mapOf(
                "id" to "feign-user",
                "className" to "com.example.order.feign.UserClient",
                "methodName" to "getUser",
                "responseTemplate" to """{"code":200,"message":"success","data":{"id":1,"username":"MOCK-USER","email":"mock@test.com","role":"admin"}}"""
            ),
            mapOf(
                "id" to "feign-inv",
                "className" to "com.example.order.feign.InventoryClient",
                "methodName" to "checkStock",
                "responseTemplate" to """{"code":200,"message":"success","data":{"available":true,"stock":100}}"""
            )
        )
        val pushResult = httpPut("/mock/rules", mapper.writeValueAsString(rules))
        val pushResp: Map<*, *> = mapper.readValue(pushResult, Map::class.java)
        assertEquals(true, pushResp["ok"])

        // Trigger createOrder — should use mocked UserClient response
        val orderBody = """{"userId":1,"productId":1,"quantity":2}"""
        val orderResult = httpPost("/api/orders", orderBody)
        println("[IntegrationTest] createOrder response: $orderResult")
        val orderResp: Map<*, *> = mapper.readValue(orderResult, Map::class.java)
        assertEquals(200, orderResp["code"])

        val data = orderResp["data"] as Map<*, *>
        assertNotNull("Order should be created", data["orderNo"])

        // Verify mock logs
        val logsResult = httpGet("/mock/logs")
        val logs: List<*> = mapper.readValue(logsResult, List::class.java)
        assertTrue("Should have at least 1 mock log", logs.isNotEmpty())
        val userLog = (logs as List<Map<*, *>>).find { it["className"] == "com.example.order.feign.UserClient" }
        assertNotNull("Should have UserClient mock log", userLog)
    }

    @Test
    fun feignClientMock_inventoryClient() {
        // Push mock rule for InventoryClient.checkStock()
        val rules = listOf(mapOf(
            "id" to "feign-inv",
            "className" to "com.example.order.feign.InventoryClient",
            "methodName" to "checkStock",
            "responseTemplate" to """{"code":200,"message":"success","data":{"available":true,"stock":100}}"""
        ))
        httpPut("/mock/rules", mapper.writeValueAsString(rules))

        // Also mock UserClient so the full chain works
        val userRules = listOf(mapOf(
            "id" to "feign-user",
            "className" to "com.example.order.feign.UserClient",
            "methodName" to "getUser",
            "responseTemplate" to """{"code":200,"message":"success","data":{"id":1,"username":"MOCK","email":"m@t.com","role":"user"}}"""
        ))
        httpPut("/mock/rules", mapper.writeValueAsString(userRules + rules))

        val orderResult = httpPost("/api/orders", """{"userId":1,"productId":1,"quantity":1}""")
        val orderResp: Map<*, *> = mapper.readValue(orderResult, Map::class.java)
        assertEquals(200, orderResp["code"])
    }

    // ==================== MyBatis Mapper Mock (JDK Proxy) ====================

    @Test
    fun mybatisMapperMock_productMapper() {
        // Push mock rule for ProductMapper.selectById()
        val rules = listOf(mapOf(
            "id" to "mapper-product",
            "className" to "com.example.order.mapper.ProductMapper",
            "methodName" to "selectById",
            "responseTemplate" to """{"id":999,"name":"MOCK-PRODUCT","price":0.01,"stock":9999,"category":"mock"}"""
        ))
        httpPut("/mock/rules", mapper.writeValueAsString(rules))

        // Also mock Feign calls so createOrder doesn't fail on remote calls
        val feignRules = listOf(
            mapOf("id" to "feign-user", "className" to "com.example.order.feign.UserClient",
                "methodName" to "getUser",
                "responseTemplate" to """{"code":200,"message":"ok","data":{"id":1,"username":"MOCK","email":"m@t.com","role":"user"}}"""),
            mapOf("id" to "feign-inv", "className" to "com.example.order.feign.InventoryClient",
                "methodName" to "checkStock",
                "responseTemplate" to """{"code":200,"message":"ok","data":{"available":true,"stock":100}}""")
        )
        httpPut("/mock/rules", mapper.writeValueAsString(feignRules + rules))

        val orderResult = httpPost("/api/orders", """{"userId":1,"productId":1,"quantity":1}""")
        val orderResp: Map<*, *> = mapper.readValue(orderResult, Map::class.java)
        assertEquals(200, orderResp["code"])

        // Verify the product was mocked (order total should be based on mocked price 0.01)
        val data = orderResp["data"] as Map<*, *>
        // The totalAmount should reflect the mocked product price
        assertNotNull("Order should have totalAmount", data["totalAmount"])
    }

    // ==================== CGLIB Mock (existing, regression test) ====================

    @Test
    fun cglibMock_orderService() {
        // Push mock rule for OrderService.createOrder() — CGLIB proxy
        val rules = listOf(mapOf(
            "id" to "cglib-order",
            "className" to "com.example.order.OrderService",
            "methodName" to "createOrder",
            "responseTemplate" to """{"orderId":999,"orderNo":"MOCK-CGLIB","status":"mocked","totalAmount":0}"""
        ))
        httpPut("/mock/rules", mapper.writeValueAsString(rules))

        val orderResult = httpPost("/api/orders", """{"userId":1,"productId":1,"quantity":1}""")
        val orderResp: Map<*, *> = mapper.readValue(orderResult, Map::class.java)
        assertEquals(200, orderResp["code"])

        val data = orderResp["data"] as Map<*, *>
        assertEquals("MOCK-CGLIB", data["orderNo"])
        assertEquals("mocked", data["status"])
    }

    // ==================== Mock Logs ====================

    @Test
    fun mockLogs_drainAfterRead() {
        // Push a CGLIB rule and trigger a call
        val rules = listOf(mapOf(
            "id" to "log-test",
            "className" to "com.example.order.OrderService",
            "methodName" to "createOrder",
            "responseTemplate" to """{"orderId":1,"orderNo":"LOG-TEST","status":"ok","totalAmount":0}"""
        ))
        httpPut("/mock/rules", mapper.writeValueAsString(rules))
        httpPost("/api/orders", """{"userId":1,"productId":1,"quantity":1}""")

        // First drain should have logs
        val logs1: List<*> = mapper.readValue(httpGet("/mock/logs"), List::class.java)
        assertTrue("First drain should have logs", logs1.isNotEmpty())

        // Second drain should be empty
        val logs2: List<*> = mapper.readValue(httpGet("/mock/logs"), List::class.java)
        assertTrue("Second drain should be empty", logs2.isEmpty())
    }

    // ==================== Agent Status ====================

    @Test
    fun agentStatusEndpoint() {
        val statusResult = httpGet("/status")
        val status: Map<*, *> = mapper.readValue(statusResult, Map::class.java)
        assertEquals(true, status["connected"])
        assertEquals("1.0.0", status["version"])
        assertNotNull(status["pid"])
    }

    // ==================== Discover ====================

    @Test
    fun discover_findsFeignClients() {
        val discoverResult = httpGet("/discover")
        val result: Map<*, *> = mapper.readValue(discoverResult, Map::class.java)
        val feignClients = result["feignClients"] as List<*>
        assertTrue("Should discover at least 2 Feign clients", feignClients.size >= 2)

        val classNames = feignClients.map { (it as Map<*, *>)["className"] }
        assertTrue("Should find UserClient", classNames.contains("com.example.order.feign.UserClient"))
        assertTrue("Should find InventoryClient", classNames.contains("com.example.order.feign.InventoryClient"))
    }

    // ==================== Helper ====================

    private fun startApp() {
        val cmd = listOf(
            "java",
            "-javaagent:$AGENT_JAR",
            "-jar", TEST_APP_JAR
        )
        println("[IntegrationTest] Starting: ${cmd.joinToString(" ")}")
        appProcess = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start()

        // Log stdout in background
        Thread {
            BufferedReader(InputStreamReader(appProcess!!.inputStream)).lines()
                .forEach { println("[app] $it") }
        }.apply { isDaemon = true; start() }
    }

    private fun waitForStartup() {
        val deadline = System.currentTimeMillis() + STARTUP_TIMEOUT_SECONDS * 1000
        while (System.currentTimeMillis() < deadline) {
            try {
                val conn = URL("http://localhost:$AGENT_PORT/status").openConnection() as HttpURLConnection
                conn.connectTimeout = 1000
                conn.readTimeout = 1000
                if (conn.responseCode == 200) {
                    println("[IntegrationTest] Agent is ready")
                    // Also wait for the app
                    waitForApp()
                    return
                }
            } catch (_: Exception) {
                Thread.sleep(500)
            }
        }
        fail("Agent did not start within ${STARTUP_TIMEOUT_SECONDS}s")
    }

    private fun waitForApp() {
        val deadline = System.currentTimeMillis() + STARTUP_TIMEOUT_SECONDS * 1000
        while (System.currentTimeMillis() < deadline) {
            try {
                val conn = URL("http://localhost:$APP_PORT/api/orders/health").openConnection() as HttpURLConnection
                conn.connectTimeout = 1000
                conn.readTimeout = 1000
                if (conn.responseCode == 200) {
                    println("[IntegrationTest] App is ready")
                    return
                }
            } catch (_: Exception) {
                Thread.sleep(500)
            }
        }
        fail("App did not start within ${STARTUP_TIMEOUT_SECONDS}s")
    }

    private fun killExistingJava() {
        // Kill only processes using our specific ports (not ALL java.exe)
        killProcessOnPort(APP_PORT)
        killProcessOnPort(AGENT_PORT)
        Thread.sleep(1000)
    }

    private fun killProcessOnPort(port: Int) {
        try {
            // Find PID using the port via netstat
            val proc = ProcessBuilder("cmd", "/c", "netstat -ano | findstr :$port | findstr LISTENING")
                .redirectErrorStream(true).start()
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor(5, TimeUnit.SECONDS)

            val pids = output.lines()
                .mapNotNull { line -> line.trim().split("\\s+".toRegex()).lastOrNull()?.toLongOrNull() }
                .filter { it > 0 }
                .distinct()

            for (pid in pids) {
                try {
                    ProcessBuilder("taskkill", "/F", "/PID", pid.toString())
                        .redirectErrorStream(true).start().waitFor(5, TimeUnit.SECONDS)
                    println("[IntegrationTest] Killed PID $pid on port $port")
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    private fun httpGet(path: String): String {
        val conn = URL("http://localhost:$AGENT_PORT$path").openConnection() as HttpURLConnection
        conn.connectTimeout = 5000; conn.readTimeout = 10000; conn.requestMethod = "GET"
        val r = conn.inputStream.bufferedReader().readText(); conn.disconnect(); return r
    }

    private fun httpPut(path: String, body: String): String {
        val conn = URL("http://localhost:$AGENT_PORT$path").openConnection() as HttpURLConnection
        conn.connectTimeout = 5000; conn.readTimeout = 10000; conn.requestMethod = "PUT"
        conn.doOutput = true; conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.outputStream.write(body.toByteArray(Charsets.UTF_8))
        val r = conn.inputStream.bufferedReader().readText(); conn.disconnect(); return r
    }

    private fun httpPost(path: String, body: String): String {
        val conn = URL("http://localhost:$APP_PORT$path").openConnection() as HttpURLConnection
        conn.connectTimeout = 5000; conn.readTimeout = 15000; conn.requestMethod = "POST"
        conn.doOutput = true; conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.outputStream.write(body.toByteArray(Charsets.UTF_8))
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val r = stream.bufferedReader().readText(); conn.disconnect(); return r
    }
}
