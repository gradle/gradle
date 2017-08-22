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
import org.gradle.internal.Factory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Hashing {
    private Hashing() {}

    private static final HashFunction MD5 = new MessageDigestHashFunction(CloningFactory.of(new Factory<MessageDigest>() {
        @Override
        public MessageDigest create() {
            try {
                return MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                throw new UnsupportedOperationException();
            }
        }
    }));

    private static final HashFunction SHA1 = new MessageDigestHashFunction(CloningFactory.of(new Factory<MessageDigest>() {
        @Override
        public MessageDigest create() {
            try {
                return MessageDigest.getInstance("SHA-1");
            } catch (NoSuchAlgorithmException e) {
                throw new UnsupportedOperationException();
            }
        }
    }));

    public static HashFunction md5() {
        return MD5;
    }

    public static HashFunction sha1() {
        return SHA1;
    }

    private static class MessageDigestHashFunction implements HashFunction {
        private final Factory<MessageDigest> factory;

        public MessageDigestHashFunction(Factory<MessageDigest> factory) {
            this.factory = factory;
        }

        @Override
        public Hasher newHasher() {
            MessageDigest digest = factory.create();
            return new MessageDigestHasher(digest);
        }

        @Override
        public HashCode hashBytes(byte[] bytes) {
            Hasher hasher = newHasher();
            hasher.putBytes(bytes);
            return hasher.hash();
        }

        @Override
        public HashCode hashString(CharSequence string) {
            Hasher hasher = newHasher();
            hasher.putString(string);
            return hasher.hash();
        }
    }

    private static class MessageDigestHasher implements Hasher {
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
            buffer.clear();
        }

        @Override
        public void putShort(short value) {
            buffer.putShort(value);
            update(2);
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
        public void putFloat(float value) {
            int intValue = Float.floatToRawIntBits(value);
            putInt(intValue);
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
        public void putChar(char value) {
            putInt(value);
        }

        @Override
        public void putString(CharSequence value) {
            putBytes(value.toString().getBytes(Charsets.UTF_8));
        }
    }

    private static class CloningFactory implements Factory<MessageDigest> {
        private final MessageDigest baseDigest;

        public CloningFactory(MessageDigest baseDigest) {
            this.baseDigest = baseDigest;
        }

        @Override
        public MessageDigest create() {
            try {
                return (MessageDigest) baseDigest.clone();
            } catch (CloneNotSupportedException ignore) {
                throw new AssertionError();
            }
        }

        public static Factory<MessageDigest> of(Factory<MessageDigest> factory) {
            MessageDigest baseDigest = factory.create();
            try {
                baseDigest.clone();
                return new CloningFactory(baseDigest);
            } catch (CloneNotSupportedException e) {
                return factory;
            }
        }
    }
}
