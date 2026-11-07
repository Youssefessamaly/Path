package com.internplatform.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.core.authority.mapping.SimpleAuthorityMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import jakarta.servlet.DispatcherType;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter
    ) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR)
                .permitAll()

                        .requestMatchers(
                        "/api/v1/auth/register/intern",
                        "/api/v1/auth/register/company",
                        "/api/v1/auth/login",
                        "/api/v1/auth/forgot-password",
                        "/api/v1/auth/verify-otp",
                        "/api/v1/auth/reset-password",
                        "/actuator/health",
                        "/actuator/info"
                ).permitAll()
                        // The skill catalog is a shared, non-sensitive reference list also needed
                        // when a COMPANY selects required skills for an internship — carve it out
                        // of the otherwise INTERN-only /api/v1/intern/** namespace.
                        .requestMatchers("/api/v1/intern/skills")
                        .hasAnyRole("INTERN", "COMPANY")

                        .requestMatchers("/api/v1/intern/**")
                        .hasRole("INTERN")

                        .requestMatchers("/api/v1/company/**")
                        .hasRole("COMPANY")

                        .requestMatchers("/api/v1/admin/**")
                        .hasRole("ADMIN")

                        .anyRequest()
                        .authenticated()
                )

                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt ->
                                jwt.jwtAuthenticationConverter(
                                        jwtAuthenticationConverter
                                )
                        )
                );

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {

        JwtGrantedAuthoritiesConverter authoritiesConverter =
                new JwtGrantedAuthoritiesConverter();

        authoritiesConverter.setAuthoritiesClaimName("role");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter authenticationConverter =
                new JwtAuthenticationConverter();

        authenticationConverter.setJwtGrantedAuthoritiesConverter(
                authoritiesConverter
        );

        return authenticationConverter;
    }

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${app.jwt.secret}") String encodedSecret
    ) {
        byte[] keyBytes = Base64.getDecoder().decode(encodedSecret);

        SecretKey secretKey = new SecretKeySpec(
                keyBytes,
                "HmacSHA256"
        );

        return NimbusJwtDecoder
                .withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * Allow the deployed company portal to call this API from a different HTTPS origin.
     * Configure additional origins as a comma-separated PATHORA_ALLOWED_ORIGINS variable.
     * Authorization is still enforced by the security filter chain below; CORS is not auth.
     */
    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource(
            @Value("${PATHORA_ALLOWED_ORIGINS:http://localhost:5174,http://localhost:5173,https://path-company.pages.dev}")
            String origins
    ) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(
                Arrays.stream(origins.split(","))
                        .map(String::trim)
                        .filter(origin -> !origin.isBlank())
                        .toList()
        );
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        );
        configuration.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "Accept")
        );
        configuration.setExposedHeaders(List.of("Content-Disposition"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}