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

import com.google.common.collect.ImmutableSet
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.artifacts.transform.DefaultTransformUpstreamDependenciesResolver
import org.gradle.api.internal.tasks.NodeExecutionContext
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.decodePreservingIdentity
import org.gradle.configurationcache.serialization.encodePreservingIdentityOf
import org.gradle.configurationcache.serialization.ownerService
import org.gradle.configurationcache.serialization.readCollection
import org.gradle.configurationcache.serialization.readCollectionInto
import org.gradle.configurationcache.serialization.readNonNull
import org.gradle.configurationcache.serialization.withGradleIsolate
import org.gradle.configurationcache.serialization.writeCollection
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


internal
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
        val scheduledNodeIds = HashMap<Node, Int>(nodeCount)
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
        nodes.forEach { node ->
            writeSuccessorReferencesOf(node, scheduledNodeIds)
            writeNodeGroup(node.group, scheduledNodeIds)
        }
    }

    private
    suspend fun ReadContext.doRead(): ScheduledWork {
        val nodeCount = readSmallInt()
        val nodes = ArrayList<Node>(nodeCount)
        val nodesById = HashMap<Int, Node>(nodeCount)
        for (i in 0 until nodeCount) {
            val node = readNode()
            nodesById[nodesById.size] = node
            if (node is LocalTaskNode) {
                node.prepareNode.require()
                nodesById[nodesById.size] = node.prepareNode
            }
            nodes.add(node)
        }
        // Note that using the ImmutableSet retains the original ordering of entry nodes.
        val entryNodes = ImmutableSet.builder<Node>()
        readCollection {
            entryNodes.add(nodesById.getValue(readSmallInt()))
        }
        nodes.forEach { node ->
            readSuccessorReferencesOf(node, nodesById)
            node.group = readNodeGroup(nodesById)
        }
        return ScheduledWork(nodes, entryNodes.build())
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
