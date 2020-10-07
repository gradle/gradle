/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.configurationcache.serialization.codecs

import org.gradle.api.internal.GradleInternal
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.readNonNull
import org.gradle.configurationcache.serialization.withGradleIsolate
import org.gradle.execution.plan.Node
import org.gradle.execution.plan.TaskInAnotherBuild
import org.gradle.execution.plan.TaskNode


internal
class WorkNodeCodec(
    private val owner: GradleInternal,
    private val internalTypesCodec: Codec<Any?>
) {

    suspend fun WriteContext.writeWork(nodes: List<Node>) {
        // Share bean instances across all nodes (except tasks, which have their own isolate)
        withGradleIsolate(owner, internalTypesCodec) {
            writeNodes(nodes)
        }
    }

    suspend fun ReadContext.readWork(): List<Node> =
        withGradleIsolate(owner, internalTypesCodec) {
            readNodes()
        }

    private
    suspend fun WriteContext.writeNodes(nodes: List<Node>) {
        val nodeCount = nodes.size
        writeSmallInt(nodeCount)
        val scheduledNodeIds = HashMap<Node, Int>(nodeCount)
        nodes.forEachIndexed { nodeId, node ->
            writeNode(node, scheduledNodeIds)
            scheduledNodeIds[node] = nodeId
        }
    }

    private
    suspend fun ReadContext.readNodes(): List<Node> {
        val nodeCount = readSmallInt()
        val nodes = ArrayList<Node>(nodeCount)
        val nodesById = HashMap<Int, Node>(nodeCount)
        for (nodeId in 0 until nodeCount) {
            val node = readNode(nodesById)
            nodesById[nodeId] = node
            nodes.add(node)
        }
        return nodes
    }

    private
    suspend fun WriteContext.writeNode(
        node: Node,
        scheduledNodeIds: Map<Node, Int>
    ) {
        write(node)
        writeSuccessorReferencesOf(node, scheduledNodeIds)
        writeExecutionStateOf(node)
    }

    private
    suspend fun ReadContext.readNode(nodesById: Map<Int, Node>): Node {
        val node = readNonNull<Node>()
        readSuccessorReferencesOf(node, nodesById)
        readExecutionStateOf(node)
        return node
    }

    private
    fun WriteContext.writeExecutionStateOf(node: Node) {
        // entry nodes are required, finalizer nodes and their dependencies are not
        writeBoolean(node.isRequired)
    }

    private
    fun ReadContext.readExecutionStateOf(node: Node) {
        val isRequired = readBoolean()
        when {
            isRequired -> node.require()
            else -> node.mustNotRun() // finalizer nodes and their dependencies
        }
        if (node !is TaskInAnotherBuild) {
            // we want TaskInAnotherBuild dependencies to be processed later
            node.dependenciesProcessed()
        }
    }

    private
    fun WriteContext.writeSuccessorReferencesOf(node: Node, scheduledNodeIds: Map<Node, Int>) {
        writeSuccessorReferences(node.dependencySuccessors, scheduledNodeIds)
        when (node) {
            is TaskNode -> {
                writeSuccessorReferences(node.shouldSuccessors, scheduledNodeIds)
                writeSuccessorReferences(node.mustSuccessors, scheduledNodeIds)
                writeSuccessorReferences(node.finalizingSuccessors, scheduledNodeIds)
            }
        }
    }

    private
    fun ReadContext.readSuccessorReferencesOf(node: Node, nodesById: Map<Int, Node>) {
        readSuccessorReferences(nodesById) {
            node.addDependencySuccessor(it)
        }
        when (node) {
            is TaskNode -> {
                readSuccessorReferences(nodesById) {
                    node.addShouldSuccessor(it)
                }
                readSuccessorReferences(nodesById) {
                    require(it is TaskNode)
                    node.addMustSuccessor(it)
                }
                readSuccessorReferences(nodesById) {
                    require(it is TaskNode)
                    node.addFinalizingSuccessor(it)
                }
            }
        }
    }

    private
    fun WriteContext.writeSuccessorReferences(
        successors: Collection<Node>,
        scheduledNodeIds: Map<Node, Int>
    ) {
        for (successor in successors) {
            // Discard should/must run after relationships to nodes that are not scheduled to run
            scheduledNodeIds[successor]?.let { successorId ->
                writeSmallInt(successorId)
            }
        }
        writeSmallInt(-1)
    }

    private
    fun ReadContext.readSuccessorReferences(nodesById: Map<Int, Node>, onSuccessor: (Node) -> Unit) {
        while (true) {
            val successorId = readSmallInt()
            if (successorId == -1) break
            val successor = nodesById.getValue(successorId)
            onSuccessor(successor)
        }
    }
}
