package org.gradle.api.changedetection.digest;

import java.security.MessageDigest;

/**
 * 
 * @author Tom Eyckmans
 */
public interface DigesterCache {
    MessageDigest getDigester(String digesterId);

    DigesterFactory getDigesterFactory();
}
