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

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

import static org.gradle.internal.collect.ChampDeletion.deleteFrom;
import static org.gradle.internal.collect.ChampNode.BITS;
import static org.gradle.internal.collect.ChampNode.index;
import static org.gradle.internal.collect.ChampNode.mask;
import static org.gradle.internal.collect.ChampNode.nodeIndex;
import static org.gradle.internal.collect.Preconditions.entryCannotBeNull;

/// A [PersistentMap] with two or more entries implemented as a [ChampNode] based trie carrying
/// not only keys but also their respective values (a.k.a. payload) as data content.
///
/// All the CHAMP algorithms must be adjusted to take the payload into account and shift
/// the computed indices accordingly.
///
final class PersistentMapTrie<K, V> implements PersistentMap<K, V> {

    private final ChampNode<K> root;
    private final int size;
    private final int hashCode;

    @SuppressWarnings("UnusedMethod")
    private PersistentMapTrie(ChampNode<K> root, int size, int hashCode) {
        assert size >= 2;
        this.root = root;
        this.size = size;
        this.hashCode = hashCode;
    }

    @SuppressWarnings("ReferenceEquality")
    @Override
    public PersistentMap<K, V> assoc(K key, V value) {
        entryCannotBeNull(key, value);

        int hash = key.hashCode();
        MutableBoolean replaced = new MutableBoolean(false);
        ChampNode<K> newRoot = insertInto(root, key, hash, value, 0, replaced);
        if (replaced.value) {
            return new PersistentMapTrie<>(newRoot, size, hashCode);
        }
        if (newRoot == root) {
            return this;
        }
        return new PersistentMapTrie<>(newRoot, size + 1, hashCode + hash);
    }

    @SuppressWarnings({"unchecked", "ReferenceEquality"})
    @Override
    public PersistentMap<K, V> dissoc(K key) {
        int hash = key.hashCode();
        ChampNode<K> newRoot = deleteFrom(root, key, hash, 0, 1);
        if (newRoot == root) {
            return this;
        }
        int newSize = size - 1;
        if (newSize == 1) {
            // ✅ Collapse to PersistentMap1. This maintains the invariant that
            // PersistentMapTrie always has size >= 2, which allows equals() to only
            // compare with other PersistentMapTrie instances.
            Object[] content = newRoot.content;
            return new PersistentMap1<>((K) content[0], (V) content[1]);
        }
        return new PersistentMapTrie<>(newRoot, newSize, hashCode - hash);
    }

    @Override
    public PersistentMap<K, V> modify(K key, BiFunction<? super K, ? super @Nullable V, ? extends @Nullable V> function) {
        // TODO: optimize to single trie traversal
        V val = function.apply(key, get(key));
        return val != null
            ? assoc(key, val)
            : dissoc(key);
    }

