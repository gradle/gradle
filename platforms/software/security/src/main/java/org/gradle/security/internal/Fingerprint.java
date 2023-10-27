/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.security.internal;

import org.bouncycastle.openpgp.PGPPublicKey;

import java.util.Arrays;

public class Fingerprint {
    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    private final byte[] fingerprint;
    private final int hashCode;

    public static Fingerprint of(PGPPublicKey key) {
        return new Fingerprint(key.getFingerprint());
    }

    public static Fingerprint wrap(byte[] fingerprint) {
        return new Fingerprint(fingerprint);
    }

    public static Fingerprint fromString(String hexString) {
        int length = hexString.length();
        if (length % 2 == 1) {
            throw new IllegalStateException("Unexpected hex string length: " + length);
        }
        int len = length / 2;
        byte[] result = new byte[len];
        for (int i = 0; i < len; i++) {
            int hi = decode(hexString.charAt(2 * i)) << 4;
            int lo = decode(hexString.charAt(2 * i + 1));
            result[i] = (byte) (hi + lo);
        }
        return new Fingerprint(result);
    }

    private static int decode(char ch) {
        if (ch >= '0' && ch <= '9') {
            return ch - '0';
        }
        if (ch >= 'a' && ch <= 'f') {
            return ch - 'a' + 10;
        }
        if (ch >= 'A' && ch <= 'F') {
            return ch - 'A' + 10;
        }
        throw new IllegalArgumentException("Illegal hexadecimal character: " + ch);
    }

    private Fingerprint(byte[] fingerprint) {
        this.fingerprint = fingerprint;
        this.hashCode = Arrays.hashCode(fingerprint);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(2 * fingerprint.length);
        for (byte b : fingerprint) {
            sb.append(HEX_DIGITS[(b >> 4) & 0xf]).append(HEX_DIGITS[b & 0xf]);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Fingerprint that = (Fingerprint) o;

        return Arrays.equals(fingerprint, that.fingerprint);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    public byte[] getBytes() {
        return fingerprint;
    }
}
