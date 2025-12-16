package com.kieru.backend.filter;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.kieru.backend.entity.User;
import com.kieru.backend.repository.UserRepository;
import com.kieru.backend.util.KieruUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class FirebaseAuthFilter extends OncePerRequestFilter {

    private final FirebaseAuth firebaseAuth;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Get the Token from Header
        String header = request.getHeader("Authorization");

        // Skip filter if no token (Public endpoints handling left to SecurityConfig)
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7); // Remove "Bearer "

        try {
            // 1. Verify with Google
            FirebaseToken decodedToken = firebaseAuth.verifyIdToken(token);
            String uid = decodedToken.getUid();
            String email = decodedToken.getEmail();
            String name = decodedToken.getName(); // Might be null
            String picture = decodedToken.getPicture(); // Might be null

            // 3. The "Mirror" Logic (Account Creation / Sync)
            // We check if this user exists in OUR database.
            Optional<User> optionalUser = userRepository.findById(uid);
            User user;

            if (optionalUser.isEmpty()) {
                // Auto-Create Account
                user = User.builder()
                        .id(uid)
                        .email(email)
                        .displayName(name != null ? decodedToken.getName() : "User")
                        .photoUrl(picture)
                        .role(KieruUtil.UserRole.USER)
                        .joinedAt(Instant.now())
                        .lastLoginAt(Instant.now())
                        .secretsCreatedCount(0)
                        .isBanned(false)
                        .build();
                userRepository.save(user);
            }
            else {
                // --- UPDATE LOGIN STATS ---
                user = optionalUser.get();
                user.setLastLoginAt(Instant.now());
                CompletableFuture.runAsync(() -> userRepository.save(user));
            }

            // 3. Ban Check
            if (user.isBanned()) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Account Suspended");
                return;
            }

            // 5. Tell Spring Security: "This user is Valid"
            // We map our Enum Role to a Spring Authority ("ROLE_USER")
            SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + user.getRole().name());

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    user, // Principal (The User Object)
                    null, // Credentials (None, verified by Token)
                    Collections.singletonList(authority) // Authorities (Roles)
            );

            // Set the context! Now Controllers can access 'user'.
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (FirebaseAuthException e) {
            // Token is invalid/expired
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid Firebase Token: " + e.getMessage());
            return;
        }

        // Continue the chain
        filterChain.doFilter(request, response);
    }
}