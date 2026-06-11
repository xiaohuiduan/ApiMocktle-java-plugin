package com.apimocktle.agent.interceptor

import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.asm.Advice
import net.bytebuddy.matcher.ElementMatchers

/**
 * Installs GenericAdvice on Spring's DynamicAdvisedInterceptor.intercept().
 *
 * This is the CGLIB callback entry point — a PLAIN class (not proxy),
 * so @Advice works correctly.
 * ALL Spring CGLIB proxied beans pass through this single method.
 */
object CustomInterceptor {

    private val INTERCEPTOR_CLASS =
        "org.springframework.aop.framework.CglibAopProxy\$DynamicAdvisedInterceptor"

    private var installed = false

    fun ensureGlobalInstalled(instrumentation: java.lang.instrument.Instrumentation) {
        if (installed) return
        installed = true

        System.err.println("[MockAgent] Installing GenericAdvice on: $INTERCEPTOR_CLASS")

        AgentBuilder.Default()
            .disableClassFormatChanges()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .type(ElementMatchers.named(INTERCEPTOR_CLASS))
            .transform { builder, typeDesc, _, _, _ ->
                System.err.println("[MockAgent] Transforming: ${typeDesc.name}")
                builder.visit(
                    Advice.to(GenericAdvice::class.java)
                        .on(ElementMatchers.named("intercept"))
                )
            }
            .installOn(instrumentation)

        // Retransform if already loaded (it usually is, in Spring apps)
        for (clazz in instrumentation.allLoadedClasses) {
            if (clazz.name == INTERCEPTOR_CLASS) {
                try {
                    instrumentation.retransformClasses(clazz)
                    System.err.println("[MockAgent] Retransformed: ${clazz.name}")
                } catch (e: Throwable) {
                    System.err.println("[MockAgent] Retransform failed: ${e.message}")
                }
                break
            }
        }
    }

    internal fun reset() { installed = false }
}
