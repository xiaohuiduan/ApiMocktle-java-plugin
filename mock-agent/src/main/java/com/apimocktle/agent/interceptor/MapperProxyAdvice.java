package com.apimocktle.agent.interceptor;

import net.bytebuddy.asm.Advice;

import java.lang.reflect.Method;
import java.lang.reflect.Type;

/**
 * Installed on MyBatis MapperProxy.invoke() for Mapper proxies.
 *
 * ALL reflection logic is inlined directly into onEnter/onExit — no helper method calls.
 */
public class MapperProxyAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static boolean onEnter(
            @Advice.FieldValue("mapperInterface") Class<?> mapperInterface,
            @Advice.Argument(1) Method method,
            @Advice.Argument(2) Object[] args,
            @Advice.Local("mockRule") Object mockRule
    ) {
        try {
            if (method.getDeclaringClass() == Object.class) return false;

            String className = mapperInterface.getName();
            String methodName = method.getName();

            ClassLoader ctxCl = Thread.currentThread().getContextClassLoader();
            Class<?> registryClass = Class.forName("com.apimocktle.agent.MockRuleRegistry", true, ctxCl);
            Object registryInstance = registryClass.getField("INSTANCE").get(null);
            Method findMatchMethod = registryClass.getMethod("findMatch", String.class, String.class, Object[].class);
            Object rule = findMatchMethod.invoke(registryInstance, className, methodName, args);

            if (rule != null) {
                mockRule = rule;
                System.err.println("[MockAgent] MAPPER MOCK HIT: " + className + "#" + methodName);
                return true;
            }
        } catch (Throwable e) {
            System.err.println("[MockAgent] MapperProxyAdvice onEnter ERROR: " + e);
        }
        return false;
    }

    @Advice.OnMethodExit
    public static void onExit(
            @Advice.FieldValue("mapperInterface") Class<?> mapperInterface,
            @Advice.Argument(1) Method method,
            @Advice.Local("mockRule") Object mockRule,
            @Advice.Return(readOnly = false) Object returnValue
    ) {
        if (mockRule == null) return;

        try {
            ClassLoader ctxCl = Thread.currentThread().getContextClassLoader();

            Method getResponseTemplate = mockRule.getClass().getMethod("getResponseTemplate");
            String responseTemplate = (String) getResponseTemplate.invoke(mockRule);

            String className = mapperInterface.getName();
            String methodName = method.getName();
            Type returnType = method.getGenericReturnType();
            ClassLoader cl = mapperInterface.getClassLoader();

            Class<?> genericAdviceClass = Class.forName(
                "com.apimocktle.agent.interceptor.GenericAdvice", true, ctxCl);
            Method decodeMethod = genericAdviceClass.getMethod(
                "decodeWithJackson", String.class, Type.class, ClassLoader.class);
            Object decoded = decodeMethod.invoke(null, responseTemplate, returnType, cl);

            if (decoded != null) {
                returnValue = decoded;

                Class<?> collectorClass = Class.forName("com.apimocktle.agent.CallLogCollector", true, ctxCl);
                Object collectorInstance = collectorClass.getField("INSTANCE").get(null);
                Class<?> mockRuleClass = Class.forName("com.apimocktle.agent.MockRule", true, ctxCl);
                Method recordMethod = collectorClass.getMethod("record",
                    String.class, String.class, Object[].class,
                    mockRuleClass, Object.class, long.class);
                recordMethod.invoke(collectorInstance, className, methodName, new Object[0], mockRule, decoded, 0L);
            }
        } catch (Throwable e) {
            System.err.println("[MockAgent] MapperProxyAdvice onExit ERROR: " + e);
        }
    }
}
