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

package org.gradle.util;

import com.google.common.base.Charsets;
import com.google.common.hash.Hasher;
import org.apache.commons.lang.SerializationUtils;

import java.io.NotSerializableException;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.math.BigInteger;
import java.util.Map;

public abstract class HasherUtil {
    public static void putByte(Hasher hasher, byte b) {
        hasher.putInt(1);
        hasher.putByte(b);
    }

    public static void putBytes(Hasher hasher, byte[] bytes) {
        hasher.putInt(bytes.length);
        hasher.putBytes(bytes);
    }

    public static void putBytes(Hasher hasher, byte[] bytes, int off, int len) {
        hasher.putInt(len);
        hasher.putBytes(bytes, off, len);
    }

    public static void putInt(Hasher hasher, int i) {
        hasher.putInt(4);
        hasher.putInt(i);
    }

    public static void putLong(Hasher hasher, long l) {
        hasher.putInt(8);
        hasher.putLong(l);
    }

    public static void putDouble(Hasher hasher, double d) {
        hasher.putInt(8);
        hasher.putDouble(d);
    }

    public static void putBoolean(Hasher hasher, boolean b) {
        hasher.putInt(1);
        hasher.putBoolean(b);
    }

    public static void putString(Hasher hasher, CharSequence charSequence) {
        hasher.putInt(charSequence.length());
        hasher.putString(charSequence, Charsets.UTF_8);
    }

    public static void putObject(Hasher hasher, Object value) throws NotSerializableException {
        if (value == null) {
            HasherUtil.putString(hasher, "$NULL");
            return;
        }

        if (value.getClass().isArray()) {
            HasherUtil.putString(hasher, "Array");
            for (int idx = 0, len = Array.getLength(value); idx < len; idx++) {
                hasher.putInt(idx);
                HasherUtil.putObject(hasher, Array.get(value, idx));
            }
            return;
        }

        if (value instanceof Iterable) {
            HasherUtil.putString(hasher, "Iterable");
            int idx = 0;
            for (Object elem : (Iterable<?>) value) {
                HasherUtil.putInt(hasher, idx);
                HasherUtil.putObject(hasher, elem);
                idx++;
            }
            return;
        }

        if (value instanceof Map) {
            HasherUtil.putString(hasher, "Map");
            int idx = 0;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                HasherUtil.putInt(hasher, idx);
                HasherUtil.putObject(hasher, entry.getKey());
                HasherUtil.putObject(hasher, entry.getValue());
                idx++;
            }
            return;
        }

        if (value instanceof Boolean) {
            HasherUtil.putBoolean(hasher, (Boolean) value);
        } else if (value instanceof Integer) {
            HasherUtil.putInt(hasher, (Integer) value);
        } else if (value instanceof Short) {
            HasherUtil.putInt(hasher, (Short) value);
        } else if (value instanceof Byte) {
            HasherUtil.putInt(hasher, (Byte) value);
        } else if (value instanceof Double) {
            HasherUtil.putDouble(hasher, (Double) value);
        } else if (value instanceof Float) {
            HasherUtil.putDouble(hasher, (Float) value);
        } else if (value instanceof BigInteger) {
            HasherUtil.putBytes(hasher, ((BigInteger) value).toByteArray());
        } else if (value instanceof CharSequence) {
            HasherUtil.putString(hasher, (CharSequence) value);
        } else if (value instanceof Enum) {
            HasherUtil.putString(hasher, value.getClass().getName());
            HasherUtil.putString(hasher, ((Enum) value).name());
        } else if (value instanceof Serializable) {
            byte[] bytes = SerializationUtils.serialize((Serializable) value);
            HasherUtil.putBytes(hasher, bytes);
        } else {
            throw new NotSerializableException(value.getClass().getName());
        }
    }
}
