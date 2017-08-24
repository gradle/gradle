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

import java.io.Serializable;
import java.util.Arrays;

public class HashCode implements Serializable {
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
        if (bytes.length > MAX_NUMBER_OF_BYTES) {
            throw new IllegalArgumentException("Invalid hash code length: " + bytes.length);
        }
        return fromBytesNoCopy(copyBytes(bytes));
    }

    public static HashCode fromInt(int value) {
        return fromBytesNoCopy(new byte[] {
            (byte) value,
            (byte) (value >> 8),
            (byte) (value >> 16),
            (byte) (value >> 24)
        });
    }

    public static HashCode fromLong(long value) {
        return fromBytesNoCopy(new byte[] {
            (byte) value,
            (byte) (value >> 8),
            (byte) (value >> 16),
            (byte) (value >> 24),
            (byte) (value >> 32),
            (byte) (value >> 40),
            (byte) (value >> 48),
            (byte) (value >> 56)
        });
    }

    public void writeTo(byte[] dest, int offset) {
        if (dest.length - offset < bytes.length) {
            throw new IllegalArgumentException("Not enough space in destination array");
        }
        System.arraycopy(bytes, 0, dest, offset, bytes.length);
    }

    public static HashCode fromString(String string) {
        if (!(string.length() >= 2)) {
          throw new IllegalArgumentException(String.format("input string (%s) must have at least 2 characters", string));
        }
        if (!(string.length() % 2 == 0)) {
          throw new IllegalArgumentException(String.format("input string (%s) must have an even number of characters", string));
        }
        if (string.length() > MAX_NUMBER_OF_BYTES * 2) {
            throw new IllegalArgumentException(String.format("input string (%s) is too long", string));
        }

        byte[] bytes = new byte[string.length() / 2];
        for (int i = 0; i < string.length(); i += 2) {
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
        throw new IllegalArgumentException("Illegal hexadecimal character: " + ch);
    }

    public int bits() {
        return bytes.length * 8;
    }

    public byte[] asBytes() {
        return copyBytes(bytes);
    }

    private static byte[] copyBytes(byte[] bytes) {
        return bytes.clone();
    }

    @Override
    public int hashCode() {
        int val = (bytes[0] & 0xFF);
        for (int i = 1; i < bytes.length; i++) {
            val |= ((bytes[i] & 0xFF) << (i * 8));
        }
        return val;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj.getClass() != HashCode.class) {
            return false;
        }
        return Arrays.equals(bytes, ((HashCode) obj).bytes);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            sb.append(HEX_DIGITS[(b >> 4) & 0xf]).append(HEX_DIGITS[b & 0xf]);
        }
        return sb.toString();
    }
}
