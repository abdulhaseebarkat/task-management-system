package com.slmtires.itms.security;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates a random, human-typeable temporary password for newly created
 * accounts. Never reused, never stored anywhere in plaintext once hashed.
 */
@Component
public class TemporaryPasswordGenerator {

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";
    private static final int LENGTH = 14;
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
