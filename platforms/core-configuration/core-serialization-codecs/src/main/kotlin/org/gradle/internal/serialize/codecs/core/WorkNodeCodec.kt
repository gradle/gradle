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
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.artifacts.transform.DefaultTransformUpstreamDependenciesResolver
import org.gradle.api.internal.tasks.NodeExecutionContext
import org.gradle.execution.plan.ActionNode
import org.gradle.execution.plan.CompositeNodeGroup
import org.gradle.execution.plan.FinalizerGroup
import org.gradle.execution.plan.LocalTaskNode
import org.gradle.execution.plan.Node
import org.gradle.execution.plan.NodeGroup
import org.gradle.execution.plan.NodeGroupComparator
import org.gradle.execution.plan.OrdinalGroup
import org.gradle.execution.plan.OrdinalGroupFactory
import org.gradle.execution.plan.ScheduledWork
import org.gradle.execution.plan.TaskNode
import org.gradle.internal.debug.Debug.println
import org.gradle.internal.cc.base.serialize.withGradleIsolate
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.ownerService
import org.gradle.internal.serialize.graph.readCollection
import org.gradle.internal.serialize.graph.readCollectionInto
import org.gradle.internal.serialize.graph.readNonNull
import org.gradle.internal.serialize.graph.writeCollection


