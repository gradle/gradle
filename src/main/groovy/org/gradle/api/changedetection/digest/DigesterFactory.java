package org.gradle.api.changedetection.digest;

import java.security.MessageDigest;

/**
 * Factory for MessageDigest instances.
 *
 * @author Tom Eyckmans
 */
public interface DigesterFactory {
    /**
     * Create a MessageDigest object.
     * 
     * @return The created MessageDigest object.
     */
    MessageDigest createDigester();
}
