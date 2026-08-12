package com.example.hyt.agent.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 拦截 tools 包下所有方法，打印入参、返回值和耗时。
 */
@Aspect
@Component
public class ToolLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(ToolLoggingAspect.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Around("execution(* com.example.hyt.agent.tools..*(..))")
    public Object logToolCall(ProceedingJoinPoint pjp) throws Throwable {
        String toolName = pjp.getSignature().getDeclaringType().getSimpleName()
                + "." + pjp.getSignature().getName();
        Object[] args = pjp.getArgs();

        String argStr = "[]";
        try {
            argStr = MAPPER.writeValueAsString(args);
        } catch (Exception ignored) {
            argStr = String.valueOf(args);
        }

        long start = System.currentTimeMillis();
        try {
            Object result = pjp.proceed();
            long cost = System.currentTimeMillis() - start;
            String resultStr;
            try {
                resultStr = result == null ? "null" : truncate(MAPPER.writeValueAsString(result), 300);
            } catch (Exception ignored) {
                resultStr = String.valueOf(result);
            }
            log.info("[TOOL] ✅ {} args={} => {} ({}ms)", toolName, argStr, resultStr, cost);
            return result;
        } catch (Throwable t) {
            long cost = System.currentTimeMillis() - start;
            log.error("[TOOL] ❌ {} args={} failed after {}ms: {}", toolName, argStr, cost, t.getMessage(), t);
            throw t;
        }
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}