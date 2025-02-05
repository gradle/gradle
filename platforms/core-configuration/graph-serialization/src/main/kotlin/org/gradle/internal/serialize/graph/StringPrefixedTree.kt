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

import org.gradle.internal.service.scopes.Scope.BuildTree
import org.gradle.internal.service.scopes.ServiceScope
import java.io.File

@ServiceScope(BuildTree::class)
class StringPrefixedTree {

    private var currentIndex: Int = 0

    val root = Node(null, "", mutableListOf())

    fun insert(file: File): Int {
        val segments = file.path.split("/")
        var current = root

        for (segment in segments) {
            if (segment.isEmpty()) {
                // leading '/'
                continue
            }

            var child = current.children.find { it.segment == segment }
            if (child == null) {
                child = Node(null, segment, mutableListOf())
                current.children.add(child)
            }

            current = child
        }
        if (current.isIntermediate) {
            current.index = currentIndex++
        }

        return current.index!!
    }

    fun buildIndexes(root: Node): Map<Int, File> {
        val indexes = mutableMapOf<Int, File>()
        buildIndexFor(root, mutableListOf(), indexes)
        return indexes
    }

    fun compress(): Node = compressFrom(root)

    private fun buildIndexFor(node: Node, segments: MutableList<String>, indexes: MutableMap<Int, File>) {
        segments.add(node.segment)
        node.index?.let { idx ->
            indexes[idx] = File("/${segments.joinToString("/")}")
        }
        for (child in node.children) {
            buildIndexFor(child, segments, indexes)
        }
        segments.removeAt(segments.size - 1) // backtrack
    }

    // TODO simplify?
    private fun compressFrom(node: Node): Node {
        if (!node.isIntermediate) return node
        val compressedSegments = mutableListOf<String>()
        var current = node

        while (current.children.size == 1) {
            compressedSegments.add(current.segment)
            val child = current.children[0]
            if (!child.isIntermediate) {
                compressedSegments.add(child.segment)
                current = child
                break
            }
            current = child
        }

        if (current.children.size != 1 && (compressedSegments.isEmpty() || compressedSegments.last() != current.segment)) {
            compressedSegments.add(current.segment)
        }

        val index = if (current.isIntermediate) null else current.index
        val children = current.children.map { compressFrom(it) }.toMutableList()
        val segment = compressedSegments
            // root segment is empty
            .filter { it.isNotEmpty() }
            .joinToString("/")

        return Node(index, segment, children)
    }

    data class Node(
        var index: Int?,
        val segment: String,
        val children: MutableList<Node>
    ) {
        val isIntermediate
            get() = index == null
    }
}

