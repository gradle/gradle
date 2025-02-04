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

package org.gradle.internal.serialize.graph

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * An implementation of <a href="https://en.wikipedia.org/wiki/Trie">Prefix tree</a> for files.
 * <p>
 * The tree can be compressed. The compressing factor is totally depends on the structure of the tree.
 */
class FilePrefixedTree {

    private var currentIndex = AtomicInteger(1)

    val root = Node(false, 0, "")

    /**
     * Inserts a file path into the tree, creating necessary nodes if they do not exist.
     * Insertion is thread-safe.
     *
     * @return the index of the final node representing the file.
     * If the path already exists, returns the existing index.
     */
    fun insert(file: File): Int {
        val segmentBuilder = StringBuilder()
        var current = root

        for (char in file.path) {
            if (char == File.separatorChar) {
                val childSegment = segmentBuilder.ifEmpty { /* leading separator for absolute file */ File.separator }.toString()
                current = current.children.computeIfAbsent(childSegment) { Node(false, currentIndex.getAndIncrement(), childSegment) }
                segmentBuilder.clear()
                continue
            }
            segmentBuilder.append(char)
        }

        if (segmentBuilder.isNotEmpty()) {
            val childSegment = segmentBuilder.toString()
            current = current.children.computeIfAbsent(childSegment) { Node(false, currentIndex.getAndIncrement(), childSegment) }
        }

        current.isFinal = true
        return current.index
    }

    /**
     * Compresses the current tree following the <a href="http://en.wikipedia.org/wiki/Radix_tree">Radix tree</a> idea.
     * <p>
     * Tree should be compressed only after all insertions are completed.
     * @return a new tree, which is a compressed version of the current one.
     */
    fun compress(): Node = compressFrom(root)

    private fun compressFrom(node: Node): Node {
        if (node.isFinal) {
            return node.copy(children = node.children.compress())
        }

        val segmentsToCompress = mutableListOf<String>()
        var current = node

        while (current.children.size == 1) {
            segmentsToCompress.add(current.segment)
            current = current.singleChild
            if (current.isFinal) break
        }

        segmentsToCompress.add(current.segment)
        return Node(current.isFinal, current.index, segmentsToCompress.compressPath(), current.children.compress())
    }

    private fun List<String>.compressPath(): String {
        val segments = dropWhile { it.isEmpty() }
        val isAbsolute = segments.firstOrNull() == File.separator
        return if (isAbsolute) {
            File.separator + segments.dropWhile { it == File.separator }.joinToString(File.separator)
        } else {
            segments.joinToString(File.separator)
        }
    }

    private fun Map<String, Node>.compress() = ConcurrentHashMap(values.map(::compressFrom).associateBy { it.segment })


    data class Node(
        @Volatile var isFinal: Boolean,
        val index: Int,
        val segment: String,
        val children: MutableMap<String, Node> = ConcurrentHashMap()
    ) {
        val singleChild
            get() = children.entries.single().value

    }

    companion object {
        /**
         * The only efficient way to perform a lookup in the tree.
         * <p>
         * Indexes are built by performing a depth-first search (DFS) through the entire tree,
         * creating a mapping between each index and its corresponding item.
         */
        fun buildIndexes(root: Node): Map<Int, File> {
            val indexes = mutableMapOf<Int, File>()
            buildIndexFor(root, mutableListOf(), indexes)
            return indexes
        }

        private fun buildIndexFor(node: Node, segments: MutableList<String>, indexes: MutableMap<Int, File>) {
            segments.add(node.segment)
            indexes[node.index] = File(segments.joinToString(File.separator))
            for (child in node.children.values) {
                buildIndexFor(child, segments, indexes)
            }
            segments.removeAt(segments.size - 1) // backtrack
        }
    }
}
