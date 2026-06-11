package com.apimocktle.agent.interceptor;

import net.bytebuddy.asm.Advice;

import java.lang.reflect.Method;
import java.lang.reflect.Type;

/**
 * Installed on ReflectiveFeign$FeignInvocationHandler.invoke() for Feign Client proxies.
 *
 * ALL reflection logic is inlined directly into onEnter/onExit — no helper method calls.
 * This is critical because ByteBuddy inlines this code into the target class bytecode,
 * and any invokestatic to a method in this class would fail with IllegalAccessError
 * (target class is on LaunchedClassLoader, this class is on system CL).
 */
public class JdkProxyAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static boolean onEnter(
            @Advice.Argument(1) Method method,
            @Advice.Argument(2) Object[] args,
            @Advice.Local("mockRule") Object mockRule
    ) {
        try {
            if (method.getDeclaringClass() == Object.class) return false;

            String className = method.getDeclaringClass().getName();
            String methodName = method.getName();

            // Pure reflection — load MockRuleRegistry from the context classloader
            ClassLoader ctxCl = Thread.currentThread().getContextClassLoader();
            Class<?> registryClass = Class.forName("com.apimocktle.agent.MockRuleRegistry", true, ctxCl);
            Object registryInstance = registryClass.getField("INSTANCE").get(null);
            Method findMatchMethod = registryClass.getMethod("findMatch", String.class, String.class, Object[].class);
            Object rule = findMatchMethod.invoke(registryInstance, className, methodName, args);

            if (rule != null) {
                mockRule = rule;
                System.err.println("[MockAgent] FEIGN MOCK HIT: " + className + "#" + methodName);
                return true;
            }
        } catch (Throwable e) {
            System.err.println("[MockAgent] JdkProxyAdvice onEnter ERROR: " + e);
        }
        return false;
    }

    @Advice.OnMethodExit
    public static void onExit(
            @Advice.Argument(1) Method method,
            @Advice.Local("mockRule") Object mockRule,
            @Advice.Return(readOnly = false) Object returnValue
    ) {
        if (mockRule == null) return;

        try {
            ClassLoader ctxCl = Thread.currentThread().getContextClassLoader();

            // Get responseTemplate from rule via reflection
            Method getResponseTemplate = mockRule.getClass().getMethod("getResponseTemplate");
            String responseTemplate = (String) getResponseTemplate.invoke(mockRule);

            // Decode JSON to the return type using GenericAdvice.decodeWithJackson via reflection
            Type returnType = method.getGenericReturnType();
            ClassLoader cl = method.getDeclaringClass().getClassLoader();
            Class<?> genericAdviceClass = Class.forName(
                "com.apimocktle.agent.interceptor.GenericAdvice", true, ctxCl);
            Method decodeMethod = genericAdviceClass.getMethod(
                "decodeWithJackson", String.class, Type.class, ClassLoader.class);
            Object decoded = decodeMethod.invoke(null, responseTemplate, returnType, cl);

            if (decoded != null) {
                returnValue = decoded;

                // Record the call via reflection
                Class<?> collectorClass = Class.forName("com.apimocktle.agent.CallLogCollector", true, ctxCl);
                Object collectorInstance = collectorClass.getField("INSTANCE").get(null);
                Class<?> mockRuleClass = Class.forName("com.apimocktle.agent.MockRule", true, ctxCl);
                Method recordMethod = collectorClass.getMethod("record",
                    String.class, String.class, Object[].class,
                    mockRuleClass, Object.class, long.class);
                recordMethod.invoke(collectorInstance,
                    method.getDeclaringClass().getName(), method.getName(), new Object[0], mockRule, decoded, 0L);
            }
        } catch (Throwable e) {
            System.err.println("[MockAgent] JdkProxyAdvice onExit ERROR: " + e);
            e.printStackTrace();
        }
    }
}
