/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.kotlin.dsl.provider.plugins.precompiled.tasks


/**
 * Reduces the given [graph] so that it only includes external node references,
 * e.g.:
 * ```
 * reduce({a: [b, c], b: [e1], c: [d], d: [e2]}) => {a: [e1, e2], b: [e1], c: [e2], d: [e2]}
 * ```
 */
internal
fun <V> reduceGraph(graph: Map<V, List<V>>): Map<V, Set<V>> {

    val result = mutableMapOf<V, Set<V>>()

    val reducing = hashSetOf<V>()

    fun reduce(node: V, refs: Iterable<V>): Set<V> {

        result[node]?.let {
            return it
        }

        val externalNodes = mutableSetOf<V>()
        result[node] = externalNodes

        reducing.add(node)
        for (ref in refs) {
            if (ref in reducing) {
                continue
            }
            graph[ref]?.let {
                externalNodes.addAll(reduce(ref, it))
            } ?: externalNodes.add(ref)
        }
        reducing.remove(node)

        return externalNodes
    }

    for ((node, refs) in graph) {
        reduce(node, refs)
    }

    return result
}