class WorkNodeCodec(
    private val owner: GradleInternal,
    private val internalTypesCodec: Codec<Any?>,
    private val ordinalGroups: OrdinalGroupFactory
) {

    suspend fun WriteContext.writeWork(scheduledNodes: List<Node>, entryNodes: Set<Node>) {
        // Share bean instances across all nodes (except tasks, which have their own isolate)
        withGradleIsolate(owner, internalTypesCodec) {
            val groupIds: Map<NodeGroup, Int> = identifyGroups(scheduledNodes)
            val (scheduledNodeIds, scheduledEntryNodeIds) = identifyNodes(scheduledNodes, entryNodes)
            doWriteScheduledNodes(scheduledNodes, scheduledNodeIds::getValue, false)
            doWriteScheduledWorkEdgesAndGroups(scheduledEntryNodeIds, scheduledNodes, scheduledNodeIds, groupIds)
        }
    }

    suspend fun ReadContext.readWork(): ScheduledWork =
        withGradleIsolate(owner, internalTypesCodec) {
            val (scheduledNodes, nodesById) = doReadScheduledNodes(false)
            doReadScheduledWorkEdgesAndGroups(nodesById, scheduledNodes)
        }

    private
    fun identifyGroups(nodes: List<Node>): Map<NodeGroup, Int> {
        val groupIds = mutableMapOf<NodeGroup, Int>().apply {
            put(NodeGroup.DEFAULT_GROUP, 0)
        }
        nodes.forEach {
            groupIds.computeIfAbsent(it.group) { groupIds.size }
        }
        return groupIds
    }


    /**
     * Writes only the scheduled nodes, remaining ScheduledWork data is written separately.
     */
    suspend fun WriteContext.writeNodes(scheduledNodes: List<Node>, nodeIdentifier: (Node) -> Int) {
        // Share bean instances across all nodes (except tasks, which have their own isolate)
        withGradleIsolate(owner, internalTypesCodec) {
            doWriteScheduledNodes(scheduledNodes, nodeIdentifier, true)
        }
    }

    suspend fun ReadContext.readScheduledNodes(): Pair<ArrayList<Node>, HashMap<Int, Node>> =
        withGradleIsolate(owner, internalTypesCodec) {
            doReadScheduledNodes(true)
        }

    /**
     * Writes only the nodes (without the edges between nodes).
     */
    private
    suspend fun WriteContext.doWriteScheduledNodes(scheduledNodes: List<Node>, nodeIdentifier: ((Node) -> Int), writeIds: Boolean) {
        println { "Writing ${scheduledNodes.size} scheduled nodes  - ${owner.identityPath}" }
        writeSmallInt(scheduledNodes.size)
        scheduledNodes.forEach { node ->
            val nodeId = nodeIdentifier(node)
            if (writeIds) {
                writeSmallInt(nodeId)
            }
            writeNode(node)
            if (node is LocalTaskNode) {
                if (writeIds) {
                    writeSmallInt(nodeIdentifier(node.prepareNode))
                }
            }
        }
    }


    private
    suspend fun ReadContext.doReadScheduledNodes(readIds: Boolean): Pair<ArrayList<Node>, HashMap<Int, Node>> {
        val nodeCount = readSmallInt()
        println { "Reading $nodeCount scheduled nodes  - ${owner.identityPath}" }
        val scheduledNodes = ArrayList<Node>(nodeCount)
        val nodesById = HashMap<Int, Node>(nodeCount)
        for (i in 0 until nodeCount) {
            val nodeId = if (readIds) readSmallInt() else nodesById.size
            val node = readNode()
            nodesById[nodeId] = node
            if (node is LocalTaskNode) {
                node.prepareNode.require()
                val prepareNodeId = if (readIds) readSmallInt() else nodesById.size
                nodesById[prepareNodeId] = node.prepareNode
            }
            scheduledNodes.add(node)
        }
        return Pair(scheduledNodes, nodesById)
    }

    fun ReadContext.readScheduledWork(scheduledNodes: List<Node>, nodesById: Map<Int, Node>): ScheduledWork =
        withGradleIsolate(owner, internalTypesCodec) {
            doReadScheduledWorkEdgesAndGroups(nodesById, scheduledNodes)
        }

    fun WriteContext.doWriteScheduledWorkEdgesAndGroups(
        scheduledEntryNodeIds: List<Int>,
        scheduledNodes: List<Node>,
        scheduledNodeIds: Map<Node, Int>,
        groupIds: Map<NodeGroup, Int>
    ) {
        withGradleIsolate(owner, internalTypesCodec) {
            writeScheduledEntryNodeIds(scheduledEntryNodeIds)
            writeSuccessorReferencesAndGroups(scheduledNodes, scheduledNodeIds, groupIds)
        }
    }

    private
    fun ReadContext.doReadScheduledWorkEdgesAndGroups(
        nodesById: Map<Int, Node>,
        scheduledNodes: List<Node>
    ): ScheduledWork {
        val entryNodes = readEntryNodes(nodesById)
        readSuccessorReferencesAndGroups(nodesById)
        return ScheduledWork(scheduledNodes, entryNodes)
    }

    /**
     * Computes and stores successor references from the given nodes, while also storing group membership.
     *
     * @see [writeSuccessorReferencesOf]
     */
    private
    fun WriteContext.writeSuccessorReferencesAndGroups(
        scheduledNodes: List<Node>,
        scheduledNodeIds: Map<Node, Int>,
        groupsById: Map<NodeGroup, Int>
    ) {
        writeGroups(groupsById, scheduledNodeIds)
        writeCollection(scheduledNodes) { node ->
            val nodeId = scheduledNodeIds.getValue(node)
            writeSmallInt(nodeId)
            writeSuccessorReferencesOf(node, scheduledNodeIds)
            val groupId = groupsById.getValue(node.group)
            writeSmallInt(groupId)
        }
    }

    private
    fun ReadContext.readSuccessorReferencesAndGroups(
        nodesById: Map<Int, Node>
    ) {
        val groupsById = readGroups(nodesById)
        readCollection {
            val scheduledNodeId = readSmallInt()
            val node = nodesById.getValue(scheduledNodeId)
            readSuccessorReferencesOf(node, nodesById)
            val groupId = readSmallInt()
            node.group = groupsById[groupId]!!
        }
    }

    private fun WriteContext.writeGroups(
        groupsById: Map<NodeGroup, Int>,
        scheduledNodeIds: Map<Node, Int>
    ) {
        groupsById.entries.forEach { (node, id) ->
            println { "$id -> $node - ${System.identityHashCode(node).toString(16)}" }
        }
        val sortedGroups = groupsById.toSortedMap(NodeGroupComparator.INSTANCE).filter { (group, _) -> group != NodeGroup.DEFAULT_GROUP }
        println("writeGroups: ${groupsById.size} - ${sortedGroups.size}")
        writeCollection(sortedGroups.entries) { (group, groupId) ->
            writeSmallInt(groupId)
            writeNodeGroup(group, scheduledNodeIds, groupsById)
            println("Wrote group $groupId - $group - ${group.javaClass.typeName}")
        }
    }

    private fun ReadContext.readGroups(nodesById: Map<Int, Node>): MutableMap<Int, NodeGroup> {
        val groupsById = mutableMapOf<Int, NodeGroup>().apply {
            put(0, NodeGroup.DEFAULT_GROUP)
        }
        readCollection {
            val groupId = readSmallInt()
            println("Reading group $groupId")
            val group = readNodeGroup(nodesById, groupsById)
            groupsById[groupId] = group
            println("Read group $groupId - $group - ${group.javaClass.typeName}")
        }
        return groupsById
    }

    /**
    A large build may have many nodes but not so many entry nodes.
    To save some disk space, we're only saving entry node ids rather than writing "entry/non-entry" boolean for every node.
     */
    private
    fun WriteContext.writeScheduledEntryNodeIds(scheduledEntryNodeIds: List<Int>) {
        writeCollection(scheduledEntryNodeIds) {
            writeSmallInt(it)
        }
    }

    private
    fun ReadContext.readEntryNodes(nodesById: Map<Int, Node>): ImmutableSet<Node> {
        // Note that using the ImmutableSet retains the original ordering of entry nodes.
        val entryNodes = ImmutableSet.builder<Node>()
        readCollection {
            val key = readSmallInt()
            entryNodes.add(nodesById.getValue(key))
        }
        return entryNodes.build()
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

    fun Node.getNodeType(): String {
        val simpleName = javaClass.getSimpleName()
        return simpleName ?: javaClass.getTypeName()
    }

    private
    suspend fun WriteContext.writeNode(node: Node) {
        println("writeNode(${node.getNodeType()} ${node}) - ${node.owningProject?.owner?.identityPath} - ${this.isolate.owner} - ${this.isolate.owner.delegate}")
        write(node)
    }

    private
    suspend fun ReadContext.readNode(): Node {
        val node = readNonNull<Any>()
        require(node is Node)
        println("readNode() -> ${node} - ${node.owningProject?.identityPath} - ${this.isolate.owner} - ${this.isolate.owner.delegate}")
        node.require()
        node.dependenciesProcessed()
        return node
    }

    private
    fun WriteContext.writeNodeGroup(group: NodeGroup, nodesById: Map<Node, Int>, groupsById: Map<NodeGroup, Int>) {
        when (group) {
            is OrdinalGroup -> {
                writeSmallInt(0)
                writeSmallInt(group.ordinal)
            }

            is FinalizerGroup -> {
                writeSmallInt(1)
                writeSmallInt(nodesById.getValue(group.node))
                writeSmallInt(groupsById.getValue(group.delegate))
            }

            is CompositeNodeGroup -> {
                writeSmallInt(2)
                writeBoolean(group.isReachableFromEntryPoint)
                writeSmallInt(groupsById.getValue(group.ordinalGroup))
                writeCollection(group.finalizerGroups) {
                    writeSmallInt(groupsById.getValue(it))
                }
            }

            NodeGroup.DEFAULT_GROUP -> {
                writeSmallInt(3)
            }

            else -> throw IllegalArgumentException()
        }
    }

    private
    fun ReadContext.readNodeGroup(nodesById: Map<Int, Node>, groupsById: MutableMap<Int, NodeGroup>): NodeGroup {
        val decoded: Any =
            when (readSmallInt()) {
                0 -> {
                    val ordinal = readSmallInt()
                    ordinalGroups.group(ordinal)
                }

                1 -> {
                    val finalizerNode = nodesById.getValue(readSmallInt()) as TaskNode
                    val delegate = groupsById.getValue(readSmallInt())
                    FinalizerGroup(finalizerNode, delegate)
                }

                2 -> {
                    val reachableFromCommandLine = readBoolean()
                    val ordinalGroup = groupsById.getValue(readSmallInt())
                    val groups = readCollectionInto(::HashSet) {
                        groupsById.getValue(readSmallInt()) as FinalizerGroup
                    }
                    CompositeNodeGroup(reachableFromCommandLine, ordinalGroup, groups)
                }

                3 -> NodeGroup.DEFAULT_GROUP
                else -> throw IllegalArgumentException()
            }
        return decoded as NodeGroup
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
            val id = scheduledNodeIds[successor]
            if (successor.isRequired) {
                writeSmallInt(id!!)
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
