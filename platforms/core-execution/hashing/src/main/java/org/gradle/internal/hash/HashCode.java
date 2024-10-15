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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serializable;
import java.math.BigInteger;

import static org.gradle.internal.hash.HashCode.Usage.CLONE_BYTES_IF_NECESSARY;
import static org.gradle.internal.hash.HashCode.Usage.SAFE_TO_REUSE_BYTES;

/**
 * An immutable hash code. Must be 4-255 bytes long.
 *
 * <h2>Memory considerations</h2>
 * <p>
 * Hashes by default are stored in {@link ByteArrayBackedHashCode a byte array}.
 * For a 128-bit hash this results in 64 bytes of memory used for each {@code HashCode}.
 * This implementation also requires GC to track two separate objects (the {@code HashCode} object and its {@code byte[]}).
 * <p>
 * Because Gradle uses a lot of MD5 hashes, for 128-bit hashes we have a more efficient implementation.
 * {@link HashCode128} uses two longs to store the bits of the hash, and does not need to cache the {@link #hashCode()} either.
 * This results in a memory footprint of 32 bytes.
 * Moreover, there is only one object for GC to keep track of.
 * <p>
 * Inspired by the <a href="https://github.com/google/guava">Google Guava project</a>.
 */
public abstract class HashCode implements Serializable, Comparable<HashCode> {
    private static final int MIN_NUMBER_OF_BYTES = 4;
    private static final int MAX_NUMBER_OF_BYTES = 255;
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    private HashCode() {
    }

    enum Usage {
        CLONE_BYTES_IF_NECESSARY,
        SAFE_TO_REUSE_BYTES
    }

    static HashCode fromBytes(byte[] bytes, Usage usage) {
        switch (bytes.length) {
            case 16:
                return new HashCode128(
                    bytesToLong(bytes, 0),
                    bytesToLong(bytes, 8)
                );
            default:
                return new ByteArrayBackedHashCode(usage == CLONE_BYTES_IF_NECESSARY
                    ? bytes.clone()
                    : bytes);
        }
    }

    public static HashCode fromBytes(byte[] bytes) {
        // Make sure hash codes are serializable with a single byte length
        if (bytes.length < MIN_NUMBER_OF_BYTES || bytes.length > MAX_NUMBER_OF_BYTES) {
            throw new IllegalArgumentException(String.format("Invalid hash code length: %d bytes", bytes.length));
        }
        return fromBytes(bytes, CLONE_BYTES_IF_NECESSARY);
    }

    /**
     * Decodes the hash code from a string.
     * <p>
     * A corresponding operation for encoding/decoding is {@link #toString()}:
     * <pre>{@code
     *      assertEquals(hash, HashCode.fromString(hash.toString()))
     * }</pre>
     * <p>
     * This method does not work with {@link #toCompactString()}.
     */
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

        return fromBytes(bytes, SAFE_TO_REUSE_BYTES);
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

    /**
     * Encodes the hash code into a hex string (base-16).
     * <p>
     * A corresponding operation for encoding/decoding is {@link #fromString(String)}:
     * <pre>{@code
     *      assertEquals(hash, HashCode.fromString(hash.toString()))
     * }</pre>
     */
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

    /**
     * Encodes the hash code into a base-36 string,
     * which yields shorter strings than {@link #toString()}.
     * <p>
     * The compact string retains all the information of the hash.
     * However, it is not intended to be parsed with {@link #fromString(String)}.
     * <p>
     * For encoding/decoding use {@link #toString()}.
     */
    public String toCompactString() {
        return new BigInteger(1, bytes()).toString(36);
    }

    // Package private accessor used by MessageDigestHasher.putHash()
    abstract void appendToHasher(PrimitiveHasher hasher);

    abstract byte[] bytes();

    @VisibleForTesting
    static class HashCode128 extends HashCode {
        private final long bits1;
        private final long bits2;

        public HashCode128(long bits1, long bits2) {
            this.bits1 = bits1;
            this.bits2 = bits2;
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

    private static class ByteArrayBackedHashCode extends HashCode {
        private final byte[] bytes;

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
        return (bytes[offset] & 0xFFL)
            | ((bytes[offset + 1] & 0xFFL) << 8)
            | ((bytes[offset + 2] & 0xFFL) << 16)
            | ((bytes[offset + 3] & 0xFFL) << 24)
            | ((bytes[offset + 4] & 0xFFL) << 32)
            | ((bytes[offset + 5] & 0xFFL) << 40)
            | ((bytes[offset + 6] & 0xFFL) << 48)
            | ((bytes[offset + 7] & 0xFFL) << 56);
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
