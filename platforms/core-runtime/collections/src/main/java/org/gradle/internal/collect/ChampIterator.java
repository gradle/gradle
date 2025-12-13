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
import java.util.NoSuchElementException;

// ✅ Iterates over CHAMP trie using an explicit stack (linked list of IteratorState).
// Iterates data elements within a node in reverse order (--nextKey), then descends into
// sub-nodes also in reverse order (content[content.length - nextNode]).
// This is correct for unordered collections (sets/maps) where iteration order doesn't matter,
// and it's faster since it avoids comparisons to the content length.
abstract class ChampIterator<K> implements Iterator<K> {

    private final int count;
    private int index = 0;
    private int nextKey;
    private int nextNode;
    private Object[] content;
    private @Nullable IteratorState<K> parent = null;

    ChampIterator(ChampNode<?> trie, int count) {
        nextKey = Integer.bitCount(trie.dataMap);
        nextNode = Integer.bitCount(trie.nodeMap);
        content = trie.content;
        this.count = count;
    }

    protected abstract K getElement(Object[] content, int index);

    protected abstract int collisionKeyCount(int keys);

    @Override
    public final boolean hasNext() {
        return index < count;
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public final K next() {
        if (nextKey != 0) {
            return nextKey();
        }
        int nextNode = this.nextNode;
        while (true) {
            if (nextNode != 0) {
                Object node = content[content.length - nextNode--];

                // Can this be made faster by keeping the iterator state in parallel arrays for `nextNode` and `content`?
                parent = nextNode != 0 ? new IteratorState<>(nextNode, content, parent) : parent;
                if (node instanceof ChampNode<?>) {
                    ChampNode<K> champNode = (ChampNode<K>) node;
                    content = champNode.content;
                    int nodeMap = champNode.nodeMap;
                    nextNode = nodeMap == -1 ? 32 : Integer.bitCount(nodeMap);
                    int dataMap = champNode.dataMap;
                    if (dataMap == 0) {
                        continue;
                    }
                    nextKey = dataMap == -1 ? 32 : Integer.bitCount(dataMap);
                } else {
                    Object[] keys = ((HashCollisionNode) node).content;
                    content = keys;
                    nextKey = collisionKeyCount(keys.length);
                    nextNode = 0;
                }
                this.nextNode = nextNode;
                return nextKey();
            }
            if (parent != null) {
                nextNode = parent.nextNode;
                content = parent.content;
                parent = parent.parent;
                continue;
            }
            throw new NoSuchElementException();
        }
    }

    private K nextKey() {
        index++;
        return getElement(content, --nextKey);
    }

    private static final class IteratorState<K> {

        private final int nextNode;
        private final Object[] content;
        private final @Nullable IteratorState<K> parent;

        IteratorState(int nextNode, Object[] content, @Nullable IteratorState<K> parent) {
            this.nextNode = nextNode;
            this.content = content;
            this.parent = parent;
        }
    }
}
