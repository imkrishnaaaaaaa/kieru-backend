package com.kieru.backend.util;

import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public final class SecurityUtil {

    private static final String ID_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Generates a random, URL-friendly string.
     * We use a loop instead of UUID to get a short, pretty string (e.g. "x9As2k")
     * instead of a long ugly one ("a1b2-c3d4-e5f6...").
     */
    public String generateRandomId(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int randomIndex = SECURE_RANDOM.nextInt(ID_ALPHABET.length());
            char randomChar = ID_ALPHABET.charAt(randomIndex);
            sb.append(randomChar);
        }
        return sb.toString();
    }


    public String hashPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) return null;

        String salt = generateRandomId(10);
        return hashWithSalt(rawPassword, salt);
    }

    public boolean verifyPassword(String inputPassword, String storedHash) {
        if (storedHash == null) return true;
        if (inputPassword == null) return false;

        String[] parts = storedHash.split("\\$", 2);
        if (parts.length < 2) return false; // Corrupt data

        String originalSalt = parts[0];
        String checkHash = hashWithSalt(inputPassword, originalSalt);

        return checkHash.equals(storedHash);
    }

    // Hashes the password using SHA-256 (One-Way Hash). this CANNOT be reversed to find the original password.
    private String hashWithSalt(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = salt + password;
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            String encodedHash = Base64.getEncoder().encodeToString(hash);
            return salt + "$" + encodedHash;
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}