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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final FirebaseAuth firebaseAuth;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request, String ip) {
        log.info("Auth Service :: Login attempt from IP: {}", ip);

        try {
            FirebaseToken decodedToken = firebaseAuth.verifyIdToken(request.getFirebaseToken());

            String uid = decodedToken.getUid();
            String email = decodedToken.getEmail();
            String name = decodedToken.getName() != null ? decodedToken.getName() : "User";
            String picture = decodedToken.getPicture();

            log.debug("Auth Service :: Firebase token verified for UID: {}, Email: {}", uid, email);

//            if (email == null || email.isBlank()) {
//                log.error("Login failed: Email is null for UID: {}", uid);
//                throw new RuntimeException("Email is required");
//            }

            KieruUtil.LoginProvider provider = KieruUtil.LoginProvider.UNKNOWN;
            Map<String, Object> claims = decodedToken.getClaims();
            Object firebaseClaim = claims.get("firebase");

            if (firebaseClaim instanceof Map<?, ?> firebaseMap) {
                Object signInProvider = firebaseMap.get("sign_in_provider");

                if (signInProvider instanceof String providerStr) {
                    provider = KieruUtil.LoginProvider.fromFirebaseProvider(providerStr);
                }
            }

            boolean isNewUser = false;
            User user = userRepository.findById(uid).orElseGet(() -> {
                log.info("Auth Service :: Creating new user for UID: {}", uid);
                return User.builder()
                        .id(uid)
                        .email(email)
                        .displayName(name)
                        .photoUrl(picture)
                        .role(KieruUtil.UserRole.USER)
                        .subscription(KieruUtil.SubscriptionPlan.EXPLORER)
                        .isBanned(false)
                        .secretsCreatedCount(0)
                        .joinedAt(Instant.now())
                        .build();
            });

            if (user.isBanned()) {
                log.warn("Auth Service :: Login blocked: User is banned. UID: {}", uid);
                throw new RuntimeException("Auth Service :: User account is banned");
            }

            if (user.getRole() == null) {
                user.setRole(KieruUtil.UserRole.USER);
            }

            user.setDisplayName(name);
            user.setPhotoUrl(picture);
            user.setLastLoginAt(Instant.now());
            user.setLastLoginIp(ip);
            user.setLoginProvider(provider);

            String newSessionVersion = UUID.randomUUID().toString();
            user.setSessionVersion(newSessionVersion);

            userRepository.save(user);

            log.info("Auth Service :: Login successful for UID: {}, Provider: {}, NewUser: {}", uid, provider, isNewUser);

            return AuthResponse.builder()
                    .userId(user.getId())
                    .email(user.getEmail())
                    .displayName(user.getDisplayName())
                    .photoUrl(user.getPhotoUrl())
                    .role(user.getRole().name())
                    .subscription(user.getSubscription().name())
                    .loginProvider(provider.name())
                    .sessionVersion(newSessionVersion)
                    .build();

        }
        catch (FirebaseAuthException e) {
            log.error("Auth Service :: Firebase token verification failed from IP: {}", ip, e);
            throw new RuntimeException("Auth Service :: Invalid Firebase token", e);
        }
    }

    @Override
    public void logout(String userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("Auth Service :: Logout failed: User not found: {}", userId);
                    return new RuntimeException("Auth Service :: User not found");
                });

        String newSessionVersion = UUID.randomUUID().toString();
        user.setSessionVersion(newSessionVersion);
        userRepository.save(user);

        log.info("Auth Service :: Logout successful for user: {}", userId);
    }
}