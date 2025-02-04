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

    private var currentIndex: Int = 1
    var root = Node(0, "", mutableMapOf())

    data class Node(
        val index: Int,
        val segment: String,
        val children: MutableMap<String, Node> // Just a list?
    )

    // Use array as a return type?
    // Support absolute/relative paths
    fun insert(file: File): List<Int> {
        val segments = file.path.split("/")
        var current = root
        val key = mutableListOf<Int>()

        for (segment in segments) {
            if (segment.isEmpty()) {
                // leading '/'
                continue
            }

            var child = current.children[segment]
            if (child == null) {
                child = Node(currentIndex++, segment, mutableMapOf())
                current.children[segment] = child
            }
            key.add(child.index)
            current = child
        }

        return key
    }

    fun getByKey(key: List<Int>): File {
        val segments = arrayListOf<String>() // array of known length key.length
        var current = root

        for (index in key) {
            current = current.children.values.first { it.index == index }
            segments.add(current.segment)
        }

        return File("/${segments.joinToString("/")}")
    }
}

