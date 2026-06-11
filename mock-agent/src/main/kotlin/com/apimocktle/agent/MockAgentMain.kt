package com.apimocktle.agent

/**
 * Mock Agent entry point.
 *
 * Intercepts Spring AOP CGLIB proxy entry point:
 *   DynamicAdvisedInterceptor.intercept(Object, Method, Object[], MethodProxy)
 *
 * This covers ALL Spring CGLIB proxied beans (Service, Component, etc.).
 *
 * Feign/MyBatis calls are NOT intercepted at their own proxy level because:
 * - Feign uses ReflectiveFeign$FeignInvocationHandler (internal proxy, not Spring AOP)
 * - MyBatis uses MapperProxy (internal proxy)
 * - Both use classloaders incompatible with ByteBuddy Advice inline
 *
 * Instead, mock the Service method (e.g. OrderService.createOrder) directly —
 * this skips all internal Feign/Mapper calls.
 */
class MockAgentMain {

    companion object {
        private const val DEFAULT_PORT = 19876

        @JvmStatic
        fun premain(args: String?, instrumentation: java.lang.instrument.Instrumentation) {
            start(args, instrumentation)
        }

        @JvmStatic
        fun agentmain(args: String?, instrumentation: java.lang.instrument.Instrumentation) {
            start(args, instrumentation)
        }

        private fun start(args: String?, instrumentation: java.lang.instrument.Instrumentation) {
            val port = parsePort(args)
            try {
                // Install GenericAdvice on Spring's DynamicAdvisedInterceptor
                com.apimocktle.agent.interceptor.CustomInterceptor.ensureGlobalInstalled(instrumentation)

                AgentHttpServer.start(port, instrumentation)
                writePortFile(port)
                println("[MockAgent] Started on port $port, PID=${ProcessHandle.current().pid()}")
            } catch (e: Exception) {
                System.err.println("[MockAgent] Failed to start: ${e.message}")
                e.printStackTrace()
            }
        }

        private fun parsePort(args: String?): Int {
            if (args == null) return DEFAULT_PORT
            return args.split(",").find { it.trim().startsWith("port=") }
                ?.substringAfter("=")?.trim()?.toIntOrNull() ?: DEFAULT_PORT
        }

        private fun writePortFile(port: Int) {
            try {
                java.io.File("${System.getProperty("user.home")}/.apimocktle-agent-port")
                    .writeText(port.toString())
            } catch (_: Exception) {}
        }
    }
}
