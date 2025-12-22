/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.collect;

import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.BiFunction;

/// Holds keys (and their respective values when `payload == 1`) that share the same hash code.
final class HashCollisionNode {

    /// The hash code shared by all keys in [#content].
    final int hash;
    final Object[] content;

    HashCollisionNode(int hash, Object[] content) {
        this.hash = hash;
        this.content = content;
    }

    @Override
    public int hashCode() {
        throw new IllegalStateException();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        HashCollisionNode that = (HashCollisionNode) other;
        if (hash != that.hash) {
            return false;
        }
        Object[] content = this.content;
        int length = content.length;
        if (length != that.content.length) {
            return false;
        }
        for (int i = length - 1; i >= 0; i--) {
            // We pass payload == 0 so all content is compared against the given value.
            if (!that.contains(content[i], 0)) {
                return false;
            }
        }
        return true;
    }

    public boolean contains(Object key, int payload) {
        return indexOf(key, payload) >= 0;
    }

    public <K> int indexOf(K key, int payload) {
        int step = 1 + payload;
        Object[] content = this.content;
        for (int i = content.length - step; i >= 0; i -= step) {
            Object data = content[i];
            if (data == key || data.equals(key)) {
                return i;
            }
        }
        return -1;
    }

    public <K> HashCollisionNode add(K key) {
        return contains(key, 0)
            ? this
            : new HashCollisionNode(hash, ArrayCopy.append(content, key));
    }

    public <K> HashCollisionNode put(K key, Object val, Modification modification) {
        int index = indexOf(key, 1);
        if (index == -1) {
            return new HashCollisionNode(hash, ArrayCopy.append(content, key, val));
        }
        int valIndex = index + 1;
        if (Objects.equals(content[valIndex], val)) {
            // entry already exists
            return this;
        }
        modification.kind = Modification.Kind.UPDATE;
        return new HashCollisionNode(hash, ArrayCopy.replaceAt(valIndex, content, val));
    }

    @SuppressWarnings("unchecked")
    public <K, V> HashCollisionNode modify(K key, BiFunction<? super K, ? super @Nullable V, ? extends @Nullable V> f, Modification modification) {
        int index = indexOf(key, 1);
        if (index == -1) {
            Object newVal = f.apply(key, null);
            if (newVal == null) {
                return this;
            }
            return new HashCollisionNode(hash, ArrayCopy.append(content, key, newVal));
        } else {
            int valIndex = index + 1;
            Object curVal = content[valIndex];

            Object newVal = f.apply(key, (V) curVal);
            if (newVal == null) {
                modification.kind = Modification.Kind.REMOVAL;
                return this;
            }

            if (Objects.equals(curVal, newVal)) {
                // entry already exists
                return this;
            }
            modification.kind = Modification.Kind.UPDATE;
            return new HashCollisionNode(hash, ArrayCopy.replaceAt(valIndex, content, newVal));
        }
    }

    public HashCollisionNode removeAt(int keyIndex, int payload) {
        return new HashCollisionNode(hash, ArrayCopy.removeAt(keyIndex, content, 1 + payload));
    }

    @Override
    public String toString() {
        return Arrays.toString(content);
    }
}
