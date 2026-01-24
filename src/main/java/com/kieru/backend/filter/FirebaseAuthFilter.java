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
import java.util.Map;
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

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7); // Remove "Bearer "

        try {
            // 1. Verify Token
            FirebaseToken decodedToken = firebaseAuth.verifyIdToken(token);
            String uid = decodedToken.getUid();
            String email = decodedToken.getEmail();
            String name = decodedToken.getName();
            String picture = decodedToken.getPicture();

            // 2. Extract Provider (google.com, github.com, anonymous)
            // Firebase puts this inside a map called "firebase" -> "sign_in_provider"
            Map<String, Object> firebaseClaims = (Map<String, Object>) decodedToken.getClaims().get("firebase");
            String signInProvider = (String) firebaseClaims.get("sign_in_provider");

            // 3. Determine Plan & Provider Enum
            KieruUtil.SubscriptionPlan plan;
            KieruUtil.LoginProvider providerEnum;

            if ("anonymous".equals(signInProvider) || email == null) {
                plan = KieruUtil.SubscriptionPlan.ANONYMOUS;
                providerEnum = KieruUtil.LoginProvider.UNKNOWN;
            }
            else {
                plan = KieruUtil.SubscriptionPlan.EXPLORER; // Default for new registered users
                if (signInProvider.contains("google")) providerEnum = KieruUtil.LoginProvider.GOOGLE;
                else if (signInProvider.contains("github")) providerEnum = KieruUtil.LoginProvider.GITHUB;
                else providerEnum = KieruUtil.LoginProvider.EMAIL;
            }

            // 4. Sync User to Database
            Optional<User> optionalUser = userRepository.findById(uid);
            User user;

            if (optionalUser.isEmpty()) {
                // --- CREATE NEW USER ---
                user = User.builder()
                        .id(uid)
                        .email(email)
                        .displayName(name != null ? name : "Anonymous User")
                        .photoUrl(picture)
                        .role(KieruUtil.UserRole.USER)
                        .joinedAt(Instant.now())
                        .lastLoginAt(Instant.now())
                        .secretsCreatedCount(0)
                        .isBanned(false)
                        .subscription(plan)
                        .loginProvider(providerEnum)
                        .build();

                userRepository.save(user);
            } else {
                // --- UPDATE EXISTING USER ---
                user = optionalUser.get();
                // If an anonymous user converts to Google, upgrade them
                if (user.getSubscription() == KieruUtil.SubscriptionPlan.ANONYMOUS && email != null) {
                    user.setEmail(email);
                    user.setDisplayName(name);
                    user.setPhotoUrl(picture);
                    user.setSubscription(KieruUtil.SubscriptionPlan.EXPLORER);
                    user.setLoginProvider(providerEnum);
                }
                user.setLastLoginAt(Instant.now());

                // Save async to not block the request
                final User userToSave = user;
                CompletableFuture.runAsync(() -> userRepository.save(userToSave));
            }

            // 5. Check Ban Status
            if (user.isBanned()) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Account Suspended");
                return;
            }

            // 6. Set Spring Security Context
            SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + user.getRole().name());
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    user, null, Collections.singletonList(authority)
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (FirebaseAuthException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid Token");
            return;
        } catch (Exception e) {
            // Catch DB errors (ConstraintViolation) and show them clearly
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Database Sync Error: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        filterChain.doFilter(request, response);
    }
}