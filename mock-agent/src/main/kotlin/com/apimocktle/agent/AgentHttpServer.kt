package com.apimocktle.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/**
 * Agent HTTP Server
 *
 * Endpoints:
 *   PUT  /mock/rules  - push mock rules
 *   DELETE /mock/rules - clear all rules
 *   GET  /mock/logs    - get and drain call logs
 *   GET  /discover     - discover interceptable classes
 *   GET  /status       - agent connection status
 */
object AgentHttpServer {

    private var server: HttpServer? = null
    private var instrumentationRef: java.lang.instrument.Instrumentation? = null
    private val mapper = ObjectMapper()

    init {
        try {
            val clazz = Class.forName("com.fasterxml.jackson.module.kotlin.KotlinModule")
            mapper.registerModule(clazz.getDeclaredConstructor().newInstance() as com.fasterxml.jackson.databind.Module)
        } catch (_: Exception) {}
    }

    fun start(port: Int, instrumentation: java.lang.instrument.Instrumentation) {
        if (server != null) return
        instrumentationRef = instrumentation

        val httpServer = HttpServer.create(InetSocketAddress(port), 0)

        httpServer.createContext("/mock/rules") { exchange ->
            when (exchange.requestMethod) {
                "PUT" -> {
                    try {
                        val body = readBody(exchange)
                        val newRules: List<MockRule> = mapper.readValue(
                            body,
                            mapper.typeFactory.constructCollectionType(List::class.java, MockRule::class.java)
                        )
                        MockRuleRegistry.update(newRules)
                        retransformCglibInterceptor(instrumentation)
                        sendJson(exchange, 200, mapOf("ok" to true, "count" to newRules.size))
                    } catch (e: Exception) {
                        sendJson(exchange, 400, mapOf("ok" to false, "error" to (e.message ?: "unknown")))
                    }
                }
                "DELETE" -> {
                    MockRuleRegistry.clear()
                    CallLogCollector.clear()
                    sendJson(exchange, 200, mapOf("ok" to true))
                }
                else -> sendJson(exchange, 405, mapOf("error" to "Method not allowed"))
            }
        }

        httpServer.createContext("/mock/logs") { exchange ->
            if (exchange.requestMethod != "GET") {
                sendJson(exchange, 405, mapOf("error" to "Method not allowed"))
                return@createContext
            }
            try {
                val logs = CallLogCollector.drainAll()
                sendJson(exchange, 200, logs)
            } catch (e: Exception) {
                sendJson(exchange, 500, mapOf("error" to (e.message ?: "unknown")))
            }
        }

        httpServer.createContext("/discover") { exchange ->
            if (exchange.requestMethod != "GET") {
                sendJson(exchange, 405, mapOf("error" to "Method not allowed"))
                return@createContext
            }
            try {
                val result = RuntimeDiscovery.discover(instrumentation)
                sendJson(exchange, 200, result)
            } catch (e: Exception) {
                sendJson(exchange, 500, mapOf("error" to (e.message ?: "unknown")))
            }
        }

        httpServer.createContext("/status") { exchange ->
            sendJson(exchange, 200, AgentStatus(connected = true, version = "1.0.0", pid = ProcessHandle.current().pid()))
        }

        httpServer.executor = Executors.newFixedThreadPool(2)
        httpServer.start()
        server = httpServer
    }

    fun stop() {
        server?.stop(0)
        server = null
        instrumentationRef = null
    }

    /** Force retransform DynamicAdvisedInterceptor (may be loaded after premain). */
    private fun retransformCglibInterceptor(instrumentation: java.lang.instrument.Instrumentation) {
        val target = "org.springframework.aop.framework.CglibAopProxy\$DynamicAdvisedInterceptor"
        for (clazz in instrumentation.allLoadedClasses) {
            if (clazz.name == target) {
                try {
                    instrumentation.retransformClasses(clazz)
                } catch (_: Throwable) {}
                break
            }
        }
    }

    private fun readBody(exchange: HttpExchange): ByteArray {
        val baos = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var len: Int
        while (exchange.requestBody.read(buffer).also { len = it } != -1) {
            baos.write(buffer, 0, len)
        }
        return baos.toByteArray()
    }

    private fun sendJson(exchange: HttpExchange, status: Int, body: Any) {
        val json = mapper.writeValueAsBytes(body)
        exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(status, json.size.toLong())
        val os: OutputStream = exchange.responseBody
        os.write(json)
        os.close()
    }
}
