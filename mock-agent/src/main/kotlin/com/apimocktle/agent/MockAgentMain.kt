package com.apimocktle.agent

/**
 * Mock Agent entry point.
 *
 * Intercepts Spring AOP proxy entry points:
 *   1. CGLIB:  DynamicAdvisedInterceptor.intercept()  → GenericAdvice
 *   2. JDK:    JdkDynamicAopProxy.invoke()            → JdkProxyAdvice
 *   3. Mapper: MapperProxy.invoke()                    → MapperProxyAdvice
 *
 * Agent classes (ReflectiveAgentBridge, MockRule, etc.) live on the system classloader.
 * Advice classes use Class.forName(..., ClassLoader.getSystemClassLoader()) to access them,
 * avoiding classloader visibility issues with bootstrap classloader / JDK modules.
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
                // Install advice on all three interception points:
                // 1. CGLIB:  DynamicAdvisedInterceptor.intercept()       → GenericAdvice
                // 2. Feign:  ReflectiveFeign$FeignInvocationHandler      → JdkProxyAdvice
                // 3. Mapper: MapperProxy.invoke()                         → MapperProxyAdvice
                //
                // JdkProxyAdvice and MapperProxyAdvice use pure reflection (Class.forName +
                // Thread.currentThread().getContextClassLoader()) to access Agent classes,
                // avoiding classloader visibility issues with Spring Boot's LaunchedClassLoader.
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
