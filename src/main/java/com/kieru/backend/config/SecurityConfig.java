package com.kieru.backend.config;

import com.kieru.backend.filter.FirebaseAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final FirebaseAuthFilter firebaseAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. DISABLE CSRF (Cross-Site Request Forgery)
                // Why: CSRF is needed for browser sessions (Cookies).
                // We use JWTs (Headers), so this attack is impossible. We disable it to simplify.
                .csrf(AbstractHttpConfigurer::disable)

                // 2. ENABLE CORS (Cross-Origin Resource Sharing)
                // Why: Your React app runs on localhost:3000. Spring runs on localhost:8080.
                // Browsers block this by default unless we say "Allow localhost:3000".
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 3. STATELESS SESSION
                // Why: We don't want Spring to create a JSESSIONID cookie.
                // We want every request to be authenticated via the Token Header.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 4. THE RULES (Public vs Private)
                .authorizeHttpRequests(auth -> auth
                        // PUBLIC ENDPOINTS (No Login Required)
                        .requestMatchers(HttpMethod.POST, "/api/secrets").permitAll()       // Create Secret
                        .requestMatchers(HttpMethod.POST, "/api/secrets/*/access").permitAll() // View Secret
                        .requestMatchers("/api/auth/**").permitAll()                        // Login/Logout logic

                        // PRIVATE ENDPOINTS (Login Required)
                        .requestMatchers("/api/dashboard/**").authenticated()               // Dashboard
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")                  // Admin Stats

                        // CATCH-ALL: Everything else needs login
                        .anyRequest().authenticated()
                )

                // 5. REGISTER THE BOUNCER
                // "Add our Firebase Filter BEFORE the standard Spring Login Filter runs."
                .addFilterBefore(firebaseAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // CORS CONFIGURATION (Allow React Frontend)
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Allow your frontend (Adjust port if needed)
        configuration.setAllowedOrigins(List.of("http://localhost:3000", "http://localhost:5173"));

        // Allow standard HTTP methods
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // Allow Auth headers
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-ID"));

        // Allow credentials (if we ever use cookies later)
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}