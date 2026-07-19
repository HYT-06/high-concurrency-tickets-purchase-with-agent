package com.hyt.test.demo.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Debug 过滤器：记录所有 /chat 请求的详细信息
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ChatDebugFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(ChatDebugFilter.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();

        if (path.startsWith("/chat")) {
            log.info("=== [Demo] 拦截到 /chat 请求 ===");
            log.info("[Demo] Method: {}", httpRequest.getMethod());
            log.info("[Demo] URI: {}", path);
            log.info("[Demo] Content-Type: {}", httpRequest.getContentType());
            log.info("[Demo] Authorization: {}",
                    httpRequest.getHeader("Authorization") != null ? "存在" : "无");
        }

        chain.doFilter(request, response);

        if (path.startsWith("/chat")) {
            log.info("[Demo] /chat 请求处理完成");
        }
    }
}
