/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.tasks.cache;

import com.google.common.base.Charsets;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultTaskCacheKeyBuilder implements TaskCacheKeyBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultTaskCacheKeyBuilder.class);
    private final Hasher hasher = Hashing.md5().newHasher();

    @Override
    public TaskCacheKeyBuilder putByte(byte b) {
        log("byte", b);
        hasher.putByte(b);
        return this;
    }

    @Override
    public TaskCacheKeyBuilder putBytes(byte[] bytes) {
        log("bytes", new ByteArrayToStringer(bytes));
        hasher.putBytes(bytes);
        return this;
    }

    @Override
    public TaskCacheKeyBuilder putBytes(byte[] bytes, int off, int len) {
        log("bytes", new ByteArrayToStringer(bytes, off, len));
        hasher.putBytes(bytes, off, len);
        return this;
    }

    @Override
    public TaskCacheKeyBuilder putInt(int i) {
        log("int", i);
        hasher.putInt(i);
        return this;
    }

    @Override
    public TaskCacheKeyBuilder putLong(long l) {
        log("long", l);
        hasher.putLong(l);
        return this;
    }

    @Override
    public TaskCacheKeyBuilder putDouble(double d) {
        log("double", d);
        hasher.putDouble(d);
        return this;
    }

    @Override
    public TaskCacheKeyBuilder putBoolean(boolean b) {
        log("boolean", b);
        hasher.putBoolean(b);
        return this;
    }

    @Override
    public TaskCacheKeyBuilder putString(CharSequence charSequence) {
        log("string", charSequence);
        hasher.putString(charSequence, Charsets.UTF_8);
        return this;
    }

    @Override
    public TaskCacheKey build() {
        HashCode hashCode = hasher.hash();
        LOGGER.info("Hash code generated: {}", hashCode);
        return new DefaultTaskCacheKey(hashCode);
    }

    private static class DefaultTaskCacheKey implements TaskCacheKey {

        private final HashCode hashCode;

        public DefaultTaskCacheKey(HashCode hashCode) {
            this.hashCode = hashCode;
        }

        @Override
        public String getHashCode() {
            return hashCode.toString();
        }

        @Override
        public String toString() {
            return hashCode.toString();
        }
    }

    private static void log(String type, Object value) {
        LOGGER.debug("Appending {} to cache key: {}", type, value);
    }

    private static class ByteArrayToStringer {
        private static final char[] HEX_DIGITS = "01234567890abcdef".toCharArray();
        private final byte[] bytes;
        private final int offset;
        private final int length;

        public ByteArrayToStringer(byte[] bytes) {
            this(bytes, 0, bytes.length);
        }

        public ByteArrayToStringer(byte[] bytes, int offset, int length) {
            this.bytes = bytes;
            this.offset = offset;
            this.length = length;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder(bytes.length * 3);
            for (int idx = offset; idx < offset + length; idx++) {
                byte b = bytes[idx];
                if (idx > 0) {
                    builder.append(':');
                }
                builder.append(HEX_DIGITS[(b >>> 4) & 0xf]);
                builder.append(HEX_DIGITS[b & 0xf]);
            }
            return builder.toString();
        }
    }
}
