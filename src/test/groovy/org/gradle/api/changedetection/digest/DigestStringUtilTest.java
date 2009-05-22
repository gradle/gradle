package org.gradle.api.changedetection.digest;

import org.junit.Test;
import static org.junit.Assert.*;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * @author Tom Eyckmans
 */
public class DigestStringUtilTest {
    @Test (expected = IllegalArgumentException.class)
    public void digestToHexNullBytes() {
        DigestStringUtil.digestToHexString(null);
    }

    @Test (expected = IllegalArgumentException.class)
    public void digestToHexEmptyBytes() {
        DigestStringUtil.digestToHexString(new byte[]{});
    }

    @Test
    public void digestToHex() throws NoSuchAlgorithmException {
        final byte[] okBytes = MessageDigest.getInstance("SHA-1").digest("okBytes".getBytes());
        final String okBytesInHex = DigestStringUtil.digestToHexString(okBytes);
        assertNotNull(okBytesInHex);
        assertTrue(okBytesInHex.length() != 0);
    }

}
