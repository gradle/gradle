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
import com.google.common.collect.Maps;
import org.gradle.internal.serialize.AbstractEncoder;
import org.gradle.internal.serialize.FlushableEncoder;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

public class StringDeduplicatingKryoBackedEncoder extends AbstractEncoder implements FlushableEncoder, Closeable {
    private IndexedStringSet strings;

    private final Output output;

    public StringDeduplicatingKryoBackedEncoder(OutputStream outputStream) {
        this(outputStream, 4096);
    }

    public StringDeduplicatingKryoBackedEncoder(OutputStream outputStream, int bufferSize) {
        output = new Output(outputStream, bufferSize);
    }

    @Override
    public void writeByte(byte value) {
        output.writeByte(value);
    }

    @Override
    public void writeBytes(byte[] bytes, int offset, int count) {
        output.writeBytes(bytes, offset, count);
    }

    @Override
    public void writeLong(long value) {
        output.writeLong(value);
    }

    @Override
    public void writeSmallLong(long value) {
        output.writeLong(value, true);
    }

    @Override
    public void writeInt(int value) {
        output.writeInt(value);
    }

    @Override
    public void writeSmallInt(int value) {
        output.writeInt(value, true);
    }

    @Override
    public void writeBoolean(boolean value) {
        output.writeBoolean(value);
    }

    @Override
    public void writeString(CharSequence value) {
        if (value == null) {
            throw new IllegalArgumentException("Cannot encode a null string.");
        }
        writeNullableString(value);
    }

    @Override
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

    @Override
    public void flush() {
        output.flush();
    }

    @Override
    public void close() {
        output.close();
    }

    public void done() {
        strings = null;
    }

    /**
     * A dedicated set of strings implementation which associates a unique
     * integer to each new string. It works similarly to a hash map, by
     * selecting a bucket based on the 8 lower bits of the hash code of
     * the string. Then there are two bucket implementations: one in case
     * there's a single string in the bucket, the other when multiple strings
     * are in.
     *
     * Integers are not chosen arbitrarily: they must be consecutive integers
     * starting from 0.
     *
     * This is done so that we can optimize the size of the stream written, by
     * replacing strings with an id. Therefore this set takes care of doing it
     * as we build the set.
     */
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

        /**
         * A bucket that contains only a single entry. Optimized for memory
         * usage.
         */
        private class SingleEntryStringSet implements StringSetBucket {
            private final IndexedString indexed;

            private SingleEntryStringSet(String value) {
                this.indexed = new IndexedString(value, count);
                output.writeInt(count, true);
                output.writeString(value);
                count++;
            }

            @Override
            public StringSetBucket register(String value) {
                if (indexed.matches(value)) {
                    output.writeInt(indexed.index, true);
                    return this;
                }
                return new MultiListStringSet(indexed).register(value);
            }

            @Override
            public String toString() {
                return indexed.toString();
            }
        }

        /**
         * A bucket implementation used when more than one string is found in a bucket, with
         * a reasonable number of strings.
         */
        private class MultiListStringSet implements StringSetBucket {
            private final List<IndexedString> store = Lists.newArrayList();

            public MultiListStringSet(IndexedString initial) {
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
                if (store.size() > 4) {
                    return new MultiMapStringSet(store);
                }
                return this;
            }

            @Override
            public String toString() {
                return store.toString();
            }
        }

        /**
         * A bucket implementation which uses a map under the hood, for
         * faster lookups whenever the number of items in a bucket grows
         * too much.
         */
        private class MultiMapStringSet implements StringSetBucket {
            private final Map<String, Integer> map;

            private MultiMapStringSet(List<IndexedString> strings) {
                map = Maps.newHashMapWithExpectedSize(strings.size() << 1);
                for (IndexedString indexedString : strings) {
                    map.put(indexedString.value, indexedString.index);
                }
            }

            @Override
            public StringSetBucket register(String value) {
                Integer index = map.get(value);
                if (index != null) {
                    output.writeInt(index, true);
                    return this;
                }
                output.writeInt(count, true);
                output.writeString(value);
                count++;
                return this;
            }

            @Override
            public String toString() {
                return map.toString();
            }
        }
    }

    /**
     * Interface for all bucket types.
     */
    private interface StringSetBucket {
        /**
         * Registers a string in the set. The returned value may either be
         * the same string set, or a different implementation optimized for
         * a different bucket size. This allows us to go from single string set
         * to list set and eventually a map backed set.
         */
        StringSetBucket register(String value);
    }

    /**
     * Associates a unique integer to a string.
     */
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
