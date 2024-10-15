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
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.apache.commons.lang3.StringUtils
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.NodeExecutionContext
import org.gradle.execution.plan.ActionNode
import org.gradle.execution.plan.CompositeNodeGroup
import org.gradle.execution.plan.FinalizerGroup
import org.gradle.execution.plan.LocalTaskNode
import org.gradle.execution.plan.Node
import org.gradle.execution.plan.NodeGroup
import org.gradle.execution.plan.OrdinalGroup
import org.gradle.execution.plan.OrdinalGroupFactory
import org.gradle.execution.plan.PostExecutionNodeAwareActionNode
import org.gradle.execution.plan.ScheduledWork
import org.gradle.execution.plan.TaskNode
import org.gradle.internal.cc.base.exceptions.ConfigurationCacheException
import org.gradle.internal.cc.base.serialize.withGradleIsolate
import org.gradle.internal.collect.PersistentList
import org.gradle.internal.extensions.stdlib.useToRun
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.MultipleBuildOperationFailures
import org.gradle.internal.operations.RunnableBuildOperation
import org.gradle.internal.serialize.graph.CloseableReadContext
import org.gradle.internal.serialize.graph.CloseableWriteContext
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.IsolateContext
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.buildCollection
import org.gradle.internal.serialize.graph.decodePreservingIdentity
import org.gradle.internal.serialize.graph.encodePreservingIdentityOf
import org.gradle.internal.serialize.graph.ownerService
import org.gradle.internal.serialize.graph.readCollectionInto
import org.gradle.internal.serialize.graph.readList
import org.gradle.internal.serialize.graph.readNonNull
import org.gradle.internal.serialize.graph.readWith
import org.gradle.internal.serialize.graph.runWriteOperation
import org.gradle.internal.serialize.graph.serviceOf
import org.gradle.internal.serialize.graph.writeCollection
import org.gradle.util.Path
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicReference

private
typealias NodeForId = (Int) -> Node


private
typealias IdForNode = (Node) -> Int


interface IsolateContextSource {
    fun readContextFor(baseContext: ReadContext, path: Path): CloseableReadContext
    fun writeContextFor(baseContext: WriteContext, path: Path): CloseableWriteContext
}


