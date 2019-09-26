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

package org.gradle.instantexecution.serialization.codecs

import org.gradle.api.internal.GradleInternal
import org.gradle.execution.plan.Node
import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.IsolateOwner
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.instantexecution.serialization.readCollection
import org.gradle.instantexecution.serialization.withIsolate
import org.gradle.instantexecution.serialization.writeCollection


internal
class WorkNodeCodec(
    private val owner: GradleInternal,
    private val internalTypesCodec: Codec<Any?>
) {

    suspend fun WriteContext.writeWork(nodes: List<Node>) {
        // Share bean instances across all nodes (except tasks, which have their own isolate)
        withIsolate(IsolateOwner.OwnerGradle(owner), internalTypesCodec) {
            writeNodes(nodes)
        }
    }

    private
    suspend fun WriteContext.writeNodes(nodes: List<Node>) {
        val nodesById = HashMap<Node, Int>(nodes.size)
        writeSmallInt(nodes.size)
        for (node in nodes) {
            writeNode(node, nodesById)
        }
    }

    private
    suspend fun WriteContext.writeNode(node: Node, nodesById: MutableMap<Node, Int>) {
        if (nodesById.containsKey(node)) {
            // Already visited
            return
        }
        for (successor in node.dependencySuccessors) {
            writeNode(successor, nodesById)
        }
        val id = nodesById.size
        writeSmallInt(id)
        write(node)
        writeCollection(node.dependencySuccessors) { writeSmallInt(nodesById.getValue(it)) }
        nodesById[node] = id
    }

    suspend fun ReadContext.readWork(): List<Node> {
        return withIsolate(IsolateOwner.OwnerGradle(owner), internalTypesCodec) {
            readNodes()
        }
    }

    private
    suspend fun ReadContext.readNodes(): ArrayList<Node> {
        val count = readSmallInt()
        val nodesById = HashMap<Int, Node>(count)
        val nodes = ArrayList<Node>(count)
        for (i in 0 until count) {
            val node = readNode(nodesById)
            nodes.add(node)
        }
        return nodes
    }

    private
    suspend fun ReadContext.readNode(nodesById: MutableMap<Int, Node>): Node {
        val id = readSmallInt()
        val node = read() as Node
        readCollection {
            val depId = readSmallInt()
            val dep = nodesById.getValue(depId)
            node.addDependencySuccessor(dep)
        }
        node.dependenciesProcessed()
        nodesById[id] = node
        return node
    }
}
