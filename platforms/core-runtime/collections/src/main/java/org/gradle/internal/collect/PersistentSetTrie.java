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
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.gradle.internal.collect.ChampDeletion.deleteFrom;
import static org.gradle.internal.collect.ChampNode.BITS;
import static org.gradle.internal.collect.ChampNode.index;
import static org.gradle.internal.collect.ChampNode.mask;
import static org.gradle.internal.collect.ChampNode.nodeIndex;
import static org.gradle.internal.collect.Preconditions.keyCannotBeNull;

/// A [PersistentSet] with two or more elements implemented as a [ChampNode] based trie.
///
final class PersistentSetTrie<K> implements PersistentSet<K> {

    private final ChampNode<K> root;
    private final int size;
    private final int hashCode;

    private PersistentSetTrie(ChampNode<K> root, int size, int hashCode) {
        assert size >= 2;
        this.root = root;
        this.size = size;
        this.hashCode = hashCode;
    }

    @SuppressWarnings("ReferenceEquality")
    @Override
    public PersistentSet<K> plus(K key) {
        keyCannotBeNull(key);
        int hash = key.hashCode();
        ChampNode<K> newRoot = insertInto(root, key, hash, 0);
        if (newRoot == root) {
            return this;
        }
        return new PersistentSetTrie<>(newRoot, size + 1, hashCode + hash);
    }

    @SuppressWarnings("ReferenceEquality")
    @Override
    public PersistentSet<K> minus(K key) {
        int hash = key.hashCode();
        ChampNode<K> newRoot = deleteFrom(root, key, hash, 0, 0);
        if (newRoot == root) {
            return this;
        }
        int newSize = size - 1;
        if (newSize == 1) {
            // ✅ Collapse to PersistentSet1. This maintains the invariant that
            // PersistentSetTrie always has size >= 2, which allows equals() to only
            // compare with other PersistentSetTrie instances.
            return new PersistentSet1<>(singleKeyOf(newRoot));
        }
        return new PersistentSetTrie<>(newRoot, newSize, hashCode - hash);
    }

    @SuppressWarnings("ReferenceEquality")
    @Override
    public PersistentSet<K> minusAll(Iterable<K> keys) {
        if (keys instanceof PersistentSet<?>) {
            // Delegate to a potentially more efficient set operation
            return except((PersistentSet<K>) keys);
        }
        return minusAll(keys.iterator());
    }

    @Override
    public PersistentSet<K> except(PersistentSet<K> other) {
        if (equals(other)) {
            return PersistentSet.of();
        }
        return minusAll(other.iterator());
    }

    @SuppressWarnings("ReferenceEquality")
    private PersistentSet<K> minusAll(Iterator<K> iterator) {
        PersistentSet<K> result = this;
        while (iterator.hasNext() && !result.isEmpty()) {
            result = result.minus(iterator.next());
        }
        return result;
    }

    @Override
    public PersistentSet<K> filter(Predicate<? super K> predicate) {
        PersistentSet<K> ks = this;
        for (K k : this) {
            if (!predicate.test(k)) {
                ks = ks.minus(k);
            }
        }
        return ks;
    }

    @Override
    public <R> PersistentSet<R> map(Function<? super K, ? extends R> mapper) {
        PersistentSet<R> rs = PersistentSet.of();
        for (K k : this) {
            rs = rs.plus(mapper.apply(k));
        }
        return rs;
    }

    @Override
    public <R> PersistentSet<R> flatMap(Function<? super K, PersistentSet<R>> mapper) {
        PersistentSet<R> rs = PersistentSet.of();
        for (K k : this) {
            rs = rs.union(mapper.apply(k));
        }
        return rs;
    }

    @Override
    public <G> PersistentMap<G, PersistentSet<K>> groupBy(Function<? super K, ? extends @Nullable G> group) {
        PersistentMap<G, PersistentSet<K>> rs = PersistentMap.of();
        for (K k : this) {
            G g = group.apply(k);
            if (g != null) {
                rs = rs.modify(g, (g1, set) -> set == null ? PersistentSet.of(k) : set.plus(k));
            }
        }
        return rs;
    }

