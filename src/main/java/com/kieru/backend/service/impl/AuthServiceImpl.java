package com.kieru.backend.service.impl;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.kieru.backend.dto.AuthResponse;
import com.kieru.backend.dto.LoginRequest;
import com.kieru.backend.entity.User;
import com.kieru.backend.repository.UserRepository;
import com.kieru.backend.service.AuthService;
import com.kieru.backend.util.KieruUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final FirebaseAuth firebaseAuth;
    private final UserRepository userRepository;

    /**
     * LOGIN / SIGNUP (Unified)
     * - Verifies Firebase token
     * - Syncs user into backend DB
     * - Enforces bans
     * - Rotates session version
     */
    @Override
    @Transactional
    public AuthResponse login(LoginRequest request, String ip) {

        try {
            /* ------------------------------------------------------------------
             * 1. VERIFY FIREBASE TOKEN (Identity Proof)
             * ------------------------------------------------------------------ */
            FirebaseToken decodedToken = firebaseAuth.verifyIdToken(request.getFirebaseToken());

            String uid = decodedToken.getUid();
            String email = decodedToken.getEmail();
            String name = decodedToken.getName() != null ? decodedToken.getName() : "User";
            String picture = decodedToken.getPicture();

            /* ------------------------------------------------------------------
             * 2. DETERMINE LOGIN PROVIDER
             * ------------------------------------------------------------------ */
            KieruUtil.LoginProvider provider = KieruUtil.LoginProvider.UNKNOWN;
            Map<String, Object> claims = decodedToken.getClaims();
            Object firebaseClaim = claims.get("firebase");

            if (firebaseClaim instanceof Map<?, ?> firebaseMap) {
                Object signInProvider = firebaseMap.get("sign_in_provider");

                if (signInProvider instanceof String providerStr) {
                    provider = KieruUtil.LoginProvider.fromFirebaseProvider(providerStr);
                }
            }

            /* ------------------------------------------------------------------
             * 3. LOAD OR CREATE USER (Mirror Pattern)
             * ------------------------------------------------------------------ */
            User user = userRepository.findById(uid).orElseGet(() ->
                    User.builder()
                            .id(uid)                       // Firebase UID
                            .email(email)
                            .displayName(name)
                            .photoUrl(picture)
                            .role(KieruUtil.UserRole.USER) // Default role
                            .subscription(KieruUtil.SubscriptionPlan.EXPLORER)
                            .isBanned(false)
                            .secretsCreatedCount(0)
                            .joinedAt(Instant.now())
                            .build()
            );

            /* ------------------------------------------------------------------
             * 4. ENFORCE BUSINESS RULES
             * ------------------------------------------------------------------ */
            if (user.isBanned()) {
                throw new RuntimeException("User account is banned");
            }

            // (Future-safe) Role sanity check
            if (user.getRole() == null) {
                user.setRole(KieruUtil.UserRole.USER);
            }

            /* ------------------------------------------------------------------
             * 5. SYNC MUTABLE PROFILE DATA
             * ------------------------------------------------------------------ */
            user.setDisplayName(name);
            user.setPhotoUrl(picture);
            user.setLastLoginAt(Instant.now());
            user.setLastLoginIp(ip);
            user.setLoginProvider(provider);

            /* ------------------------------------------------------------------
             * 6. ROTATE SESSION VERSION (Single Active Session Rule)
             * ------------------------------------------------------------------ */
            String newSessionVersion = UUID.randomUUID().toString();
            user.setSessionVersion(newSessionVersion);

            userRepository.save(user);

            /* ------------------------------------------------------------------
             * 7. BUILD RESPONSE
             * ------------------------------------------------------------------ */
            return AuthResponse.builder()
                    .userId(user.getId())
                    .email(user.getEmail())
                    .displayName(user.getDisplayName())
                    .role(user.getRole().name())
                    .subscription(user.getSubscription().name())
                    .loginProvider(provider.name())
                    .sessionVersion(newSessionVersion)
                    .build();

        }
        catch (FirebaseAuthException e) {
            throw new RuntimeException("Invalid Firebase token", e);
        }
    }

    /**
     * LOGOUT
     * - Invalidates current session
     */
    @Override
    @Transactional
    public void logout(String userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Kill the session by clearing or rotating the version
        user.setSessionVersion(null);
        userRepository.save(user);
    }
}