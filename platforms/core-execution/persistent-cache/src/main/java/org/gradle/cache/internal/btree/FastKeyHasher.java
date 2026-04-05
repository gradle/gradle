/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.cache.internal.btree;

import com.google.common.hash.Hashing;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.serialize.kryo.KryoBackedEncoder;

import java.io.ByteArrayOutputStream;

/**
 * Hashes cache keys to {@code long} values using SipHash-2-4 via Guava.
 *
 * Compared to {@link KeyHasher} (MD5), SipHash-2-4 is significantly faster on
 * short inputs (typical serialized keys) while providing equivalent distribution
 * quality for hash-table use. It also produces a 64-bit value directly, avoiding
 * the byte-slicing step MD5 requires.
 *
 * Serialized key bytes are accumulated in a reusable buffer per thread to avoid
 * allocation on every call.
 */
class FastKeyHasher<K> {
    private final Serializer<K> serializer;
    private final ThreadLocal<State> state = ThreadLocal.withInitial(State::new);

    FastKeyHasher(Serializer<K> serializer) {
        this.serializer = serializer;
    }

    long getHashCode(K key) throws Exception {
        State s = state.get();
        s.buffer.reset();
        serializer.write(s.encoder, key);
        s.encoder.flush();
        return Hashing.sipHash24().hashBytes(s.buffer.getBuffer(), 0, s.buffer.getCount()).asLong();
    }

    private static class State {
        final ExposedByteArrayOutputStream buffer = new ExposedByteArrayOutputStream(512);
        final KryoBackedEncoder encoder = new KryoBackedEncoder(buffer);
    }

    /** ByteArrayOutputStream with exposed internal buffer to avoid copying on hash. */
    private static class ExposedByteArrayOutputStream extends ByteArrayOutputStream {
        ExposedByteArrayOutputStream(int size) {
            super(size);
        }

        byte[] getBuffer() { return buf; }
        int getCount() { return count; }
    }
}
