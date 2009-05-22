package org.gradle.api.changedetection.digest;

import org.apache.commons.codec.binary.Hex;

/**
 * @author Tom Eyckmans
 */
public class DigestStringUtil {
    public static String digestToHexString(byte[] digest) {
        if ( digest == null || digest.length == 0 ) throw new IllegalArgumentException("digest is empty!");
        return String.valueOf(Hex.encodeHex(digest));
    }
}
