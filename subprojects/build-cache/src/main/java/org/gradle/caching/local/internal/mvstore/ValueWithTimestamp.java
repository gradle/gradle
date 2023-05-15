/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.caching.local.internal.mvstore;

import org.gradle.internal.hash.HashCode;
import org.h2.mvstore.DataUtils;
import org.h2.mvstore.WriteBuffer;
import org.h2.mvstore.type.BasicDataType;

import java.nio.ByteBuffer;

class ValueWithTimestamp implements Comparable<ValueWithTimestamp> {
    private final long timestamp;
    private final byte[] value;

    ValueWithTimestamp(long timestamp, byte[] value) {
        this.timestamp = timestamp;
        this.value = value;
    }

    @Override
    public int compareTo(ValueWithTimestamp o) {
        int result = Long.compare(timestamp, o.timestamp);
        if (result != 0) {
            return result;
        }
        return HashCode.compareBytes(value, o.value);
    }

    public byte[] get() {
        return value;
    }

    public long getTimestamp() {
        return timestamp;
    }

    static class ValueWithTimestampType extends BasicDataType<ValueWithTimestamp> {
        static final ValueWithTimestampType INSTANCE = new ValueWithTimestampType();

        @Override
        public int getMemory(ValueWithTimestamp obj) {
            return Long.BYTES + obj.value.length;
        }

        @Override
        public void write(WriteBuffer buff, ValueWithTimestamp obj) {
            buff.putLong(obj.timestamp);
            buff.putVarInt(obj.value.length);
            buff.put(obj.value);
        }

        @Override
        public ValueWithTimestamp read(ByteBuffer buff) {
            long timestamp = buff.getLong();
            int size = DataUtils.readVarInt(buff);
            byte[] key = new byte[size];
            buff.get(key);
            return new ValueWithTimestamp(timestamp, key);
        }

        @Override
        public ValueWithTimestamp[] createStorage(int size) {
            return new ValueWithTimestamp[size];
        }

        @Override
        public int compare(ValueWithTimestamp a, ValueWithTimestamp b) {
            return a.compareTo(b);
        }
    }
}
