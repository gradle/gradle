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

import static org.gradle.internal.collect.ChampNode.BITS;
import static org.gradle.internal.collect.ChampNode.index;
import static org.gradle.internal.collect.ChampNode.mask;
import static org.gradle.internal.collect.ChampNode.nodeIndex;

/// Implementation of the CHAMP deletion algorithm shared by [PersistentSetTrie], when `payload == 0`,
/// and [PersistentMapTrie], when `payload == 1`.
///
/// ✅ Key insight: After deletion, the algorithm "inlines" single-element sub-nodes back into
/// their parent to maintain the CHAMP invariant that nodes only exist when they have 2+ data
/// elements or contain sub-nodes. This keeps the trie compact.
final class ChampDeletion {

    @SuppressWarnings({"unchecked", "ReferenceEquality"})
    static <K> ChampNode<K> deleteFrom(ChampNode<K> trie, K key, int hash, int shift, int payload) {

        int mask = mask(hash, shift);
        int bit = 1 << mask;

        int nodeMap = trie.nodeMap;
        if ((nodeMap & bit) == bit) {

            Object[] content = trie.content;
            int index = nodeIndex(content, nodeMap, mask, bit);
            Object node = content[index];
            if (node instanceof ChampNode<?>) {
                ChampNode<K> champNode = (ChampNode<K>) node;
                ChampNode<K> resultNode = deleteFrom(champNode, key, hash, shift + BITS, payload);
                if (resultNode == champNode) {
                    return trie;
                }
                int dataMap = trie.dataMap;
                int arity = arity(dataMap, nodeMap);
                if (arity == 1) {
                    if (branchSize(resultNode) == 1) {
                        return resultNode;
                    } else {
                        return trie.replaceContentAt(index, resultNode);
                    }
                } else {
                    if (branchSize(resultNode) == 1) {
                        Object[] resultNodes = resultNode.content;
                        assert resultNodes.length == 1 << payload;
                        return inlineDataForNodeWithoutSubNode(
                            resultNodes,
                            0,
                            index,
                            content,
                            dataMap,
                            nodeMap,
                            shift,
                            payload
                        );
                    } else {
                        return trie.replaceContentAt(index, resultNode);
                    }
                }
            }

            HashCollisionNode collisionNode = (HashCollisionNode) node;
            int keyIndex = collisionNode.indexOf(key, payload);
            if (keyIndex != -1) {

                Object[] collisionContent = collisionNode.content;
                int inlineThreshold = 2 << payload;
                if (collisionContent.length > inlineThreshold) {
                    return trie.replaceContentAt(index, collisionNode.removeAt(keyIndex, payload));
                }
                assert collisionContent.length == inlineThreshold;
                // ✅ When the collision node has exactly 2 entries, and we remove one, inline the remaining entry.
                // For payload=0 (set): [key0, key1] → if removing key0, inline key1 (index 1)
                // For payload=1 (map): [k0, v0, k1, v1] → if removing k0, inline k1 at index 2 (1<<1)
                return inlineDataForNodeWithoutSubNode(
                    collisionContent,
                    // if we are removing the key at index 0, then we are inlining the key/data at the other index
                    keyIndex == 0 ? 1 << payload : 0,
                    index,
                    content,
                    trie.dataMap,
                    nodeMap,
                    shift,
                    payload
                );
            }
        } else {

            int dataMap = trie.dataMap;
            if ((dataMap & bit) == bit) {
                int index = index(dataMap, mask, bit) << payload;
                Object data = trie.content[index];
                if (data == key || data.equals(key)) {
                    return trie.removeDataAt(index, bit, payload);
                }
            }
        }

        // The key is not present.
        return trie;
    }

    private static <K> ChampNode<K> inlineDataForNodeWithoutSubNode(
        Object[] data,
        int dataIndex,
        int inlinedNodeIndex,
        Object[] content,
        int dataMap,
        int nodeMap,
        int shift,
        int payload
    ) {
        Object key = data[dataIndex];
        int hash = key.hashCode();
        int mask = mask(hash, shift);
        int bit = 1 << mask;
        assert (dataMap & bit) != bit;
        assert (nodeMap & bit) == bit;
        int newDataMap = dataMap | bit;
        int newNodeMap = nodeMap & ~bit;
        int keyIndex = index(newDataMap, mask, bit);
        Object[] newContent =
            payload == 0
                ? ArrayCopy.insertAtPushingRight(keyIndex, content, key, inlinedNodeIndex)
                : ArrayCopy.insertAtPushingRight(keyIndex << payload, content, data, dataIndex, 1 + payload, inlinedNodeIndex);
        return new ChampNode<>(newDataMap, newNodeMap, newContent);
    }

    private static int branchSize(ChampNode<?> node) {
        int nodeArity = Integer.bitCount(node.nodeMap);
        if (nodeArity == 0) {
            int dataArity = Integer.bitCount(node.dataMap);
            switch (dataArity) {
                case 0:
                    return 0;
                case 1:
                    return 1;
                default:
                    return 2;
            }
        }
        return 2;
    }

    private static int arity(int dataMap, int nodeMap) {
        return Integer.bitCount(dataMap)
            + Integer.bitCount(nodeMap);
    }
}
