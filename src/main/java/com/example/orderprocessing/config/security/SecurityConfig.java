package com.example.orderprocessing.config.security;

import com.example.orderprocessing.interfaces.http.error.ApiError;
import com.example.orderprocessing.infrastructure.security.RoleClaimJwtAuthenticationConverter;
import com.example.orderprocessing.infrastructure.web.RateLimitingFilter;
import com.example.orderprocessing.infrastructure.web.RequestContextFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
/**
 * Security and filter-chain wiring for the interface layer.
 *
 * <p><b>Architecture role:</b> infrastructure configuration that composes HTTP adapters
 * (authentication, authorization, request context, and rate limiting).</p>
 *
 * <p><b>Idempotency and resilience context:</b> idempotency is enforced in application services,
 * while this class enforces admission controls. Rate limiting acts as fail-open under Redis
 * degradation to preserve API availability.</p>
 *
 * <p><b>Transaction boundary:</b> none. This class defines runtime security behavior only.</p>
 */
public class SecurityConfig {

    private final ObjectMapper objectMapper;

    /**
     * Creates security configuration dependencies.
     * @param objectMapper mapper used for JSON error responses
     */
    public SecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    /**
     * Builds the security filter chain for API endpoints.
     * @param http HTTP security builder
     * @param rateLimitingFilter redis-backed rate limiting filter
     * @return configured security filter chain
     * @throws Exception when security configuration fails
     */
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   RateLimitingFilter rateLimitingFilter) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                        .requestMatchers(HttpMethod.POST, "/orders").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/orders", "/orders/*").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/orders/*/cancel").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/orders/*/status").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(new RoleClaimJwtAuthenticationConverter())))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(401);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            ApiError body = new ApiError(
                                    "UNAUTHORIZED",
                                    "Authentication required or token invalid",
                                    MDC.get(RequestContextFilter.REQUEST_ID),
                                    Instant.now());
                            response.getWriter().write(objectMapper.writeValueAsString(body));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(403);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            ApiError body = new ApiError(
                                    "FORBIDDEN",
                                    "Insufficient permissions",
                                    MDC.get(RequestContextFilter.REQUEST_ID),
                                    Instant.now());
                            response.getWriter().write(objectMapper.writeValueAsString(body));
                        }))
                .addFilterAfter(rateLimitingFilter, BearerTokenAuthenticationFilter.class)
                .build();
    }

    @Bean
    /**
     * Builds the JWT decoder from the shared HMAC secret.
     * @param jwtSecret configured secret source value
     * @return JWT decoder for bearer token validation
     */
    public JwtDecoder jwtDecoder(@Value("${app.security.jwt-secret}") String jwtSecret) {
        byte[] secret = Arrays.copyOf(jwtSecret.getBytes(StandardCharsets.UTF_8), 32);
        SecretKeySpec key = new SecretKeySpec(secret, "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();
    }
}
