package org.gradle.api.changedetection.digest;

import org.gradle.api.GradleException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Creates MessageDigest objects for the SHA algorithm.
 *
 * @author Tom Eyckmans
 */
class ShaDigesterFactory implements DigesterFactory {
    private static final String SHA_ALGORITHM = "SHA-1";

    /**
     * Calls {@see MessageDigest.getInstance} for algorithm 'SHA'.
     *  
     * @return The created MessageDigest object.
     */
    public MessageDigest createDigester() {
        try {
            return MessageDigest.getInstance(SHA_ALGORITHM);
        }
        catch ( NoSuchAlgorithmException e) {
            throw new GradleException(SHA_ALGORITHM + " algorithm not supported!", e);
        }
    }
}
