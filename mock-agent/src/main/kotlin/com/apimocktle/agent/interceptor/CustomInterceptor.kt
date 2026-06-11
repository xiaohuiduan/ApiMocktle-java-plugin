package com.apimocktle.agent.interceptor

import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.asm.Advice
import net.bytebuddy.matcher.ElementMatchers

/**
 * Installs ByteBuddy Advice on three interception points:
 *
 * 1. CGLIB:  DynamicAdvisedInterceptor.intercept()          → GenericAdvice
 * 2. Feign:  ReflectiveFeign$FeignInvocationHandler.invoke() → JdkProxyAdvice
 * 3. Mapper: MapperProxy.invoke()                            → MapperProxyAdvice
 *
 * Notes:
 * - Feign Client beans are NOT wrapped by Spring AOP (no @Transactional etc.),
 *   so they go directly through Feign's own InvocationHandler, NOT JdkDynamicAopProxy.
 * - MyBatis Mapper calls may or may not go through JdkDynamicAopProxy (depends on @Transactional),
 *   but always go through MapperProxy.invoke(), so we intercept there directly.
 */
object CustomInterceptor {

    private const val CGLIB_INTERCEPTOR =
        "org.springframework.aop.framework.CglibAopProxy\$DynamicAdvisedInterceptor"
    private const val FEIGN_HANDLER =
        "feign.ReflectiveFeign\$FeignInvocationHandler"
    private const val MAPPER_PROXY =
        "org.apache.ibatis.binding.MapperProxy"

    private var installed = false

    fun ensureGlobalInstalled(instrumentation: java.lang.instrument.Instrumentation) {
        if (installed) return
        installed = true

        System.err.println("[MockAgent] Installing CGLIB advice on: $CGLIB_INTERCEPTOR")
        installCglibAdvice(instrumentation)

        System.err.println("[MockAgent] Installing Feign advice on: $FEIGN_HANDLER")
        installAdvice(instrumentation, FEIGN_HANDLER, JdkProxyAdvice::class.java, "invoke")

        System.err.println("[MockAgent] Installing Mapper advice on: $MAPPER_PROXY")
        installAdvice(instrumentation, MAPPER_PROXY, MapperProxyAdvice::class.java, "invoke")
    }

    /**
     * Install GenericAdvice on CglibAopProxy$DynamicAdvisedInterceptor.intercept().
     * This is the CGLIB callback entry point — all Spring CGLIB proxied beans pass through it.
     */
    private fun installCglibAdvice(instrumentation: java.lang.instrument.Instrumentation) {
        AgentBuilder.Default()
            .disableClassFormatChanges()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .type(ElementMatchers.named(CGLIB_INTERCEPTOR))
            .transform { builder, typeDesc, _, _, _ ->
                System.err.println("[MockAgent] Transforming CGLIB: ${typeDesc.name}")
                builder.visit(
                    Advice.to(GenericAdvice::class.java)
                        .on(ElementMatchers.named("intercept"))
                )
            }
            .installOn(instrumentation)

        retransformIfLoaded(instrumentation, CGLIB_INTERCEPTOR)
    }

    /**
     * Install advice on a target class's specified method.
     * Uses RETRANSFORMATION strategy so the advice is applied even if the class is already loaded.
     */
    private fun installAdvice(
        instrumentation: java.lang.instrument.Instrumentation,
        targetClassName: String,
        adviceClass: Class<*>,
        methodName: String
    ) {
        AgentBuilder.Default()
            .disableClassFormatChanges()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .type(ElementMatchers.named(targetClassName))
            .transform { builder, typeDesc, _, _, _ ->
                System.err.println("[MockAgent] Transforming: ${typeDesc.name}")
                builder.visit(
                    Advice.to(adviceClass)
                        .on(ElementMatchers.named(methodName))
                )
            }
            .installOn(instrumentation)

        retransformIfLoaded(instrumentation, targetClassName)
    }

    /** Retransform a class if it's already loaded. */
    private fun retransformIfLoaded(instrumentation: java.lang.instrument.Instrumentation, className: String) {
        for (clazz in (instrumentation.allLoadedClasses ?: emptyArray())) {
            if (clazz.name == className) {
                try {
                    instrumentation.retransformClasses(clazz)
                    System.err.println("[MockAgent] Retransformed: ${clazz.name}")
                } catch (e: Throwable) {
                    System.err.println("[MockAgent] Retransform failed for ${clazz.name}: ${e.message}")
                }
                break
            }
        }
    }

    internal fun reset() { installed = false }
}
