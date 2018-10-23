/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.serialize.kryo;

import com.esotericsoftware.kryo.io.Output;
import com.google.common.collect.Lists;
import org.gradle.internal.serialize.AbstractEncoder;
import org.gradle.internal.serialize.FlushableEncoder;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.OutputStream;
import java.util.List;

public class StringDeduplicatingKryoBackedEncoder extends AbstractEncoder implements FlushableEncoder, Closeable {
    private IndexedStringSet strings;

    private final Output output;

    public StringDeduplicatingKryoBackedEncoder(OutputStream outputStream) {
        this(outputStream, 4096);
    }

    public StringDeduplicatingKryoBackedEncoder(OutputStream outputStream, int bufferSize) {
        output = new Output(outputStream, bufferSize);
    }

    public void writeByte(byte value) {
        output.writeByte(value);
    }

    public void writeBytes(byte[] bytes, int offset, int count) {
        output.writeBytes(bytes, offset, count);
    }

    public void writeLong(long value) {
        output.writeLong(value);
    }

    public void writeSmallLong(long value) {
        output.writeLong(value, true);
    }

    public void writeInt(int value) {
        output.writeInt(value);
    }

    public void writeSmallInt(int value) {
        output.writeInt(value, true);
    }

    public void writeBoolean(boolean value) {
        output.writeBoolean(value);
    }

    public void writeString(CharSequence value) {
        if (value == null) {
            throw new IllegalArgumentException("Cannot encode a null string.");
        }
        writeNullableString(value);
    }

    public void writeNullableString(@Nullable CharSequence value) {
        if (value == null) {
            output.writeByte((byte) 0);
            return;
        } else {
            if (strings == null) {
                strings = new IndexedStringSet();
            }
            output.writeByte((byte) 1);
        }
        strings.register(value.toString());
    }

    /**
     * Returns the total number of bytes written by this encoder, some of which may still be buffered.
     */
    public long getWritePosition() {
        return output.total();
    }

    public void flush() {
        output.flush();
    }

    public void close() {
        output.close();
    }

    public void done() {
        strings = null;
    }

    private class IndexedStringSet {

        private final StringSetBucket[] buckets = new StringSetBucket[256];
        private int count;

        public void register(String value) {
            int bucketId = value.hashCode() & 0xFF;
            StringSetBucket bucket = buckets[bucketId];
            if (bucket == null) {
                buckets[bucketId] = new SingleEntryStringSet(value);
            } else {
                buckets[bucketId] = bucket.register(value);
            }
        }

        private class SingleEntryStringSet implements StringSetBucket {
            private final IndexedString indexed;

            private SingleEntryStringSet(String value) {
                this.indexed = new IndexedString(value, count);
                output.writeInt(count, true);
                output.writeString(value);
                count++;
            }

            public StringSetBucket register(String value) {
                if (indexed.matches(value)) {
                    output.writeInt(indexed.index, true);
                    return this;
                }
                return new MultiStringSet(indexed).register(value);
            }

            @Override
            public String toString() {
                return indexed.toString();
            }
        }

        private class MultiStringSet implements StringSetBucket {
            private final List<IndexedString> store = Lists.newArrayList();

            public MultiStringSet(IndexedString initial) {
                store.add(initial);
            }

            @Override
            public StringSetBucket register(String value) {
                for (IndexedString indexedString : store) {
                    if (indexedString.matches(value)) {
                        output.writeInt(indexedString.index, true);
                        return this;
                    }
                }
                output.writeInt(count, true);
                output.writeString(value);
                store.add(new IndexedString(value, count));
                count++;
                return this;
            }

            @Override
            public String toString() {
                return store.toString();
            }
        }
    }

    private interface StringSetBucket {
        StringSetBucket register(String value);
    }

    private static class IndexedString {
        private final String value;
        private final int index;

        private IndexedString(String value, int index) {
            this.value = value;
            this.index = index;
        }

        boolean matches(String value) {
            return value.hashCode() == this.value.hashCode() && value.equals(this.value);
        }

        @Override
        public String toString() {
            return "Value '" + value + "' index " + index;
        }
    }
}
