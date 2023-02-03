/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.kotlin.dsl.resolver

import org.gradle.kotlin.dsl.execution.splitIncluding

import java.io.File


internal
fun compactStringFor(files: Iterable<File>) =
    compactStringFor(files.map { it.path }, File.separatorChar)


internal
fun compactStringFor(paths: Iterable<String>, separator: Char) =
    CompactTree.of(paths.map { it.splitIncluding(separator).toList() }).toString()


private
sealed class CompactTree {

    companion object {

        fun of(paths: Iterable<List<String>>): CompactTree =
            paths
                .filter { it.isNotEmpty() }
                .groupBy({ it[0] }, { it.drop(1) })
                .map { (label, remaining) ->
                    when (val subTree = of(remaining)) {
                        is Empty -> Label(label)
                        is Label -> Label(
                            label + subTree.label
                        )
                        is Branch -> Edge(
                            Label(label),
                            subTree
                        )
                        is Edge -> Edge(
                            Label(label + subTree.label),
                            subTree.tree
                        )
                    }
                }.let {
                    when (it.size) {
                        0 -> Empty
                        1 -> it.first()
                        else -> Branch(it)
                    }
                }
    }

    object Empty : CompactTree() {
        override fun toString() = "ø"
    }

    data class Label(val label: String) : CompactTree() {
        override fun toString() = label
    }

    data class Branch(val edges: List<CompactTree>) : CompactTree() {
        override fun toString() = edges.joinToString(separator = ", ", prefix = "{", postfix = "}")
    }

    data class Edge(val label: Label, val tree: CompactTree) : CompactTree() {
        override fun toString() = "$label$tree"
    }
}
