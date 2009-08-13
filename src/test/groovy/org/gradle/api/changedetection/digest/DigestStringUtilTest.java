/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