class WorkNodeCodec(
    private val owner: GradleInternal,
    private val internalTypesCodec: Codec<Any?>,
    private val ordinalGroups: OrdinalGroupFactory,
    private val contextSource: IsolateContextSource,
    /** Should we store work nodes in parallel? */
    private val parallelStore: Boolean,
    /** Should we load work nodes in parallel? */
    private val parallelLoad: Boolean
) {

    fun WriteContext.writeWork(work: ScheduledWork) {
        // Share bean instances across all nodes (except tasks, which have their own isolate)
        withGradleIsolate(owner, internalTypesCodec) {
            doWrite(work)
        }
    }

    fun ReadContext.readWork(): ScheduledWork =
        withGradleIsolate(owner, internalTypesCodec) {
            doRead()
        }

    private
    fun WriteContext.doWrite(work: ScheduledWork) {
        val nodes = work.scheduledNodes
        val entryNodes = work.entryNodes
        val nodeCount = nodes.size
        val scheduledNodeIds = Object2IntOpenHashMap<Node>(nodeCount).apply {
            defaultReturnValue(-1)
        }

        val scheduledEntryNodeIds = assignNodeIds(scheduledNodeIds, nodes, entryNodes)
        val idForNode: IdForNode = { node ->
            scheduledNodeIds.getInt(node).also { nodeId ->
                require(nodeId >= 0) {
                    "Node id missing for node $node"
                }
            }
        }

        writeSmallInt(scheduledNodeIds.size)
        val actionNodeSuccessors = writeNodes(nodes, idForNode)
        writeEntryNodes(scheduledEntryNodeIds)
        writeEdgesAndGroupMembership(nodes, actionNodeSuccessors, idForNode)
    }

    private
    fun ReadContext.doRead(): ScheduledWork {
        val nodeIdCount = readSmallInt()
        val nodeForId = readNodes(nodeIdCount)
        val entryNodes = readEntryNodes(nodeForId)
        val nodes = readEdgesAndGroupMembership(nodeForId)
        return ScheduledWork(nodes, entryNodes)
    }

    private
    fun WriteContext.writeEntryNodes(scheduledEntryNodeIds: List<Int>) {
        // A large build may have many nodes but not so many entry nodes.
        // To save some disk space, we're only saving entry node ids rather than writing "entry/non-entry" boolean for every node.
        writeCollection(scheduledEntryNodeIds) {
            writeSmallInt(it)
        }
    }

    private
    fun ReadContext.readEntryNodes(nodeForId: NodeForId) =
        // Note that using the ImmutableSet retains the original ordering of entry nodes.
        buildCollection({ ImmutableSet.builderWithExpectedSize<Node>(it) }) {
            add(nodeForId(readSmallInt()))
        }.build()

    private
    fun WriteContext.writeEdgesAndGroupMembership(
        nodes: ImmutableList<Node>,
        actionNodeSuccessors: (ActionNode) -> List<Node>?,
        idForNode: IdForNode
    ) {
        writeCollection(nodes) { node ->
            writeSmallInt(idForNode(node))
            writeSuccessorReferencesOf(node, actionNodeSuccessors, idForNode)
            writeNodeGroup(node.group, idForNode)
        }
    }

    private
    fun ReadContext.readEdgesAndGroupMembership(nodeForId: NodeForId): List<Node> {
        return readList {
            val node = nodeForId(readSmallInt())
            readSuccessorReferencesOf(node, nodeForId)
            node.group = readNodeGroup(nodeForId)
            node
        }
    }

    private
    fun assignNodeIds(
        scheduledNodeIds: Object2IntOpenHashMap<Node>,
        nodes: List<Node>,
        entryNodes: ImmutableSet<Node>
    ): List<Int> {
        // Not all entry nodes are always scheduled.
        // In particular, it happens when the entry node is a task of the included plugin build that runs as part of building the plugin.
        // Such tasks do not rerun when configuration cache is re-used, even if specified on the command line.
        // Not restoring them as entry points doesn't affect the resulting execution plan.
        val scheduledEntryNodeIds = mutableListOf<Int>()
        nodes.forEach { node ->
            val nodeId = scheduledNodeIds.size
            scheduledNodeIds[node] = nodeId
            if (node in entryNodes) {
                scheduledEntryNodeIds.add(nodeId)
            }
            if (node is LocalTaskNode) {
                scheduledNodeIds[node.prepareNode] = scheduledNodeIds.size
            }
        }
        return scheduledEntryNodeIds
    }


    private
    fun WriteContext.writeNodes(
        nodes: List<Node>,
        idForNode: IdForNode
    ): (ActionNode) -> List<Node>? {
        val groupedNodes = nodes.groupBy(NodeOwner::of)
        writeCollection(groupedNodes.keys) { nodeOwner ->
            val groupPath = nodeOwner.path()
            writeString(groupPath.path)
        }

        val batchedActionNodeSuccessors =
            AtomicReference<PersistentList<Iterable<PostExecutionNodes>>>(PersistentList.of())

        runBuildOperations(parallelStore, "saving task graph") {
            groupedNodes.entries.map { (nodeOwner, groupNodes) ->
                val groupPath = nodeOwner.path()
                OperationInfo(displayName = "Storing configuration for $groupPath", context = groupPath) {
                    contextSource.writeContextFor(this, groupPath).useToRun {
                        val postExecutionSuccessors = writeGroupedNodes(nodeOwner, groupNodes, idForNode)
                        if (postExecutionSuccessors.isNotEmpty()) {
                            batchedActionNodeSuccessors.updateAndGet {
                                it.plus(postExecutionSuccessors)
                            }
                        }
                    }
                }
            }
        }

        return combineActionNodeSuccessors(batchedActionNodeSuccessors)::get
    }

    private
    fun combineActionNodeSuccessors(batchedActionNodeSuccessors: AtomicReference<PersistentList<Iterable<PostExecutionNodes>>>) =
        batchedActionNodeSuccessors.get()
            .combineInto(IdentityHashMap<ActionNode, List<Node>>()) { (node, successors) ->
                this[node] = successors
            }

    private
    fun ReadContext.readNodes(nodeIdCount: Int): NodeForId {
        val batchedGroupNodes = AtomicReference<PersistentList<List<NodeWithId>>>(PersistentList.of())
        val groupPaths = readCollectionInto<Path, MutableList<Path>>(::ArrayList) {
            Path.path(readString())
        }

        runBuildOperations(parallel = parallelLoad, message = "reading task graph") {
            groupPaths.map { groupPath ->
                OperationInfo(displayName = "Loading configuration for $groupPath", context = groupPath) {
                    contextSource.readContextFor(this@readNodes, groupPath).readWith(Unit) {
                        val nodesInGroup = readGroupedNodes()
                        batchedGroupNodes.updateAndGet {
                            it.plus(nodesInGroup)
                        }
                    }
                }
            }
        }

        val nodesById = batchedGroupNodes.get()
            .combineInto(Array<Node?>(nodeIdCount) { null }) { (node, id) ->
                this[id] = node
            }
        return { id: Int -> nodesById[id]!! }
    }

    private
    inline fun <T, O> Iterable<Iterable<T>>.combineInto(destination: O, combine: O.(T) -> Unit): O {
        this.forEach { batch ->
            batch.forEach {
                destination.combine(it)
            }
        }
        return destination
    }

    private
    fun <R> handleBuildOperationExceptions(message: String, action: () -> R): R =
        try {
            action()
        } catch (@Suppress("SwallowedException") e: MultipleBuildOperationFailures) {
            if (e.causes.size == 1) {
                throw e.causes[0].maybeUnwrapOperationException()
            }
            throw ConfigurationCacheException({ "Error while $message" }, e.causes)
        }

    private
    fun Throwable.maybeUnwrapOperationException(): Throwable {
        if (this is OperationException) {
            return this.cause!!
        }
        return this
    }

    private
    data class NodeWithId(
        val node: Node,
        val id: Int
    )

    /**
     * Returns a path that uniquely identifies this node owner.
     */
    private
    fun NodeOwner.path(): Path {
        return when (this) {
            NodeOwner.None -> Path.path("build").append(owner.identityPath)
            is NodeOwner.Project -> project.identityPath
        }
    }

    private
    fun WriteContext.writeGroupedNodes(
        nodeOwner: NodeOwner,
        nodes: List<Node>,
        idForNode: IdForNode
    ): List<PostExecutionNodes> {
        val safeRun = safeRunnerFor(nodeOwner)
        val actionNodeSuccessors = mutableListOf<PostExecutionNodes>()
        runWriteOperation {
            writeCollection(nodes) { node ->
                val nodeId = idForNode(node)
                writeSmallInt(nodeId)
                safeRun {
                    write(node)
                    if (node is ActionNode) {
                        collectPostExecutionNodes(node, actionNodeSuccessors)
                    }
                }
                if (node is LocalTaskNode) {
                    val prepareNodeId = idForNode(node.prepareNode)
                    writeSmallInt(prepareNodeId)
                }
            }
        }
        return actionNodeSuccessors
    }

    private
    suspend fun ReadContext.readGroupedNodes(): List<NodeWithId> {
        val size = readSmallInt()
        val nodes = ArrayList<NodeWithId>(size)
        repeat(size) {
            val nodeId = readSmallInt()
            val node = readNode()
            nodes.add(NodeWithId(node, nodeId))
            if (node is LocalTaskNode) {
                val prepareNodeId = readSmallInt()
                val prepareNode = node.prepareNode
                prepareNode.require()
                nodes.add(NodeWithId(prepareNode, prepareNodeId))
            }
        }
        return nodes
    }

    private
    fun WriteContext.collectPostExecutionNodes(
        node: ActionNode,
        postExecutionSuccessors: MutableList<PostExecutionNodes>
    ) {
        val setupNode = node.action?.preExecutionNode
        // Could probably add some abstraction for nodes that can be executed eagerly and discarded
        if (setupNode is PostExecutionNodeAwareActionNode) {
            setupNode.run(object : NodeExecutionContext {
                override fun <T : Any> getService(type: Class<T>): T {
                    return ownerService(type)
                }
            })
            val postExecutionNodes = setupNode.postExecutionNodes
            if (postExecutionNodes.isNotEmpty()) {
                postExecutionSuccessors.add(PostExecutionNodes(node, postExecutionNodes))
            }
        }
    }

    private
    data class PostExecutionNodes(
        val node: ActionNode,
        val postExecutionNodes: List<Node>
    )

    private
    fun safeRunnerFor(owner: NodeOwner): suspend WriteContext.(suspend WriteContext.() -> Unit) -> Unit {
        return when (owner) {
            is NodeOwner.None -> { f -> f() }

            is NodeOwner.Project -> {
                val stateOwner = owner.project.owner
                { f ->
                    stateOwner.applyToMutableState {
                        runWriteOperation {
                            f()
                        }
                    }
                }
            }
        }
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
    fun WriteContext.writeSuccessorReferencesOf(node: Node, actionNodeSuccessors: (ActionNode) -> List<Node>?, scheduledNodeIds: IdForNode) {
        writeSuccessorReferences(dependencySuccessorsOf(node, actionNodeSuccessors), scheduledNodeIds)
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
    fun dependencySuccessorsOf(node: Node, actionNodeSuccessors: (ActionNode) -> List<Node>?): MutableSet<Node> {
        var successors = node.dependencySuccessors
        if (node is ActionNode) {
            actionNodeSuccessors(node)?.let {
                successors = successors + it
            }
        }
        return successors
    }

    private
    fun WriteContext.writeSuccessorReferences(
        successors: Collection<Node>,
        idForNode: IdForNode
    ) {
        for (successor in successors) {
            if (successor.isRequired) {
                val successorId = idForNode(successor)
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

    private
    fun IsolateContext.runBuildOperations(parallel: Boolean, message: String, operations: () -> Iterable<OperationInfo>) {
        val buildOperationExecutor = isolate.owner.serviceOf<BuildOperationExecutor>()
        handleBuildOperationExceptions(message) {
            buildOperationExecutor.runAllWithAccessToProjectState {
                if (parallel) {
                    logger.debug("$message in parallel")
                    // each operation as a proper build operation
                    operations().forEach { add(asBuildOperation(it.displayName, it.context, it.action)) }
                } else {
                    logger.debug("$message sequentially")
                    // all operations under a single build operation
                    add(asBuildOperation(message, Path.ROOT) {
                        operations().forEach { it.action() }
                    })
                }
            }
        }
    }
}


sealed class NodeOwner {

    object None : NodeOwner()

    data class Project(val project: ProjectInternal) : NodeOwner()

    companion object {
        fun of(node: Node): NodeOwner {
            return when (val project = node.owningProject) {
                null -> None
                else -> Project(project)
            }
        }
    }
}


private
data class OperationInfo(
    val displayName: String,
    val context: Path,
    val action: () -> Unit
)


private
class OperationException(message: String, cause: Throwable): RuntimeException(message, cause)


private
fun asBuildOperation(displayName: String, contextPath: Path, action: () -> Unit): RunnableBuildOperation =
    object : RunnableBuildOperation {
        override fun run(context: BuildOperationContext) {
            try {
                action.invoke()
            } catch (e: Exception) {
                throw OperationException("Exception while ${StringUtils.uncapitalize(displayName)}: ${e.message}", e)
            }
        }

        override fun description(): BuildOperationDescriptor.Builder {
            return BuildOperationDescriptor
                .displayName(displayName)
                .progressDisplayName(contextPath.path)
        }
    }
