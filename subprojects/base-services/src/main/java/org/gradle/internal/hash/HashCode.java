/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.hash;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serializable;

/**
 * An immutable hash code. Must be 4-255 bytes long.
 * Inspired by the Google Guava project – https://github.com/google/guava.
 */
public class HashCode implements Serializable, Comparable<HashCode> {
    private static final int MIN_NUMBER_OF_BYTES = 4;
    private static final int MAX_NUMBER_OF_BYTES = 255;
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    private final byte[] bytes;

    private HashCode(byte[] bytes) {
        this.bytes = bytes;
    }

    static HashCode fromBytesNoCopy(byte[] bytes) {
        return new HashCode(bytes);
    }

    public static HashCode fromBytes(byte[] bytes) {
        // Make sure hash codes are serializable with a single byte length
        if (bytes.length < MIN_NUMBER_OF_BYTES || bytes.length > MAX_NUMBER_OF_BYTES) {
            throw new IllegalArgumentException(String.format("Invalid hash code length: %d bytes", bytes.length));
        }
        return fromBytesNoCopy(bytes.clone());
    }

    public static HashCode fromInt(int value) {
        return fromBytesNoCopy(new byte[] {
            (byte) (value >> 24),
            (byte) (value >> 16),
            (byte) (value >> 8),
            (byte) value
        });
    }

    public static HashCode fromString(String string) {
        int length = string.length();

        if (length % 2 != 0
            || length < MIN_NUMBER_OF_BYTES * 2
            || length > MAX_NUMBER_OF_BYTES * 2) {
            throw new IllegalArgumentException(String.format("Invalid hash code length: %d characters", length));
        }

        byte[] bytes = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            int ch1 = decode(string.charAt(i)) << 4;
            int ch2 = decode(string.charAt(i + 1));
            bytes[i / 2] = (byte) (ch1 + ch2);
        }

        return fromBytesNoCopy(bytes);
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

    public int length() {
        return bytes.length;
    }

    public byte[] toByteArray() {
        return bytes.clone();
    }

    @Override
    public int hashCode() {
        return (bytes[0] & 0xFF)
            | ((bytes[1] & 0xFF) << 8)
            | ((bytes[2] & 0xFF) << 16)
            | ((bytes[3] & 0xFF) << 24);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj == null || obj.getClass() != HashCode.class) {
            return false;
        }

        byte[] a = bytes;
        byte[] b = ((HashCode) obj).bytes;
        int length = a.length;

        if (b.length != length) {
            return false;
        }

        for (int i = 0; i < length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }

        return true;
    }

    @Override
    public int compareTo(@Nonnull HashCode o) {
        byte[] bytes2 = o.bytes;
        int result;
        int len1 = bytes.length;
        int len2 = bytes2.length;
        int length = Math.min(len1, len2);
        for (int idx = 0; idx < length; idx++) {
            result = bytes[idx] - bytes2[idx];
            if (result != 0) {
                return result;
            }
        }
        return len1 - len2;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            sb.append(HEX_DIGITS[(b >> 4) & 0xf]).append(HEX_DIGITS[b & 0xf]);
        }
        return sb.toString();
    }

    // Package private accessor used by MessageDigestHasher.putHash for performance reasons
    byte[] getBytes() {
        return bytes;
    }
}
