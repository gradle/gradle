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
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Some popular hash functions. Replacement for Guava's hashing utilities.
 * Inspired by the Google Guava project – https://github.com/google/guava.
 */
public class Hashing {
    private Hashing() {
    }

    private static final HashFunction MD5 = MessageDigestHashFunction.of("MD5");

    private static final HashFunction SHA1 = MessageDigestHashFunction.of("SHA-1");

    private static final HashFunction SHA256 = MessageDigestHashFunction.of("SHA-256");

    private static final HashFunction SHA512 = MessageDigestHashFunction.of("SHA-512");

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
     * Hash the contents of the given {@link java.io.InputStream} with the default hash function.
     */
    public static HashCode hashStream(InputStream stream) throws IOException {
        return DEFAULT.hashStream(stream);
    }

    /**
     * Hash the contents of the given {@link java.io.File} with the default hash function.
     */
    public static HashCode hashFile(File file) throws IOException {
        return DEFAULT.hashFile(file);
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

    /**
     * SHA-256 hashing function.
     */
    public static HashFunction sha256() {
        return SHA256;
    }

    /**
     * SHA-512 hashing function.
     */
    public static HashFunction sha512() {
        return SHA512;
    }

    private static abstract class MessageDigestHashFunction implements HashFunction {
        private final int hexDigits;

        public MessageDigestHashFunction(int hashBits) {
            this.hexDigits = hashBits / 4;
        }

        public static MessageDigestHashFunction of(String algorithm) {
            MessageDigest prototype;
            try {
                prototype = MessageDigest.getInstance(algorithm);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalArgumentException("Cannot instantiate digest algorithm: " + algorithm);
            }
            int hashBits = prototype.getDigestLength() * 8;
            try {
                prototype.clone();
                return new CloningMessageDigestHashFunction(prototype, hashBits);
            } catch (CloneNotSupportedException e) {
                return new RegularMessageDigestHashFunction(algorithm, hashBits);
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

        @Override
        public HashCode hashStream(InputStream stream) throws IOException {
            HashingOutputStream hashingOutputStream = primitiveStreamHasher();
            ByteStreams.copy(stream, hashingOutputStream);
            return hashingOutputStream.hash();
        }

        @Override
        public HashCode hashFile(File file) throws IOException {
            HashingOutputStream hashingOutputStream = primitiveStreamHasher();
            Files.copy(file, hashingOutputStream);
            return hashingOutputStream.hash();
        }

        private HashingOutputStream primitiveStreamHasher() {
            return new HashingOutputStream(this, ByteStreams.nullOutputStream());
        }

        protected abstract MessageDigest createDigest();

        @Override
        public int getHexDigits() {
            return hexDigits;
        }

        @Override
        public String toString() {
            return getAlgorithm();
        }
    }

    private static class CloningMessageDigestHashFunction extends MessageDigestHashFunction {
        private final MessageDigest prototype;

        public CloningMessageDigestHashFunction(MessageDigest prototype, int hashBits) {
            super(hashBits);
            this.prototype = prototype;
        }

        @Override
        public String getAlgorithm() {
            return prototype.getAlgorithm();
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

        public RegularMessageDigestHashFunction(String algorithm, int hashBits) {
            super(hashBits);
            this.algorithm = algorithm;
        }

        @Override
        public String getAlgorithm() {
            return algorithm;
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
        private final ByteBuffer buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        private MessageDigest digest;

        public MessageDigestHasher(MessageDigest digest) {
            this.digest = digest;
        }

        private MessageDigest getDigest() {
            if (digest == null) {
                throw new IllegalStateException("Cannot reuse hasher!");
            }
            return digest;
        }

        @Override
        public void putByte(byte b) {
            getDigest().update(b);
        }

        @Override
        public void putBytes(byte[] bytes) {
            getDigest().update(bytes);
        }

        @Override
        public void putBytes(byte[] bytes, int off, int len) {
            getDigest().update(bytes, off, len);
        }

        private void update(int length) {
            getDigest().update(buffer.array(), 0, length);
            castBuffer(buffer).clear();
        }

        /**
         * Without this cast, when the code compiled by Java 9+ is executed on Java 8, it will throw
         * java.lang.NoSuchMethodError: Method flip()Ljava/nio/ByteBuffer; does not exist in class java.nio.ByteBuffer
         */
        @SuppressWarnings("RedundantCast")
        private static <T extends Buffer> Buffer castBuffer(T byteBuffer) {
            return (Buffer) byteBuffer;
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

        @Override
        public HashCode hash() {
            byte[] bytes = getDigest().digest();
            digest = null;
            return HashCode.fromBytesNoCopy(bytes);
        }
    }

    private static class DefaultHasher implements Hasher {
        private final PrimitiveHasher hasher;

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
        public void put(Hashable hashable) {
            hashable.appendToHasher(this);
        }

        @Override
        public void putNull() {
            this.putInt(0);
        }

        @Override
        public HashCode hash() {
            return hasher.hash();
        }
    }
}