    @Override
    public boolean anyMatch(Predicate<? super K> predicate) {
        for (K k : this) {
            if (predicate.test(k)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean noneMatch(Predicate<? super K> predicate) {
        for (K k : this) {
            if (predicate.test(k)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean contains(K key) {
        return ChampLookup.containsKey(root, key, 0);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <S extends K> PersistentSet<K> union(PersistentSet<S> other) {
        if (other.isEmpty()) {
            return this;
        }
        return join((PersistentSet<K>) other, (smaller, larger) -> {
            for (K key : smaller) {
                larger = larger.plus(key);
            }
            return larger;
        });
    }

    @Override
    public PersistentSet<K> intersect(PersistentSet<K> other) {
        if (other.isEmpty()) {
            return other;
        }
        return join(other, (smaller, larger) -> {
            for (K key : smaller) {
                if (!larger.contains(key)) {
                    smaller = smaller.minus(key);
                    if (smaller.isEmpty()) {
                        break;
                    }
                }
            }
            return smaller;
        });
    }

    private PersistentSet<K> join(PersistentSet<K> other, BiFunction<PersistentSet<K>, PersistentSet<K>, PersistentSet<K>> joinFunction) {
        if (equals(other)) {
            return this;
        }
        PersistentSet<K> smaller;
        PersistentSet<K> larger;
        if (size > other.size()) {
            smaller = other;
            larger = this;
        } else {
            smaller = this;
            larger = other;
        }
        return joinFunction.apply(smaller, larger);
    }

    @Override
    public Iterator<K> iterator() {
        return new ChampSetIterator<>(root, size);
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
        PersistentSetTrie<?> that = (PersistentSetTrie<?>) o;
        return this.hashCode == that.hashCode
            && this.root.equals(that.root);
    }

    @Override
    public String toString() {
        return ToString.nonEmptyIterator('{', iterator(), '}');
    }

    static <K> PersistentSet<K> ofDistinct(K k1, K k2) {
        int hash1 = k1.hashCode();
        int hash2 = k2.hashCode();
        ChampNode<K> root = hash1 != hash2
            ? branchOnKeys(k1, hash1, k2, hash2, 0)
            : new ChampNode<>(0, 1 << mask(hash1, 0), new Object[]{new HashCollisionNode(hash1, new Object[]{k1, k2})});
        return new PersistentSetTrie<>(root, 2, hash1 + hash2);
    }

    /**
     * Inserts a key in the given trie.
     *
     * @return the same node in case the key is already present in the trie, otherwise a new trie including the given key.
     */
    private static <K> ChampNode<K> insertInto(ChampNode<K> trie, K key, int hash, int shift) {
        assert shift < 32;
        int mask = mask(hash, shift);
        int bit = 1 << mask;
        return insertInto(trie, key, hash, mask, bit, shift);
    }

    @SuppressWarnings({"ReferenceEquality", "unchecked"})
    private static <K> ChampNode<K> insertInto(ChampNode<K> trie, K key, int hash, int mask, int bit, int shift) {

        int dataMap = trie.dataMap;
        if ((dataMap & bit) == bit) {
            int index = index(dataMap, mask, bit);
            Object data = trie.content[index];
            if (data == key || data.equals(key)) {
                // Key already exists
                return trie;
            }
            int dataHash = data.hashCode();
            Object newNode = dataHash == hash
                ? new HashCollisionNode(hash, new Object[]{data, key})
                : branchOnKeys(key, hash, data, dataHash, shift + BITS);
            return trie.replaceDataWithNode(index, mask, bit, newNode, 0);
        }

        int nodeMap = trie.nodeMap;
        if ((nodeMap & bit) == bit) {
            Object[] content = trie.content;
            int index = nodeIndex(content, nodeMap, mask, bit);
            Object node = content[index];

            if (node instanceof ChampNode<?>) {
                ChampNode<K> champNode = (ChampNode<K>) node;
                ChampNode<K> newNode = insertInto(champNode, key, hash, shift + BITS);
                if (champNode == newNode) {
                    // Key already exists
                    return trie;
                }
                return trie.replaceContentAt(index, newNode);
            }

            HashCollisionNode collision = (HashCollisionNode) node;
            if (collision.hash == hash) {
                HashCollisionNode newCollision = collision.add(key);
                if (collision == newCollision) {
                    // Key already exists
                    return trie;
                }
                return trie.replaceContentAt(index, newCollision);
            }

            ChampNode<K> newNode = branchOnCollision(collision, key, hash, shift + BITS);
            return trie.replaceContentAt(index, newNode);
        }

        int newDataMap = dataMap | bit;
        int newIndex = index(newDataMap, mask, bit);
        Object[] newContent = ArrayCopy.insertAt(newIndex, trie.content, key);
        return new ChampNode<>(newDataMap, nodeMap, newContent);
    }

    private static <K> ChampNode<K> branchOnKeys(K k1, int hash1, K k2, int hash2, int shift) {
        int mask1 = mask(hash1, shift);
        int bit1 = 1 << mask1;
        int mask2 = mask(hash2, shift);
        int bit2 = 1 << mask2;
        if (bit1 != bit2) {
            return new ChampNode<>(
                bit1 | bit2,
                0,
                mask1 > mask2 ? new Object[]{k2, k1} : new Object[]{k1, k2}
            );
        }
        return insertInto(new ChampNode<>(bit1, 0, new Object[]{k1}), k2, hash2, mask2, bit2, shift);
    }

    private static <K> ChampNode<K> branchOnCollision(HashCollisionNode collision, K key, int hash, int shift) {
        int keyMask = mask(hash, shift);
        int keyBit = 1 << keyMask;
        int collisionMask = mask(collision.hash, shift);
        int collisionBit = 1 << collisionMask;
        if (keyBit != collisionBit) {
            return new ChampNode<>(keyBit, collisionBit, new Object[]{key, collision});
        }
        ChampNode<K> node = new ChampNode<>(0, collisionBit, new Object[]{collision});
        return insertInto(node, key, hash, keyMask, keyBit, shift);
    }

    @SuppressWarnings("unchecked")
    private static <K> K singleKeyOf(ChampNode<K> node) {
        Object[] content = node.content;
        assert content.length == 1;
        return (K) content[0];
    }

    private static final class ChampSetIterator<K> extends ChampIterator<K> {

        public ChampSetIterator(ChampNode<K> root, int size) {
            super(root, size);
        }

        @SuppressWarnings("unchecked")
        @Override
        protected K getElement(Object[] content, int index) {
            return (K) content[index];
        }

        @Override
        protected int collisionKeyCount(int keys) {
            return keys;
        }
    }
}
