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

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;

/**
 * An immutable hash code. Must be 8-127 bytes (64-1016 bits) long.
 * Inspired by the Google Guava project – https://github.com/google/guava.
 */
public abstract class HashCode implements Serializable, Comparable<HashCode> {
    private static final int MIN_NUMBER_OF_BYTES = Ints.BYTES; // TODO Long.BYTES;
    private static final int MAX_NUMBER_OF_BYTES = Byte.MAX_VALUE;
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    private static final HashCode EMPTY = fromLongs(0);


    protected final int hashCode;

    protected HashCode(int hashCode) {
        this.hashCode = hashCode;
    }

    public static HashCode fromLongs(long... values) {
        if (values.length == 2) {
            int hashCode = Arrays.hashCode(values);
            return new HashCode16(hashCode, values);
        } else {
            return fromBytes(longsToBytes(values));
        }
    }

    public static HashCode fromBytes(byte... values) {
        // Make sure hash codes are serializable with a single byte length
        if (values.length < MIN_NUMBER_OF_BYTES || values.length > MAX_NUMBER_OF_BYTES) {
            throw new IllegalArgumentException(String.format("Invalid hash code length: %d bytes", values.length));
        }

        int hashCode = Arrays.hashCode(values);
        if (values.length == 16) {
            return new HashCode16(hashCode, values);
        } else {
            return new HashCodeN(hashCode, values);
        }
    }

    public static HashCode empty() {
        return EMPTY;
    }

    public static HashCode fromInt(int value) {
        byte[] bytes = Ints.toByteArray(value); // Big-endian
        return fromBytes(bytes);
    }

    public static HashCode fromString(String value) {
        int length = value.length();

        if (length % 2 != 0
            || length < MIN_NUMBER_OF_BYTES * 2
            || length > MAX_NUMBER_OF_BYTES * 2) {
            throw new IllegalArgumentException(String.format("Invalid hash code length: %d characters", length));
        }

        byte[] bytes = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            int ch1 = decode(value.charAt(i)) << 4;
            int ch2 = decode(value.charAt(i + 1));
            bytes[i / 2] = (byte) (ch1 + ch2);
        }

        return fromBytes(bytes);
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

    @Override
    public int compareTo(HashCode that) {
        return Integer.compare(hashCode, that.hashCode);
    }

    @Override
    public abstract boolean equals(@Nullable Object obj);

    @Override
    public abstract int hashCode();

    public abstract int length();

    public abstract byte[] toByteArray();

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(2 * length());
        for (byte b : toByteArray()) {
            sb.append(HEX_DIGITS[(b >> 4) & 0xf])
                .append(HEX_DIGITS[b & 0xf]);
        }
        return sb.toString();
    }

    static byte[] longsToBytes(long... values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.length * Long.BYTES);
        for (long value : values) {
            buffer.putLong(value);
        }
        return buffer.array();
    }

    static long[] bytesToLongs(byte... values) {
        int size = (int) Math.ceil((double) values.length / Long.SIZE);
        LongBuffer buffer = LongBuffer.allocate(size);
        for (byte b : values) {
            buffer.put(b);
        }
        return buffer.array();
    }
}

class HashCode16 extends HashCode {
    private final long long0;
    private final long long1;

    HashCode16(int hashCode, byte[] values) {
        this(
            hashCode,
            Longs.fromBytes(values[0], values[1], values[2], values[3], values[4], values[5], values[6], values[7]),
            Longs.fromBytes(values[8], values[9], values[10], values[11], values[12], values[13], values[14], values[15])
        );
    }

    HashCode16(int hashCode, long... values) {
        super(hashCode);
        this.long0 = values[0];
        this.long1 = values[1];
    }

    @Override
    public int length() {
        return 2 * Long.BYTES;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj == this) {
            return true;
        } else if (obj == null || obj.getClass() != HashCode16.class) {
            return false;
        }
        HashCode16 that = (HashCode16) obj;
        return long0 == that.long0
            && long1 == that.long1;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public byte[] toByteArray() {
        return longsToBytes(long0, long1);
    }
}

class HashCodeN extends HashCode {
    private final byte[] bytes;

    HashCodeN(int hashCode, byte[] values) {
        super(hashCode);
        this.bytes = values.clone();
    }

    @Override
    public int length() {
        return bytes.length;
    }

    public boolean equals(@Nullable Object obj) {
        if (obj == this) {
            return true;
        } else if (obj == null || obj.getClass() != HashCodeN.class) {
            return false;
        }
        HashCodeN that = (HashCodeN) obj;
        return Arrays.equals(bytes, that.bytes);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    public byte[] toByteArray() {
        return bytes.clone();
    }
}
