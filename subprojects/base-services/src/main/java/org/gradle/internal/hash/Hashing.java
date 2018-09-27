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

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import org.gradle.internal.io.BufferCaster;
import org.gradle.internal.io.NullOutputStream;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Some popular hash functions. Replacement for Guava's hashing utilities.
 * Inspired by the Google Guava project – https://github.com/google/guava.
 */
public class Hashing {
    private Hashing() {}

    private static final HashFunction MD5 = MessageDigestHashFunction.of("MD5");

    private static final HashFunction SHA1 = MessageDigestHashFunction.of("SHA-1");

    private static final HashFunction DEFAULT = MD5;

    /**
     * Returns a new {@link Hasher} based on the default hashing implementation.
     */
    public static Hasher newHasher() {
        return DEFAULT.newHasher();
    }

    /**
     * Returns a new {@link PrimitiveHasher} based on the default hashing implementation.
     */
    public static PrimitiveHasher newPrimitiveHasher() {
        return DEFAULT.newPrimitiveHasher();
    }

    /**
     * Returns a hash code to use as a signature for a given type.
     */
    public static HashCode signature(Class<?> type) {
        return signature("CLASS:" + type.getName());
    }

    /**
     * Returns a hash code to use as a signature for a given thing.
     */
    public static HashCode signature(String thing) {
        Hasher hasher = DEFAULT.newHasher();
        hasher.putString("SIGNATURE");
        hasher.putString(thing);
        return hasher.hash();
    }

    /**
     * Hash the given bytes with the default hash function.
     */
    public static HashCode hashBytes(byte[] bytes) {
        return DEFAULT.hashBytes(bytes);
    }

    /**
     * Hash the given string with the default hash function.
     */
    public static HashCode hashString(CharSequence string) {
        return DEFAULT.hashString(string);
    }

    /**
     * Creates a {@link HashingOutputStream} with the default hash function.
     */
    public static HashingOutputStream primitiveStreamHasher() {
        return primitiveStreamHasher(NullOutputStream.INSTANCE);
    }

    /**
     * Creates a {@link HashingOutputStream} with the default hash function.
     */
    public static HashingOutputStream primitiveStreamHasher(OutputStream output) {
        return new HashingOutputStream(DEFAULT, output);
    }

    /**
     * The default hashing function.
     */
    public static HashFunction defaultFunction() {
        return DEFAULT;
    }

    /**
     * MD5 hashing function.
     */
    public static HashFunction md5() {
        return MD5;
    }

    /**
     * SHA1 hashing function.
     */
    public static HashFunction sha1() {
        return SHA1;
    }

    private static abstract class MessageDigestHashFunction implements HashFunction {
        public static MessageDigestHashFunction of(String algorithm) {
            MessageDigest prototype;
            try {
                prototype = MessageDigest.getInstance(algorithm);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalArgumentException("Cannot instantiate digest algorithm: " + algorithm);
            }
            try {
                prototype.clone();
                return new CloningMessageDigestHashFunction(prototype);
            } catch (CloneNotSupportedException e) {
                return new RegularMessageDigestHashFunction(algorithm);
            }
        }

        @Override
        public PrimitiveHasher newPrimitiveHasher() {
            MessageDigest digest = createDigest();
            return new MessageDigestHasher(digest);
        }

        @Override
        public Hasher newHasher() {
            return new DefaultHasher(newPrimitiveHasher());
        }

        @Override
        public HashCode hashBytes(byte[] bytes) {
            PrimitiveHasher hasher = newPrimitiveHasher();
            hasher.putBytes(bytes);
            return hasher.hash();
        }

        @Override
        public HashCode hashString(CharSequence string) {
            PrimitiveHasher hasher = newPrimitiveHasher();
            hasher.putString(string);
            return hasher.hash();
        }

        protected abstract MessageDigest createDigest();
    }

    private static class CloningMessageDigestHashFunction extends MessageDigestHashFunction {
        private final MessageDigest prototype;

        public CloningMessageDigestHashFunction(MessageDigest prototype) {
            this.prototype = prototype;
        }

        @Override
        protected MessageDigest createDigest() {
            try {
                return (MessageDigest) prototype.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }
    }

