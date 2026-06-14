package com.apimocktle.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/**
 * Agent HTTP Server — lightweight HTTP API using com.sun.net.httpserver.
 *
 * Endpoints:
 *   PUT  /mock/rules     - push mock rules (rejected when muted)
 *   DELETE /mock/rules    - clear all rules
 *   GET  /mock/logs       - get and drain call logs
 *   GET  /logs            - get agent runtime logs
 *   GET  /discover        - discover interceptable classes
 *   GET  /status          - agent connection status (includes muted flag)
 *   POST /agent/mute      - mute: clear rules + reject new rules
 *   POST /agent/unmute    - unmute: accept rules again
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

        // ==================== Mock Rules ====================

        httpServer.createContext("/mock/rules", object : HttpHandler {
            override fun handle(exchange: HttpExchange) {
                when (exchange.requestMethod) {
                    "PUT" -> {
                        if (MockRuleRegistry.isMuted()) {
                            AgentLogCollector.warn("Rejected rule push: agent is muted")
                            sendJson(exchange, 403, mapOf("ok" to false, "error" to "Agent is muted, rules rejected"))
                            return
                        }
                        try {
                            val body = readBody(exchange)
                            val newRules: List<MockRule> = mapper.readValue(
                                body,
                                mapper.typeFactory.constructCollectionType(List::class.java, MockRule::class.java)
                            )
                            val accepted = MockRuleRegistry.update(newRules)
                            if (!accepted) {
                                sendJson(exchange, 403, mapOf("ok" to false, "error" to "Agent is muted, rules rejected"))
                                return
                            }
                            retransformClass(instrumentation, "org.springframework.aop.framework.CglibAopProxy\$DynamicAdvisedInterceptor")
                            retransformClass(instrumentation, "feign.ReflectiveFeign\$FeignInvocationHandler")
                            retransformClass(instrumentation, "org.apache.ibatis.binding.MapperProxy")
                            AgentLogCollector.info("Pushed ${newRules.size} mock rules")
                            sendJson(exchange, 200, mapOf("ok" to true, "count" to newRules.size))
                        } catch (e: Exception) {
                            AgentLogCollector.error("Failed to push rules", e)
                            sendJson(exchange, 400, mapOf("ok" to false, "error" to (e.message ?: "unknown")))
                        }
                    }
                    "DELETE" -> {
                        MockRuleRegistry.clear()
                        CallLogCollector.clear()
                        AgentLogCollector.info("Cleared all mock rules")
                        sendJson(exchange, 200, mapOf("ok" to true))
                    }
                    else -> sendJson(exchange, 405, mapOf("error" to "Method not allowed"))
                }
            }
        })

        // ==================== Mock Call Logs ====================

        httpServer.createContext("/mock/logs", object : HttpHandler {
            override fun handle(exchange: HttpExchange) {
                if (exchange.requestMethod != "GET") {
                    sendJson(exchange, 405, mapOf("error" to "Method not allowed"))
                    return
                }
                try {
                    val logs = CallLogCollector.drainAll()
                    sendJson(exchange, 200, logs)
                } catch (e: Exception) {
                    sendJson(exchange, 500, mapOf("error" to (e.message ?: "unknown")))
                }
            }
        })

        // ==================== Discover ====================

        httpServer.createContext("/discover", object : HttpHandler {
            override fun handle(exchange: HttpExchange) {
                if (exchange.requestMethod != "GET") {
                    sendJson(exchange, 405, mapOf("error" to "Method not allowed"))
                    return
                }
                try {
                    val result = RuntimeDiscovery.discover(instrumentation)
                    sendJson(exchange, 200, result)
                } catch (e: Exception) {
                    sendJson(exchange, 500, mapOf("error" to (e.message ?: "unknown")))
                }
            }
        })

        // ==================== Status ====================

        httpServer.createContext("/status", object : HttpHandler {
            override fun handle(exchange: HttpExchange) {
                sendJson(exchange, 200, mapOf(
                    "connected" to true,
                    "version" to "1.0.0",
                    "pid" to ProcessHandle.current().pid(),
                    "muted" to MockRuleRegistry.isMuted()
                ))
            }
        })

        // ==================== Agent Runtime Logs ====================

        httpServer.createContext("/logs", object : HttpHandler {
            override fun handle(exchange: HttpExchange) {
                if (exchange.requestMethod != "GET") {
                    sendJson(exchange, 405, mapOf("error" to "Method not allowed"))
                    return
                }
                try {
                    val n = exchange.requestURI.query?.let { query ->
                        query.split("&")
                            .find { it.startsWith("n=") }
                            ?.substringAfter("=")
                            ?.toIntOrNull()
                    }
                    val logs = if (n != null) AgentLogCollector.getRecent(n) else AgentLogCollector.getAll()
                    sendJson(exchange, 200, logs)
                } catch (e: Exception) {
                    sendJson(exchange, 500, mapOf("error" to (e.message ?: "unknown")))
                }
            }
        })

        // ==================== Agent Mute / Unmute ====================

        httpServer.createContext("/agent/mute", object : HttpHandler {
            override fun handle(exchange: HttpExchange) {
                if (exchange.requestMethod != "POST") {
                    sendJson(exchange, 405, mapOf("error" to "Method not allowed"))
                    return
                }
                MockRuleRegistry.mute()
                sendJson(exchange, 200, mapOf("ok" to true, "muted" to true))
            }
        })

        httpServer.createContext("/agent/unmute", object : HttpHandler {
            override fun handle(exchange: HttpExchange) {
                if (exchange.requestMethod != "POST") {
                    sendJson(exchange, 405, mapOf("error" to "Method not allowed"))
                    return
                }
                MockRuleRegistry.unmute()
                sendJson(exchange, 200, mapOf("ok" to true, "muted" to false))
            }
        })

        httpServer.executor = Executors.newFixedThreadPool(2)
        httpServer.start()
        server = httpServer
    }

    fun stop() {
        server?.stop(0)
        server = null
        instrumentationRef = null
    }

    private fun retransformClass(instrumentation: java.lang.instrument.Instrumentation, className: String) {
        for (clazz in (instrumentation.allLoadedClasses ?: emptyArray())) {
            if (clazz.name == className) {
                try { instrumentation.retransformClasses(clazz) } catch (_: Throwable) {}
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
