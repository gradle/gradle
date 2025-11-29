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

import static org.gradle.internal.collect.ChampNode.index;
import static org.gradle.internal.collect.ChampNode.mask;
import static org.gradle.internal.collect.ChampNode.nodeIndex;

/// Implementation of the CHAMP lookup algorithm shared by [PersistentSetTrie], when `payload == 0`,
/// and [PersistentMapTrie], when `payload == 1`.
final class ChampLookup {

    @SuppressWarnings({"BoxedPrimitiveEquality", "ReferenceEquality"})
    static <K> boolean containsKey(ChampNode<K> node, K key, int payload) {
        return lookup(node, key, payload, Boolean.FALSE, (content, index) -> Boolean.TRUE) == Boolean.TRUE;
    }

    @SuppressWarnings("unchecked")
    static <K, V extends @Nullable Object> V lookup(ChampNode<K> trie, K key, int payload, V defaultValue, LookupResult<V> result) {
        int shift = 0;
        int hash = key.hashCode();
        while (true) {
            int mask = mask(hash, shift);
            int bit = 1 << mask;

            int dataMap = trie.dataMap;
            if ((dataMap & bit) == bit) {

                Object[] content = trie.content;
                int dataIndex = index(dataMap, mask, bit) << payload;
                Object data = content[dataIndex];
                if (data == key || data.equals(key)) {
                    return result.apply(content, dataIndex);
                }

            } else {

                int nodeMap = trie.nodeMap;
                if ((nodeMap & bit) == bit) {
                    Object[] content = trie.content;
                    int nodeIndex = nodeIndex(content, nodeMap, mask, bit);
                    Object node = content[nodeIndex];
                    if (node instanceof ChampNode<?>) {
                        trie = (ChampNode<K>) node;
                        shift += ChampNode.BITS;
                        continue;
                    }
                    HashCollisionNode collision = (HashCollisionNode) node;
                    if (collision.hash == hash) {
                        int index = collision.indexOf(key, payload);
                        if (index >= 0) {
                            return result.apply(collision.content, index);
                        }
                    }
                }
            }

            return defaultValue;
        }
    }

    @FunctionalInterface
    interface LookupResult<V extends @Nullable Object> {
        V apply(Object[] content, int index);
    }
}
