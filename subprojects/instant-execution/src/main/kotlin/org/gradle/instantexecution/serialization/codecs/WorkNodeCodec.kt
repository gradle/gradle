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
import org.gradle.execution.plan.TaskNode
import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.IsolateOwner
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.instantexecution.serialization.readCollection
import org.gradle.instantexecution.serialization.readList
import org.gradle.instantexecution.serialization.readNonNull
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

    suspend fun ReadContext.readWork(): List<Node> =
        withIsolate(IsolateOwner.OwnerGradle(owner), internalTypesCodec) {
            readNodes()
        }

    private
    suspend fun WriteContext.writeNodes(nodes: List<Node>) {
        val nodeIds = nodes.asSequence()
            .mapIndexed { index, node -> node to index }
            .toMap()
        writeCollection(nodes)
        for (node in nodes) {
            writeSuccessorsOf(node, nodeIds)
        }
    }

    private
    suspend fun ReadContext.readNodes(): List<Node> {
        val nodes = readList { readNonNull<Node>() }
        nodes.forEach { node ->
            readSuccessorsOf(node, nodes)
        }
        return nodes
    }

    private
    fun WriteContext.writeSuccessorsOf(node: Node, nodeIds: Map<Node, Int>) {
        writeSuccessors(nodeIds, node.dependencySuccessors)
        when (node) {
            is TaskNode -> {
                writeSuccessors(nodeIds, node.shouldSuccessors)
                writeSuccessors(nodeIds, node.mustSuccessors)
                writeSuccessors(nodeIds, node.finalizingSuccessors)
            }
        }
    }

    private
    fun ReadContext.readSuccessorsOf(node: Node, nodesById: List<Node>) {
        readSuccessors(nodesById) {
            node.addDependencySuccessor(it)
        }
        when (node) {
            is TaskNode -> {
                readSuccessors(nodesById) {
                    node.addShouldSuccessor(it)
                }
                readSuccessors(nodesById) {
                    require(it is TaskNode)
                    node.addMustSuccessor(it)
                }
                readSuccessors(nodesById) {
                    require(it is TaskNode)
                    node.addFinalizingSuccessor(it)
                }
            }
        }
        node.dependenciesProcessed()
    }

    private
    fun WriteContext.writeSuccessors(nodeIds: Map<Node, Int>, successors: MutableSet<Node>) {
        writeCollection(successors) {
            writeSmallInt(nodeIds.getValue(it))
        }
    }

    private
    fun ReadContext.readSuccessors(nodesById: List<Node>, onSuccessor: (Node) -> Unit) {
        readCollection {
            val successorId = readSmallInt()
            val successor = nodesById[successorId]
            onSuccessor(successor)
        }
    }
}
