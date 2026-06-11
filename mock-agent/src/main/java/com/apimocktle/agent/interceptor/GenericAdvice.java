package com.apimocktle.agent.interceptor;

import com.apimocktle.agent.CallLogCollector;
import com.apimocktle.agent.MockRule;
import com.apimocktle.agent.MockRuleRegistry;
import net.bytebuddy.asm.Advice;

import java.lang.reflect.Method;
import java.lang.reflect.Type;

/**
 * Installed on Spring AOP DynamicAdvisedInterceptor.intercept() for CGLIB proxies.
 *
 * Uses @Advice.Argument(1)=Method, @Advice.Argument(2)=args.
 * onEnter matches rules, onExit decodes JSON and assigns returnValue via @Advice.Return.
 */
public class GenericAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static boolean onEnter(
            @Advice.Argument(1) Method method,
            @Advice.Argument(2) Object[] args,
            @Advice.Local("mockRule") MockRule mockRule,
            @Advice.Local("allArgs") Object[] allArgs
    ) {
        try {
            String rawName = method.getDeclaringClass().getName();
            int idx = rawName.indexOf("$$");
            String className = idx > 0 ? rawName.substring(0, idx) : rawName;
            String methodName = method.getName();

            MockRule rule = MockRuleRegistry.INSTANCE.findMatch(className, methodName, args);
            if (rule != null) {
                mockRule = rule;
                allArgs = args;
                System.err.println("[MockAgent] CGLIB MOCK HIT: " + className + "#" + methodName);
                return true;
            }
        } catch (Throwable e) {
            System.err.println("[MockAgent] GenericAdvice onEnter ERROR: " + e);
        }
        return false;
    }

    @Advice.OnMethodExit
    public static void onExit(
            @Advice.Argument(1) Method method,
            @Advice.Local("mockRule") MockRule mockRule,
            @Advice.Local("allArgs") Object[] allArgs,
            @Advice.Return(readOnly = false) Object returnValue
    ) {
        if (mockRule == null) return;

        try {
            Type returnType = method.getGenericReturnType();
            ClassLoader cl = method.getDeclaringClass().getClassLoader();
            Object decoded = decodeWithJackson(mockRule.getResponseTemplate(), returnType, cl);
            if (decoded != null) {
                returnValue = decoded;
                CallLogCollector.INSTANCE.record(
                    mockRule.getClassName(),
                    mockRule.getMethodName(),
                    allArgs,
                    mockRule,
                    decoded,
                    0
                );
            }
        } catch (Throwable e) {
            System.err.println("[MockAgent] GenericAdvice onExit ERROR: " + e);
        }
    }

    /**
     * Decode JSON to target type using Jackson.
     * Tries the given classloader first, falls back to system classloader if Jackson not found.
     * This handles the case where the target class is on Spring Boot's LaunchedClassLoader
     * but Jackson is on the system classloader.
     */
    public static Object decodeWithJackson(String json, Type returnType, ClassLoader cl) throws Exception {
        // Find a classloader that can see Jackson
        ClassLoader jacksonCl = findJacksonClassLoader(cl);

        Class<?> mc = Class.forName("com.fasterxml.jackson.databind.ObjectMapper", true, jacksonCl);
        Object mapper = mc.getDeclaredConstructor().newInstance();

        // Register JavaTimeModule if available (Spring Boot apps typically have it)
        try {
            Class<?> jtmClass = Class.forName(
                "com.fasterxml.jackson.datatype.jsr310.JavaTimeModule", true, jacksonCl);
            Object module = jtmClass.getDeclaredConstructor().newInstance();
            mc.getMethod("registerModule",
                Class.forName("com.fasterxml.jackson.databind.Module", true, jacksonCl))
                .invoke(mapper, module);
        } catch (ClassNotFoundException ignored) {
            // jackson-datatype-jsr310 not on classpath, skip
        }

        Object tf = mc.getMethod("getTypeFactory").invoke(mapper);
        Object jt = tf.getClass().getMethod("constructType", Type.class).invoke(tf, returnType);
        return mc.getMethod("readValue", String.class,
            Class.forName("com.fasterxml.jackson.databind.JavaType", true, jacksonCl)).invoke(mapper, json, jt);
    }

    /**
     * Find a classloader that can load Jackson ObjectMapper.
     * Walks up the parent chain from the given classloader, then tries system CL.
     */
    private static ClassLoader findJacksonClassLoader(ClassLoader cl) {
        // Try the given classloader and its parents
        ClassLoader current = cl;
        while (current != null) {
            try {
                Class.forName("com.fasterxml.jackson.databind.ObjectMapper", true, current);
                return current;
            } catch (ClassNotFoundException ignored) {}
            current = current.getParent();
        }
        // Try system classloader
        try {
            Class.forName("com.fasterxml.jackson.databind.ObjectMapper", true, ClassLoader.getSystemClassLoader());
            return ClassLoader.getSystemClassLoader();
        } catch (ClassNotFoundException ignored) {}
        // Fallback to original
        return cl;
    }
}
