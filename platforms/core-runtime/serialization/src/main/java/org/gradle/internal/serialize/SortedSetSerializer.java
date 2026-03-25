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
package org.gradle.internal.serialize;

import com.google.common.base.Objects;
import com.google.common.collect.Sets;
import org.jspecify.annotations.NullMarked;

import java.util.Arrays;
import java.util.Set;

/**
 * Serializer for {@code Set<T>} that writes elements in sorted order,
 * ensuring deterministic serialization regardless of input iteration order.
 *
 * <p>Elements must be {@link Comparable}. The wire format is identical to {@link SetSerializer}:
 * size followed by elements. On read, elements are returned in a {@link java.util.LinkedHashSet}
 * preserving the serialized (sorted) order.</p>
 */
@NullMarked
public class SortedSetSerializer<T extends Comparable<T>> extends AbstractSerializer<Set<T>> {

    private final Serializer<T> entrySerializer;

    public SortedSetSerializer(Serializer<T> entrySerializer) {
        this.entrySerializer = entrySerializer;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void write(Encoder encoder, Set<T> value) throws Exception {
        // We cannot create a T[] directly due to type erasure; Comparable[] is the
        // closest array type available since T extends Comparable<T>.
        T[] sorted = (T[]) value.toArray(new Comparable[0]);
        Arrays.sort(sorted);
        encoder.writeInt(sorted.length);
        for (T t : sorted) {
            entrySerializer.write(encoder, t);
        }
    }

    @Override
    public Set<T> read(Decoder decoder) throws Exception {
        int size = decoder.readInt();
        Set<T> set = Sets.newLinkedHashSetWithExpectedSize(size);
        for (int i = 0; i < size; i++) {
            set.add(entrySerializer.read(decoder));
        }
        return set;
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }
        SortedSetSerializer<?> rhs = (SortedSetSerializer<?>) obj;
        return Objects.equal(entrySerializer, rhs.entrySerializer);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), entrySerializer);
    }
}
