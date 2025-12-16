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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final FirebaseAuth firebaseAuth;

    private final UserRepository userRepository;
    // private final SecurityUtil securityUtil; // Helper for random IDs if needed

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        try {
            // 1. Verify Token with Google
            FirebaseToken decodedToken = firebaseAuth.verifyIdToken(request.getFirebaseToken());
            String uid = decodedToken.getUid();
            String email = decodedToken.getEmail();
            String name = decodedToken.getName() != null ? decodedToken.getName() : "User";
            String picture = decodedToken.getPicture();

            // 2. Sync User (The Mirror)
            User user = userRepository.findById(uid).orElseGet(() -> {
                // First time user! Create account.
                return User.builder()
                        .id(uid) // EXPLICITLY set ID to match Firebase
                        .email(email)
                        .displayName(name)
                        .photoUrl(picture)
                        .role(KieruUtil.UserRole.USER)
                        .joinedAt(Instant.now())
                        .secretsCreatedCount(0)
                        .isBanned(false)
                        .build();
            });

            // 3. Update Sync Data
            // We always update name/photo in case they changed it on Google
            user.setDisplayName(name);
            user.setPhotoUrl(picture);
            user.setLastLoginAt(Instant.now());

            // 4. ROTATE SESSION VERSION (The Highlander Rule)
            // This invalidates any previous sessions on other devices
            String newSessionVersion = UUID.randomUUID().toString();
            user.setSessionVersion(newSessionVersion);

            userRepository.save(user);

            // 5. Return Response
            return AuthResponse.builder()
                    .userId(user.getId())
                    .email(user.getEmail())
                    .displayName(user.getDisplayName())
                    .role(user.getRole().name()) // Enum to String
                    .sessionVersion(newSessionVersion)
                    .build();

        } catch (FirebaseAuthException e) {
            throw new RuntimeException("Invalid Login: " + e.getMessage());
        }
    }

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