    private static class RegularMessageDigestHashFunction extends MessageDigestHashFunction {
        private final String algorithm;

        public RegularMessageDigestHashFunction(String algorithm) {
            this.algorithm = algorithm;
        }

        @Override
        protected MessageDigest createDigest() {
            try {
                return MessageDigest.getInstance(algorithm);
            } catch (NoSuchAlgorithmException e) {
                throw new AssertionError(e);
            }
        }
    }

    private static class MessageDigestHasher implements PrimitiveHasher {
        private final MessageDigest digest;
        private final ByteBuffer buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        private boolean done;

        public MessageDigestHasher(MessageDigest digest) {
            this.digest = digest;
        }

        private void checkNotDone() {
            if (done) {
                throw new IllegalStateException("Cannot reuse hasher");
            }
        }

        @Override
        public void putByte(byte b) {
            checkNotDone();
            digest.update(b);
        }

        @Override
        public void putBytes(byte[] bytes) {
            checkNotDone();
            digest.update(bytes);
        }

        @Override
        public void putBytes(byte[] bytes, int off, int len) {
            checkNotDone();
            digest.update(bytes, off, len);
        }

        @Override
        public HashCode hash() {
            done = true;
            byte[] bytes = digest.digest();
            return HashCode.fromBytesNoCopy(bytes);
        }

        private void update(int length) {
            checkNotDone();
            digest.update(buffer.array(), 0, length);
            BufferCaster.cast(buffer).clear();
        }

        @Override
        public void putInt(int value) {
            buffer.putInt(value);
            update(4);
        }

        @Override
        public void putLong(long value) {
            buffer.putLong(value);
            update(8);
        }

        @Override
        public void putDouble(double value) {
            long longValue = Double.doubleToRawLongBits(value);
            putLong(longValue);
        }

        @Override
        public void putBoolean(boolean value) {
            checkNotDone();
            putByte((byte) (value ? 1 : 0));
        }

        @Override
        public void putString(CharSequence value) {
            putBytes(value.toString().getBytes(Charsets.UTF_8));
        }

        @Override
        public void putHash(HashCode hashCode) {
            putBytes(hashCode.getBytes());
        }
    }

    private static class DefaultHasher implements Hasher {
        private final PrimitiveHasher hasher;
        private String invalidReason;

        public DefaultHasher(PrimitiveHasher unsafeHasher) {
            this.hasher = unsafeHasher;
        }

        @Override
        public void putByte(byte value) {
            hasher.putInt(1);
            hasher.putByte(value);
        }

        @Override
        public void putBytes(byte[] bytes) {
            hasher.putInt(bytes.length);
            hasher.putBytes(bytes);
        }

        @Override
        public void putBytes(byte[] bytes, int off, int len) {
            hasher.putInt(len);
            hasher.putBytes(bytes, off, len);
        }

        @Override
        public void putHash(HashCode hashCode) {
            hasher.putInt(hashCode.length());
            hasher.putHash(hashCode);
        }

        @Override
        public void putInt(int value) {
            hasher.putInt(4);
            hasher.putInt(value);
        }

        @Override
        public void putLong(long value) {
            hasher.putInt(8);
            hasher.putLong(value);
        }

        @Override
        public void putDouble(double value) {
            hasher.putInt(8);
            hasher.putDouble(value);
        }

        @Override
        public void putBoolean(boolean value) {
            hasher.putInt(1);
            hasher.putBoolean(value);
        }

        @Override
        public void putString(CharSequence value) {
            hasher.putInt(value.length());
            hasher.putString(value);
        }

        @Override
        public void putNull() {
            this.putInt(0);
        }

        @Override
        public void markAsInvalid(String invalidReason) {
            this.invalidReason = invalidReason;
        }

        @Override
        public boolean isValid() {
            return invalidReason == null;
        }

        @Override
        public String getInvalidReason() {
            return Preconditions.checkNotNull(invalidReason);
        }

        @Override
        public HashCode hash() {
            if (!isValid()) {
                throw new IllegalStateException("Hash is invalid: " + getInvalidReason());
            }
            return hasher.hash();
        }
    }
}
