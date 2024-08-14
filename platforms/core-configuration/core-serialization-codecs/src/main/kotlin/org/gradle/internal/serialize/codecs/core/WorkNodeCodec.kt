/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.serialize.codecs.core

import com.google.common.collect.ImmutableSet
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.artifacts.transform.DefaultTransformUpstreamDependenciesResolver
import org.gradle.api.internal.tasks.NodeExecutionContext
import org.gradle.execution.plan.ActionNode
import org.gradle.execution.plan.CompositeNodeGroup
import org.gradle.execution.plan.FinalizerGroup
import org.gradle.execution.plan.LocalTaskNode
import org.gradle.execution.plan.Node
import org.gradle.execution.plan.NodeGroup
import org.gradle.execution.plan.OrdinalGroup
import org.gradle.execution.plan.OrdinalGroupFactory
import org.gradle.execution.plan.ScheduledWork
import org.gradle.execution.plan.TaskNode
import org.gradle.internal.cc.base.serialize.withGradleIsolate
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.buildCollection
import org.gradle.internal.serialize.graph.decodePreservingIdentity
import org.gradle.internal.serialize.graph.encodePreservingIdentityOf
import org.gradle.internal.serialize.graph.ownerService
import org.gradle.internal.serialize.graph.readCollectionInto
import org.gradle.internal.serialize.graph.readNonNull
import org.gradle.internal.serialize.graph.writeCollection


private
typealias NodeForId = (Int) -> Node


private
typealias IdForNode = (Node) -> Int


