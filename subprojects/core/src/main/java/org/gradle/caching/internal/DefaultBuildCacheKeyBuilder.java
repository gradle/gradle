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

package org.gradle.caching.internal;

import com.google.common.base.Charsets;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import org.apache.commons.lang.SerializationUtils;
import org.gradle.caching.BuildCacheKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.math.BigInteger;
import java.util.Map;

/**
 * A builder for build cache keys.
 *
 * In order to avoid collisions we prepend the length of the next bytes to the underlying
 * hasher (see this <a href="http://crypto.stackexchange.com/a/10065">answer</a> on stackexchange).
 */
public class DefaultBuildCacheKeyBuilder implements BuildCacheKeyBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultBuildCacheKeyBuilder.class);
    private final Hasher hasher = Hashing.md5().newHasher();

    @Override
    public BuildCacheKeyBuilder putByte(byte b) {
        log("byte", b);
        hasher.putInt(1);
        hasher.putByte(b);
        return this;
    }

    @Override
    public BuildCacheKeyBuilder putBytes(byte[] bytes) {
        log("bytes", new ByteArrayToStringer(bytes));
        hasher.putInt(bytes.length);
        hasher.putBytes(bytes);
        return this;
    }

    @Override
    public BuildCacheKeyBuilder putBytes(byte[] bytes, int off, int len) {
        log("bytes", new ByteArrayToStringer(bytes, off, len));
        hasher.putInt(len);
        hasher.putBytes(bytes, off, len);
        return this;
    }

    @Override
    public BuildCacheKeyBuilder putInt(int i) {
        log("int", i);
        hasher.putInt(4);
        hasher.putInt(i);
        return this;
    }

    @Override
    public BuildCacheKeyBuilder putLong(long l) {
        log("long", l);
        hasher.putInt(8);
        hasher.putLong(l);
        return this;
    }

    @Override
    public BuildCacheKeyBuilder putDouble(double d) {
        log("double", d);
        hasher.putInt(8);
        hasher.putDouble(d);
        return this;
    }

    @Override
    public BuildCacheKeyBuilder putBoolean(boolean b) {
        log("boolean", b);
        hasher.putInt(1);
        hasher.putBoolean(b);
        return this;
    }

    @Override
    public BuildCacheKeyBuilder putString(CharSequence charSequence) {
        log("string", charSequence);
        hasher.putInt(charSequence.length());
        hasher.putString(charSequence, Charsets.UTF_8);
        return this;
    }

    @Override
    public BuildCacheKey build() {
        HashCode hashCode = hasher.hash();
        LOGGER.debug("Hash code generated: {}", hashCode);
        return new DefaultBuildCacheKey(hashCode);
    }

    private static class DefaultBuildCacheKey implements BuildCacheKey {

        private final HashCode hashCode;

        public DefaultBuildCacheKey(HashCode hashCode) {
            this.hashCode = hashCode;
        }

        @Override
        public String getHashCode() {
            return hashCode.toString();
        }

        @Override
        public String toString() {
            return getHashCode();
        }
    }

    private static void log(String type, Object value) {
        LOGGER.debug("Appending {} to cache key: {}", type, value);
    }

    public BuildCacheKeyBuilder appendToCacheKey(Object value) {

        if (value == null) {
            this.putString("$NULL");
            return this;
        }

        if (value.getClass().isArray()) {
            this.putString("Array");
            for (int idx = 0, len = Array.getLength(value); idx < len; idx++) {
                this.putInt(idx);
                this.appendToCacheKey(Array.get(value, idx));
            }
            return this;
        }

        if (value instanceof Iterable) {
            this.putString("Iterable");
            int idx = 0;
            for (Object elem : (Iterable<?>) value) {
                this.putInt(idx);
                this.appendToCacheKey(elem);
                idx++;
            }
            return this;
        }

        if (value instanceof Map) {
            this.putString("Map");
            int idx = 0;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                this.putInt(idx);
                this.appendToCacheKey(entry.getKey());
                this.appendToCacheKey(entry.getValue());
                idx++;
            }
            return this;
        }

        if (value instanceof Boolean) {
            this.putBoolean((Boolean) value);
        } else if (value instanceof Long) {
            this.putLong((Long) value);
        } else if (value instanceof Integer) {
            this.putInt((Integer) value);
        } else if (value instanceof Short) {
            this.putInt((Short) value);
        } else if (value instanceof Byte) {
            this.putInt((Byte) value);
        } else if (value instanceof Double) {
            this.putDouble((Double) value);
        } else if (value instanceof Float) {
            this.putDouble((Float) value);
        } else if (value instanceof BigInteger) {
            this.putBytes(((BigInteger) value).toByteArray());
        } else if (value instanceof CharSequence) {
            this.putString((CharSequence) value);
        } else if (value instanceof Enum) {
            this.putString(value.getClass().getName());
            this.putString(((Enum) value).name());
        } else {
            byte[] bytes = SerializationUtils.serialize((Serializable) value);
            this.putBytes(bytes);
        }
        return this;
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
