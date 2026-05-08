package com.teya.ledger.api.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

/**
 * Registers {@link IdempotencyInterceptor} on every request and wraps both
 * the inbound request and outbound response so the interceptor can:
 * <ul>
 *   <li>Read the request body without consuming the controller's own read
 *       (via {@link ContentCachingRequestWrapper}).</li>
 *   <li>Read the response body after the controller writes it (via
 *       {@link ContentCachingResponseWrapper}), while still forwarding
 *       the bytes to the real response.</li>
 * </ul>
 */
@Configuration
public class IdempotencyConfig implements WebMvcConfigurer {

    private final IdempotencyInterceptor interceptor;

    /** Constructs the configuration with the idempotency interceptor to register. */
    public IdempotencyConfig(IdempotencyInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    /**
     * Adds {@link IdempotencyInterceptor} to the MVC interceptor chain so it
     * runs for every request before and after the handler method.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor);
    }

    /**
     * Registers {@link BodyCachingFilter} at order 0 so it runs before the
     * dispatcher servlet, ensuring the wrappers are in place before the
     * interceptor's {@code preHandle} is called.
     */
    @Bean
    public FilterRegistrationBean<BodyCachingFilter> bodyCachingFilter() {
        FilterRegistrationBean<BodyCachingFilter> reg = new FilterRegistrationBean<>(
                new BodyCachingFilter());
        reg.setOrder(0);
        reg.addUrlPatterns("/*");
        return reg;
    }

    /**
     * Servlet filter that wraps each request and response so their bodies can
     * be read by the idempotency interceptor without consuming them.
     *
     * <p>The {@link CachedBodyHttpServletRequest} eagerly buffers the request
     * body on construction so the interceptor can read it in {@code preHandle}
     * and the controller can still read it normally — Spring's
     * {@code ContentCachingRequestWrapper} doesn't work here because it's a
     * read-through accumulator, so the interceptor would see an empty body
     * (nothing has read the stream yet).
     *
     * <p>The {@link ContentCachingResponseWrapper} captures bytes written by
     * the controller. {@code copyBodyToResponse()} is called in a {@code finally}
     * block to flush those captured bytes to the real response — without this
     * call the client receives an empty body.
     */
    static final class BodyCachingFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain chain)
                throws ServletException, IOException {
            CachedBodyHttpServletRequest wrappedReq = new CachedBodyHttpServletRequest(request);
            ContentCachingResponseWrapper wrappedResp = new ContentCachingResponseWrapper(response);
            try {
                chain.doFilter(wrappedReq, wrappedResp);
            } finally {
                // Flush the captured response bytes to the real output stream.
                // Without this, the client receives an empty body.
                wrappedResp.copyBodyToResponse();
            }
        }
    }
}
