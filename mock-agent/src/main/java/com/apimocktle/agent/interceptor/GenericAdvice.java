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

    public static Object decodeWithJackson(String json, Type returnType, ClassLoader cl) throws Exception {
        Class<?> mc = Class.forName("com.fasterxml.jackson.databind.ObjectMapper", true, cl);
        Object mapper = mc.getDeclaredConstructor().newInstance();
        Object tf = mc.getMethod("getTypeFactory").invoke(mapper);
        Object jt = tf.getClass().getMethod("constructType", Type.class).invoke(tf, returnType);
        return mc.getMethod("readValue", String.class,
            Class.forName("com.fasterxml.jackson.databind.JavaType", true, cl)).invoke(mapper, json, jt);
    }
}
