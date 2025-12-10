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

import java.util.Arrays;

/// A node of a _Compressed Hash-Array Mapped Prefix-tree_.
final class ChampNode<K> {

    static final int BITS = 5;

    /// Bitmap containing indices of data/keys.
    final int dataMap;

    /// Bitmap containing indices of nodes. Nodes are indexed right to left.
    final int nodeMap;

    /// Contents of this node containing a mix of [data][#dataMap] and [sub-nodes][#nodeMap].
    final Object[] content;

    ChampNode(int dataMap, int nodeMap, Object[] content) {
        this.dataMap = dataMap;
        this.nodeMap = nodeMap;
        this.content = content;
    }

    ChampNode<K> replaceDataWithNode(int index, int mask, int bit, Object newNode, int payload) {
        assert (nodeMap & bit) != bit;
        int newDataMap = dataMap & ~bit;
        int newNodeMap = nodeMap | bit;
        int newNodeIndex = nodeIndex(content, newNodeMap, mask, bit);
        Object[] newContent = ArrayCopy.insertAtPushingLeft(newNodeIndex, content, newNode, index, payload);
        return new ChampNode<>(newDataMap, newNodeMap, newContent);
    }

    ChampNode<K> replaceContentAt(int index, Object newElement) {
        Object[] newContent = ArrayCopy.replaceAt(index, content, newElement);
        return new ChampNode<>(dataMap, nodeMap, newContent);
    }

    public ChampNode<K> removeDataAt(int index, int bit, int payload) {
        assert (dataMap & bit) == bit;
        int newDataMap = dataMap & ~bit;
        Object[] newContent = ArrayCopy.removeAt(index, content, 1 + payload);
        return new ChampNode<>(newDataMap, nodeMap, newContent);
    }

    @Override
    public int hashCode() {
        throw new IllegalStateException("ChampNode should not be used as hash key.");
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        ChampNode<?> that = (ChampNode<?>) other;
        return dataMap == that.dataMap
            && nodeMap == that.nodeMap
            && Arrays.equals(content, that.content);
    }

    static int nodeIndex(Object[] content, int nodeMap, int mask, int bit) {
        return content.length - 1 - index(nodeMap, mask, bit);
    }

    static int mask(int hash, int shift) {
        return (hash >>> shift) & 0b11111;
    }

    static int index(int bitmap, int bit) {
        return Integer.bitCount(bitmap & (bit - 1));
    }

    static int index(int bitmap, int mask, int bit) {
        // when the array is full, the mask already represents the index
        return bitmap == -1 ? mask : index(bitmap, bit);
    }
}
