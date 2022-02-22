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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serializable;
import java.math.BigInteger;

/**
 * An immutable hash code. Must be 4-255 bytes long.
 * Inspired by the Google Guava project – https://github.com/google/guava.
 */
public abstract class HashCode implements Serializable, Comparable<HashCode> {
    private static final int MIN_NUMBER_OF_BYTES = 4;
    private static final int MAX_NUMBER_OF_BYTES = 255;
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    private HashCode() {
    }

    static HashCode fromBytesNoCopy(byte[] bytes) {
        switch (bytes.length) {
            case 16:
                return new HashCode128(bytes);
            default:
                return new ByteArrayBackedHashCode(bytes);
        }
    }

    public static HashCode fromBytes(byte[] bytes) {
        // Make sure hash codes are serializable with a single byte length
        if (bytes.length < MIN_NUMBER_OF_BYTES || bytes.length > MAX_NUMBER_OF_BYTES) {
            throw new IllegalArgumentException(String.format("Invalid hash code length: %d bytes", bytes.length));
        }
        return fromBytesNoCopy(bytes.clone());
    }

    @VisibleForTesting
    public static HashCode fromInt(int value) {
        byte[] bytes = Ints.toByteArray(value); // Big-endian
        return fromBytesNoCopy(bytes);
    }

    @VisibleForTesting
    public static HashCode fromLong(long value) {
        byte[] bytes = Longs.toByteArray(value); // Big-endian
        return fromBytesNoCopy(bytes);
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

    public abstract int length();

    public abstract byte[] toByteArray();

    @Override
    public abstract int hashCode();

    @Override
    public abstract boolean equals(@Nullable Object obj);

    @Override
    public String toString() {
        StringBuilder sb = toStringBuilder(2 * length(), bytes());
        return sb.toString();
    }

    public String toZeroPaddedString(int length) {
        StringBuilder sb = toStringBuilder(length, bytes());
        while (sb.length() < length) {
            sb.insert(0, '0');
        }
        return sb.toString();
    }

    private static StringBuilder toStringBuilder(int capacity, byte[] bytes) {
        StringBuilder sb = new StringBuilder(capacity);
        for (byte b : bytes) {
            sb.append(HEX_DIGITS[(b >> 4) & 0xf]).append(HEX_DIGITS[b & 0xf]);
        }
        return sb;
    }

    public String toCompactString() {
        return new BigInteger(1, bytes()).toString(36);
    }

    // Package private accessor used by MessageDigestHasher.putHash()
    abstract void appendToHasher(PrimitiveHasher hasher);

    abstract byte[] bytes();

    static class HashCode128 extends HashCode {
        private final long bits1;
        private final long bits2;

        public HashCode128(byte[] bytes) {
            if (bytes.length != 16) {
                throw new IllegalArgumentException("Must be 32 bytes");
            }
            this.bits1 = bytesToLong(bytes, 0);
            this.bits2 = bytesToLong(bytes, 8);
        }

        @Override
        public int length() {
            return 16;
        }

        @Override
        byte[] bytes() {
            return toByteArray();
        }

        @Override
        public byte[] toByteArray() {
            byte[] bytes = new byte[16];
            longToBytes(bits1, bytes, 0);
            longToBytes(bits2, bytes, 8);
            return bytes;
        }

        @Override
        void appendToHasher(PrimitiveHasher hasher) {
            hasher.putLong(bits1);
            hasher.putLong(bits2);
        }

        @Override
        public int hashCode() {
            return (int) bits1;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || o.getClass() != HashCode128.class) {
                return false;
            }

            HashCode128 other = (HashCode128) o;

            return bits1 == other.bits1 && bits2 == other.bits2;
        }

        @Override
        public int compareTo(HashCode o) {
            if (o.getClass() != HashCode128.class) {
                return HashCode.compareBytes(bytes(), o.bytes());
            }

            HashCode128 other = (HashCode128) o;

            int result = compareLong(bits1, other.bits1);
            if (result == 0) {
                result = compareLong(bits2, other.bits2);
            }

            return result;
        }
    }

    static class ByteArrayBackedHashCode extends HashCode {
        private final byte[] bytes;
        private long hashCode;

        public ByteArrayBackedHashCode(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public int length() {
            return bytes.length;
        }

        @Override
        byte[] bytes() {
            return bytes;
        }

        @Override
        public byte[] toByteArray() {
            return bytes.clone();
        }

        @Override
        void appendToHasher(PrimitiveHasher hasher) {
            hasher.putBytes(bytes);
        }

        @Override
        public int hashCode() {
            if (hashCode == 0) {
                hashCode = (bytes[0] & 0xFF)
                    | ((bytes[1] & 0xFF) << 8)
                    | ((bytes[2] & 0xFF) << 16)
                    | ((bytes[3] & 0xFF) << 24)
                    // Make sure it's always > 0 but without affecting the lower 32 bits
                    | (1L << 32);
            }
            return (int) hashCode;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (obj == this) {
                return true;
            }

            if (obj == null || obj.getClass() != ByteArrayBackedHashCode.class) {
                return false;
            }

            byte[] a = bytes;
            byte[] b = ((ByteArrayBackedHashCode) obj).bytes;
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
            return compareBytes(bytes, o.bytes());
        }
    }

    // TODO Replace with Long.compare() after migrating off of Java 6
    private static int compareLong(long a, long b) {
        return (a < b) ? -1 : ((a == b) ? 0 : 1);
    }

    private static int compareBytes(byte[] a, byte[] b) {
        int result;
        int len1 = a.length;
        int len2 = b.length;
        int length = Math.min(len1, len2);
        for (int idx = 0; idx < length; idx++) {
            result = a[idx] - b[idx];
            if (result != 0) {
                return result;
            }
        }
        return len1 - len2;
    }


    private static long bytesToLong(byte[] bytes, int offset) {
        return (long) (bytes[offset] & 0xFF)
            | (long) (bytes[offset + 1] & 0xFF) << 8
            | (long) (bytes[offset + 2] & 0xFF) << 16
            | (long) (bytes[offset + 3] & 0xFF) << 24
            | (long) (bytes[offset + 4] & 0xFF) << 32
            | (long) (bytes[offset + 5] & 0xFF) << 40
            | (long) (bytes[offset + 6] & 0xFF) << 48
            | (long) (bytes[offset + 7] & 0xFF) << 56;
    }

    private static void longToBytes(long value, byte[] bytes, int offset) {
        bytes[offset] = (byte) (value & 0xFF);
        bytes[offset + 1] = (byte) ((value >>> 8) & 0xFF);
        bytes[offset + 2] = (byte) ((value >>> 16) & 0xFF);
        bytes[offset + 3] = (byte) ((value >>> 24) & 0xFF);
        bytes[offset + 4] = (byte) ((value >>> 32) & 0xFF);
        bytes[offset + 5] = (byte) ((value >>> 40) & 0xFF);
        bytes[offset + 6] = (byte) ((value >>> 48) & 0xFF);
        bytes[offset + 7] = (byte) ((value >>> 56) & 0xFF);
    }
}