    // TODO: remove NullAway suppression
    @SuppressWarnings({"unchecked", "NullAway"})
    @Override
    public @Nullable V get(K key) {
        return ChampLookup.<K, @Nullable V>lookup(
            root,
            key,
            1,
            null,
            (content, index) -> (V) content[index + 1]
        );
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public V getOrDefault(K key, V defaultValue) {
        return ChampLookup.lookup(
            root,
            key,
            1,
            defaultValue,
            (content, index) -> (V) content[index + 1]
        );
    }

    @Override
    public boolean containsKey(K key) {
        return ChampLookup.containsKey(root, key, 1);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public Iterator<Map.Entry<K, V>> iterator() {
        return new ChampMapIterator<>(root, size);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PersistentMapTrie<?, ?> that = (PersistentMapTrie<?, ?>) o;
        return this.hashCode == that.hashCode
            && this.root.equals(that.root);
    }

    @Override
    public String toString() {
        return ToString.nonEmptyIterator('{', iterator(), '}');
    }

    static <K, V> PersistentMap<K, V> ofDistinct(K k1, V v1, K k2, V v2) {
        int hash1 = k1.hashCode();
        int hash2 = k2.hashCode();
        ChampNode<K> root;
        if (hash1 != hash2) {
            root = branchOnKeys(k1, hash1, v1, k2, hash2, v2, 0, new MutableBoolean(false));
        } else {
            root = new ChampNode<>(
                0,
                1 << mask(hash1, 0),
                new Object[]{new HashCollisionNode(hash1, new Object[]{k1, v1, k2, v2})}
            );
        }
        return new PersistentMapTrie<>(root, 2, hash1 + hash2);
    }

    /**
     * Inserts a key/value pair in the given trie.
     *
     * @return the same node in case the entry is already present in the trie, otherwise a new trie including the given entry.
     */
    private static <K, V> ChampNode<K> insertInto(ChampNode<K> trie, K key, int hash, V val, int shift, MutableBoolean replaced) {
        assert shift < 32;
        int mask = mask(hash, shift);
        int bit = 1 << mask;
        return insertInto(trie, key, hash, mask, bit, val, shift, replaced);
    }

    @SuppressWarnings({"ReferenceEquality", "unchecked"})
    private static <K, V> ChampNode<K> insertInto(ChampNode<K> trie, K key, int hash, int mask, int bit, V val, int shift, MutableBoolean replaced) {

        int dataMap = trie.dataMap;
        if ((dataMap & bit) == bit) {
            int index = index(dataMap, mask, bit);
            int keyIndex = index << 1;
            Object[] content = trie.content;
            Object data = content[keyIndex];
            if (data == key || data.equals(key)) {
                int valIndex = keyIndex + 1;
                if (Objects.equals(content[valIndex], val)) {
                    // Entry already exists
                    return trie;
                }
                replaced.value = true;
                return trie.replaceContentAt(valIndex, val);
            }
            int dataHash = data.hashCode();
            Object dataVal = content[keyIndex + 1];
            Object newNode = dataHash == hash
                ? new HashCollisionNode(hash, new Object[]{data, dataVal, key, val})
                : branchOnKeys(key, hash, val, data, dataHash, dataVal, shift + BITS, replaced);
            return trie.replaceDataWithNode(keyIndex, mask, bit, newNode, 1);
        }

        int nodeMap = trie.nodeMap;
        if ((nodeMap & bit) == bit) {
            Object[] content = trie.content;
            int index = nodeIndex(content, nodeMap, mask, bit);
            Object node = content[index];

            if (node instanceof ChampNode<?>) {
                ChampNode<K> champNode = (ChampNode<K>) node;
                ChampNode<K> newNode = insertInto(champNode, key, hash, val, shift + BITS, replaced);
                if (newNode == champNode) {
                    // Entry already exists
                    return trie;
                }
                return trie.replaceContentAt(index, newNode);
            }

            HashCollisionNode collision = (HashCollisionNode) node;
            if (collision.hash == hash) {
                HashCollisionNode newCollision = collision.put(key, val, replaced);
                if (collision == newCollision) {
                    // Entry already exists
                    return trie;
                }
                return trie.replaceContentAt(index, newCollision);
            }

            ChampNode<K> newNode = branchOnCollision(collision, key, hash, val, shift + BITS, replaced);
            return trie.replaceContentAt(index, newNode);
        }

        int newDataMap = dataMap | bit;
        int newIndex = index(newDataMap, mask, bit);
        Object[] newContent = ArrayCopy.insertAt(newIndex << 1, trie.content, key, val);
        return new ChampNode<>(newDataMap, nodeMap, newContent);
    }

    private static <K, V> ChampNode<K> branchOnKeys(K k1, int hash1, V v1, K k2, int hash2, V v2, int shift, MutableBoolean replaced) {
        int mask1 = mask(hash1, shift);
        int bit1 = 1 << mask1;
        int mask2 = mask(hash2, shift);
        int bit2 = 1 << mask2;
        if (bit1 != bit2) {
            return new ChampNode<>(
                bit1 | bit2,
                0,
                mask1 > mask2 ? new Object[]{k2, v2, k1, v1} : new Object[]{k1, v1, k2, v2}
            );
        }
        return insertInto(new ChampNode<>(bit1, 0, new Object[]{k1, v1}), k2, hash2, mask2, bit2, v2, shift, replaced);
    }

    private static <K, V> ChampNode<K> branchOnCollision(HashCollisionNode collision, K key, int hash, V val, int shift, MutableBoolean replaced) {
        int keyMask = mask(hash, shift);
        int keyBit = 1 << keyMask;
        int collisionMask = mask(collision.hash, shift);
        int collisionBit = 1 << collisionMask;
        if (keyBit != collisionBit) {
            return new ChampNode<>(keyBit, collisionBit, new Object[]{key, val, collision});
        }
        ChampNode<K> node = new ChampNode<>(0, collisionBit, new Object[]{collision});
        return insertInto(node, key, hash, keyMask, keyBit, val, shift, replaced);
    }

    private static final class ChampMapIterator<K, V> extends ChampIterator<Map.Entry<K, V>> {

        public ChampMapIterator(ChampNode<?> root, int size) {
            super(root, size);
        }

        @SuppressWarnings("unchecked")
        @Override
        protected Map.Entry<K, V> getElement(Object[] content, int index) {
            int keyIndex = index << 1;
            K key = (K) content[keyIndex];
            V val = (V) content[keyIndex + 1];
            return new PersistentMapEntry<>(key, val);
        }

        @Override
        protected int collisionKeyCount(int keys) {
            return keys >> 1;
        }
    }
}
