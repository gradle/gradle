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

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.artifacts.transform.DefaultTransformUpstreamDependenciesResolver
import org.gradle.api.internal.tasks.NodeExecutionContext
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.decodePreservingIdentity
import org.gradle.internal.serialize.graph.encodePreservingIdentityOf
import org.gradle.internal.serialize.graph.ownerService
import org.gradle.internal.serialize.graph.readCollection
import org.gradle.internal.serialize.graph.readCollectionInto
import org.gradle.internal.serialize.graph.readNonNull
import org.gradle.internal.cc.base.serialize.withGradleIsolate
import org.gradle.internal.serialize.graph.writeCollection
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


class WorkNodeCodec(
    private val owner: GradleInternal,
    private val internalTypesCodec: Codec<Any?>,
    private val ordinalGroups: OrdinalGroupFactory
) {

    suspend fun WriteContext.writeWork(work: ScheduledWork) {
        // Share bean instances across all nodes (except tasks, which have their own isolate)
        withGradleIsolate(owner, internalTypesCodec) {
            val scheduledNodes = work.scheduledNodes
            val entryNodes = work.entryNodes
            doWriteNodes(scheduledNodes)
            val (scheduledNodeIds, scheduledEntryNodeIds) = identifyNodes(scheduledNodes, entryNodes)
            writeEdgesAndOtherNodeMetadata(scheduledEntryNodeIds, scheduledNodes, scheduledNodeIds)
        }
    }

    /**
     * Writes only the scheduled nodes, remaining SchedyledWork data is written separately.
     */
    suspend fun WriteContext.writeNodes(projectPath: String, scheduledNodes: List<Node>) {
        // Share bean instances across all nodes (except tasks, which have their own isolate)
        withGradleIsolate(owner, internalTypesCodec) {
            write(projectPath)
            doWriteNodes(scheduledNodes)
        }
    }

    suspend fun ReadContext.readWork(): ScheduledWork =
        withGradleIsolate(owner, internalTypesCodec) {
            doRead()
        }

    private fun WriteContext.writeEdgesAndOtherNodeMetadata(
        scheduledEntryNodeIds: List<Int>,
        scheduledNodes: ImmutableList<Node>,
        scheduledNodeIds: Map<Node, Int>
    ) {
        writeScheduleEntryNodeIds(scheduledEntryNodeIds)
        writeSuccessorReferencesAndGrouping(scheduledNodes, scheduledNodeIds)
    }

    /**
     * Computes and stores successor references from the given nodes, while also storing group membership.
     *
     * @see [writeSuccessorReferencesOf]
     */
    private
    fun WriteContext.writeSuccessorReferencesAndGrouping(
        scheduledNodes: List<Node>,
        scheduledNodeIds: Map<Node, Int>
    ) {
        scheduledNodes.forEach { node ->
            writeSuccessorReferencesOf(node, scheduledNodeIds)
            writeNodeGroup(node.group, scheduledNodeIds)
        }
    }

    /**
    A large build may have many nodes but not so many entry nodes.
    To save some disk space, we're only saving entry node ids rather than writing "entry/non-entry" boolean for every node.
     */
    private
    fun WriteContext.writeScheduleEntryNodeIds(scheduledEntryNodeIds: List<Int>) {
        writeCollection(scheduledEntryNodeIds) {
            writeSmallInt(it)
        }
    }

    /**
     * Writes only the nodes (without the edges between nodes).
     */
    private
    suspend fun WriteContext.doWriteNodes(scheduledNodes: List<Node>) {
        writeSmallInt(scheduledNodes.size)
        scheduledNodes.forEach { node ->
            write(node)
        }
    }

    /**
     * Assigns ids for the given scheduled nodes, while also identifying entry nodes.
     *
     * @param scheduledNodes the list of nodes that are scheduled
     * @param entryNodes a set containing those scheduled nodes that are entry nodes
     * @return a pair **<a, b>** where
     * **a** is a map of nodes and their generated ids and
     * **b** is a set of the ids for scheduled nodes that are also entry nodes
     */
    private
    fun identifyNodes(
        scheduledNodes: List<Node>,
        entryNodes: Set<Node>
    ): Pair<Map<Node, Int>, List<Int>> {
        val scheduledNodeIds = HashMap<Node, Int>(scheduledNodes.size)
        // Not all entry nodes are always scheduled.
        // In particular, it happens when the entry node is a task of the included plugin build that runs as part of building the plugin.
        // Such tasks do not rerun when configuration cache is re-used, even if specified on the command line.
        // Not restoring them as entry points doesn't affect the resulting execution plan.
        val scheduledEntryNodeIds = mutableListOf<Int>()
        scheduledNodes.forEach { node ->
            val nodeId = scheduledNodeIds.size
            scheduledNodeIds[node] = nodeId
            if (node in entryNodes) {
                scheduledEntryNodeIds.add(nodeId)
            }
            if (node is LocalTaskNode) {
                scheduledNodeIds[node.prepareNode] = scheduledNodeIds.size
            }
        }
        return Pair(scheduledNodeIds, scheduledEntryNodeIds)
    }

    private
    suspend fun ReadContext.doRead(): ScheduledWork {
        val (scheduledNodes, nodesById) = readScheduledNodes()
        return readScheduledWork(nodesById, scheduledNodes)
    }

    private
    fun ReadContext.readScheduledWork(
        nodesById: HashMap<Int, Node>,
        scheduledNodes: ArrayList<Node>
    ): ScheduledWork {
        val entryNodes = restoreEntryNodes(nodesById)
        restoreSuccessorReferencesAndNodeGroups(scheduledNodes, nodesById)
        return ScheduledWork(scheduledNodes, entryNodes.build())
    }

    private
    fun ReadContext.restoreSuccessorReferencesAndNodeGroups(
        scheduledNodes: ArrayList<Node>,
        nodesById: HashMap<Int, Node>
    ) {
        scheduledNodes.forEach { node ->
            readSuccessorReferencesOf(node, nodesById)
            node.group = readNodeGroup(nodesById)
        }
    }

    private
    fun ReadContext.restoreEntryNodes(nodesById: HashMap<Int, Node>): ImmutableSet.Builder<Node> {
        // Note that using the ImmutableSet retains the original ordering of entry nodes.
        val entryNodes = ImmutableSet.builder<Node>()
        readCollection {
            entryNodes.add(nodesById.getValue(readSmallInt()))
        }
        return entryNodes
    }

    private
    suspend fun ReadContext.readScheduledNodes(): Pair<ArrayList<Node>, HashMap<Int, Node>> {
        val nodeCount = readSmallInt()
        val scheduledNodes = ArrayList<Node>(nodeCount)
        val nodesById = HashMap<Int, Node>(nodeCount)
        for (i in 0 until nodeCount) {
            val node = readNode()
            nodesById[nodesById.size] = node
            if (node is LocalTaskNode) {
                node.prepareNode.require()
                nodesById[nodesById.size] = node.prepareNode
            }
            scheduledNodes.add(node)
        }
        return Pair(scheduledNodes, nodesById)
    }

    private
    suspend fun ReadContext.readNode(): Node {
        val node = readNonNull<Node>()
        node.require()
        node.dependenciesProcessed()
        return node
    }

    private
    fun WriteContext.writeNodeGroup(group: NodeGroup, nodesById: Map<Node, Int>) {
        encodePreservingIdentityOf(group) {
            when (group) {
                is OrdinalGroup -> {
                    writeSmallInt(0)
                    writeSmallInt(group.ordinal)
                }

                is FinalizerGroup -> {
                    writeSmallInt(1)
                    writeSmallInt(nodesById.getValue(group.node))
                    writeNodeGroup(group.delegate, nodesById)
                }

                is CompositeNodeGroup -> {
                    writeSmallInt(2)
                    writeBoolean(group.isReachableFromEntryPoint)
                    writeNodeGroup(group.ordinalGroup, nodesById)
                    writeCollection(group.finalizerGroups) {
                        writeNodeGroup(it, nodesById)
                    }
                }

                NodeGroup.DEFAULT_GROUP -> {
                    writeSmallInt(3)
                }

                else -> throw IllegalArgumentException()
            }
        }
    }

    private
    fun ReadContext.readNodeGroup(nodesById: Map<Int, Node>): NodeGroup {
        return decodePreservingIdentity { id ->
            when (readSmallInt()) {
                0 -> {
                    val ordinal = readSmallInt()
                    ordinalGroups.group(ordinal)
                }

                1 -> {
                    val finalizerNode = nodesById.getValue(readSmallInt()) as TaskNode
                    val delegate = readNodeGroup(nodesById)
                    FinalizerGroup(finalizerNode, delegate)
                }

                2 -> {
                    val reachableFromCommandLine = readBoolean()
                    val ordinalGroup = readNodeGroup(nodesById)
                    val groups = readCollectionInto(::HashSet) { readNodeGroup(nodesById) as FinalizerGroup }
                    CompositeNodeGroup(reachableFromCommandLine, ordinalGroup, groups)
                }

                3 -> NodeGroup.DEFAULT_GROUP
                else -> throw IllegalArgumentException()
            }.also {
                isolate.identities.putInstance(id, it)
            }
        }
    }

    /**
     * Writes all successor references of the given node.
     *
     * Successor references include:
     * - [Node.getDependencySuccessors]
     * - [CalculateFinalDependencies.postExecutionNodes][DefaultTransformUpstreamDependenciesResolver.FinalizeTransformDependenciesFromSelectedArtifacts.CalculateFinalDependencies.getPostExecutionNodes]
     * - [TaskNode.getShouldSuccessors]
     * - [TaskNode.getMustSuccessors]
     * - [TaskNode.getFinalizingSuccessors]
     * - [TaskNode.getLifecycleSuccessors]
     */
    private
    fun WriteContext.writeSuccessorReferencesOf(node: Node, scheduledNodeIds: Map<Node, Int>) {
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
                    require(it is TaskNode) {
                        "Expecting a TaskNode as a must successor of `$node`, got `$it`."
                    }
                    node.addMustSuccessor(it)
                }
                readSuccessorReferences(nodesById) {
                    node.addFinalizingSuccessor(it)
                }
                val lifecycleSuccessors = mutableSetOf<Node>()
                readSuccessorReferences(nodesById) {
                    lifecycleSuccessors.add(it)
                }
                node.lifecycleSuccessors = lifecycleSuccessors
            }
        }
    }

    private
    fun WriteContext.writeSuccessorReferences(
        successors: Collection<Node>,
        scheduledNodeIds: Map<Node, Int>
    ) {
        for (successor in successors) {
            if (successor.isRequired) {
                val successorId = scheduledNodeIds.getValue(successor)
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
