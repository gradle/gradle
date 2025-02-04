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

    val root = Node(null, "", mutableMapOf())

    fun insert(file: File): Int {
        val segments = file.path.split("/")
        var current = root

        for (segment in segments) {
            if (segment.isEmpty()) {
                // leading '/'
                continue
            }

            var child = current.children[segment]
            if (child == null) {
                child = Node(null, segment, mutableMapOf())
                current.children[segment] = child
            }

            current = child
        }
        if (current.index == null) {
            current.index = currentIndex++
        }

        return current.index!!
    }

    data class Node(
        var index: Int?,
        val segment: String,
        val children: MutableMap<String, Node> // Just a list?
    )
}