class WorkNodeCodec(
    private val owner: GradleInternal,
    private val internalTypesCodec: Codec<Any?>,
    private val ordinalGroups: OrdinalGroupFactory
) {

    suspend fun WriteContext.writeWork(work: ScheduledWork) {
        // Share bean instances across all nodes (except tasks, which have their own isolate)
        withGradleIsolate(owner, internalTypesCodec) {
            doWrite(work)
        }
    }

    suspend fun ReadContext.readWork(): ScheduledWork =
        withGradleIsolate(owner, internalTypesCodec) {
            doRead()
        }

    private
    suspend fun WriteContext.doWrite(work: ScheduledWork) {
        val nodes = work.scheduledNodes
        val nodeCount = nodes.size
        writeSmallInt(nodeCount)
        val scheduledNodeIds = Object2IntOpenHashMap<Node>(nodeCount)
        // Not all entry nodes are always scheduled.
        // In particular, it happens when the entry node is a task of the included plugin build that runs as part of building the plugin.
        // Such tasks do not rerun when configuration cache is re-used, even if specified on the command line.
        // Not restoring them as entry points doesn't affect the resulting execution plan.
        val scheduledEntryNodeIds = mutableListOf<Int>()
        nodes.forEach { node ->
            write(node)
            val nodeId = scheduledNodeIds.size
            scheduledNodeIds[node] = nodeId
            if (node in work.entryNodes) {
                scheduledEntryNodeIds.add(nodeId)
            }
            if (node is LocalTaskNode) {
                scheduledNodeIds[node.prepareNode] = scheduledNodeIds.size
            }
        }
        // A large build may have many nodes but not so many entry nodes.
        // To save some disk space, we're only saving entry node ids rather than writing "entry/non-entry" boolean for every node.
        writeCollection(scheduledEntryNodeIds) {
            writeSmallInt(it)
        }
        val idForNode: IdForNode = scheduledNodeIds::getInt
        nodes.forEach { node ->
            writeSuccessorReferencesOf(node, idForNode)
            writeNodeGroup(node.group, idForNode)
        }
    }

    private
    suspend fun ReadContext.doRead(): ScheduledWork {
        val nodeCount = readSmallInt()
        val nodes = ArrayList<Node>(nodeCount)
        val nodesById = ArrayList<Node>(nodeCount)
        repeat(nodeCount) {
            val node = readNode()
            nodesById.add(node)
            if (node is LocalTaskNode) {
                node.prepareNode.require()
                nodesById.add(node.prepareNode)
            }
            nodes.add(node)
        }
        // Note that using the ImmutableSet retains the original ordering of entry nodes.
        val entryNodes =
            buildCollection({ ImmutableSet.builderWithExpectedSize<Node>(it) }) {
                add(nodesById[readSmallInt()])
            }.build()

        val nodeForId: NodeForId = nodesById::get
        nodes.forEach { node ->
            readSuccessorReferencesOf(node, nodeForId)
            node.group = readNodeGroup(nodeForId)
        }
        return ScheduledWork(nodes, entryNodes)
    }

    private
    suspend fun ReadContext.readNode(): Node {
        val node = readNonNull<Node>()
        node.require()
        node.dependenciesProcessed()
        return node
    }

    private
    fun WriteContext.writeNodeGroup(group: NodeGroup, idForNode: IdForNode) {
        encodePreservingIdentityOf(group) {
            when (group) {
                is OrdinalGroup -> {
                    writeSmallInt(0)
                    writeOrdinal(group)
                }

                is FinalizerGroup -> {
                    writeSmallInt(1)
                    writeSmallInt(idForNode(group.node))
                    writeNodeGroup(group.delegate, idForNode)
                    writeNullableOrdinal(group.asOrdinal())
                }

                is CompositeNodeGroup -> {
                    writeSmallInt(2)
                    writeBoolean(group.isReachableFromEntryPoint)
                    writeNodeGroup(group.ordinalGroup, idForNode)
                    writeCollection(group.finalizerGroups) {
                        writeNodeGroup(it, idForNode)
                    }
                }

                NodeGroup.DEFAULT_GROUP -> {
                    writeSmallInt(3)
                }

                else -> error("Unexpected node group: ${group.javaClass.name}")
            }
        }
    }

    private
    fun ReadContext.readNodeGroup(nodeForId: NodeForId): NodeGroup {
        return decodePreservingIdentity { id ->
            when (readSmallInt()) {
                0 -> readOrdinal()

                1 -> {
                    val finalizerNode = nodeForId(readSmallInt()) as TaskNode
                    val delegate = readNodeGroup(nodeForId)
                    val ordinal = readNullableOrdinal()
                    FinalizerGroup(finalizerNode, delegate, ordinal)
                }

                2 -> {
                    val reachableFromCommandLine = readBoolean()
                    val ordinalGroup = readNodeGroup(nodeForId)
                    val groups = readCollectionInto(::HashSet) { readNodeGroup(nodeForId) as FinalizerGroup }
                    CompositeNodeGroup(reachableFromCommandLine, ordinalGroup, groups)
                }

                3 -> NodeGroup.DEFAULT_GROUP
                else -> error("Unexpected input when decoding node group")
            }.also {
                isolate.identities.putInstance(id, it)
            }
        }
    }

    private
    fun WriteContext.writeOrdinal(group: OrdinalGroup) {
        writeSmallInt(group.ordinal)
    }

    private
    fun ReadContext.readOrdinal(): OrdinalGroup {
        val ordinal = readSmallInt()
        return ordinalGroups.group(ordinal)
    }

    private
    fun WriteContext.writeNullableOrdinal(ordinal: OrdinalGroup?) {
        if (ordinal != null) {
            writeBoolean(true)
            writeOrdinal(ordinal)
        } else {
            writeBoolean(false)
        }
    }

    private
    fun ReadContext.readNullableOrdinal(): OrdinalGroup? =
        if (readBoolean()) {
            readOrdinal()
        } else {
            null
        }

    private
    fun WriteContext.writeSuccessorReferencesOf(node: Node, scheduledNodeIds: IdForNode) {
        var successors = node.dependencySuccessors
        if (node is ActionNode) {
            val setupNode = node.action?.preExecutionNode
            // Could probably add some abstraction for nodes that can be executed eagerly and discarded
            if (setupNode is DefaultTransformUpstreamDependenciesResolver.FinalizeTransformDependenciesFromSelectedArtifacts.CalculateFinalDependencies) {
                setupNode.run(object : NodeExecutionContext {
                    override fun <T : Any> getService(type: Class<T>): T {
                        return ownerService(type)
                    }
                })
                successors = successors + setupNode.postExecutionNodes
            }
        }
        writeSuccessorReferences(successors, scheduledNodeIds)
        when (node) {
            is TaskNode -> {
                writeSuccessorReferences(node.shouldSuccessors, scheduledNodeIds)
                writeSuccessorReferences(node.mustSuccessors, scheduledNodeIds)
                writeSuccessorReferences(node.finalizingSuccessors, scheduledNodeIds)
                writeSuccessorReferences(node.lifecycleSuccessors, scheduledNodeIds)
            }
        }
    }

    private
    fun ReadContext.readSuccessorReferencesOf(node: Node, nodeForId: NodeForId) {
        readSuccessorReferences(nodeForId) {
            node.addDependencySuccessor(it)
        }
        when (node) {
            is TaskNode -> {
                readSuccessorReferences(nodeForId) {
                    node.addShouldSuccessor(it)
                }
                readSuccessorReferences(nodeForId) {
                    require(it is TaskNode) {
                        "Expecting a TaskNode as a must successor of `$node`, got `$it`."
                    }
                    node.addMustSuccessor(it)
                }
                readSuccessorReferences(nodeForId) {
                    node.addFinalizingSuccessor(it)
                }
                val lifecycleSuccessors = mutableSetOf<Node>()
                readSuccessorReferences(nodeForId) {
                    lifecycleSuccessors.add(it)
                }
                node.lifecycleSuccessors = lifecycleSuccessors
            }
        }
    }

    private
    fun WriteContext.writeSuccessorReferences(
        successors: Collection<Node>,
        scheduledNodeIds: IdForNode
    ) {
        for (successor in successors) {
            if (successor.isRequired) {
                val successorId = scheduledNodeIds(successor)
                writeSmallInt(successorId)
            }
        }
        writeSmallInt(-1)
    }

    private
    fun ReadContext.readSuccessorReferences(nodeForId: NodeForId, onSuccessor: (Node) -> Unit) {
        while (true) {
            val successorId = readSmallInt()
            if (successorId == -1) break
            val successor = nodeForId(successorId)
            onSuccessor(successor)
        }
    }
}
