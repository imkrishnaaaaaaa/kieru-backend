package com.kieru.backend.service;

import com.kieru.backend.dto.AuthResponse;
import com.kieru.backend.dto.LoginRequest;

public interface AuthService {
    /**
     * Verifies Firebase token, syncs user to DB, rotates session version.
     * @return User details + New Session Version
     */
    AuthResponse login(LoginRequest request);

    /**
     * Invalidates the session version in DB, effectively logging out ALL devices.
     */
    void logout(String userId);
}