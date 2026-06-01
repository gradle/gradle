/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.cc.impl

import org.gradle.execution.plan.LocalTaskNode
import org.gradle.execution.plan.Node
import org.gradle.execution.plan.ScheduledWork


/**
 * Prunes a loaded [ScheduledWork] to the requested entry tasks plus their transitive
 * dependencies, when a stored configuration cache entry is reused for a subset of its
 * original requested-task list. Used by `ConfigurationCacheState` at the deserialization
 * boundary; the loader sees the pruned plan via `setScheduledWork`.
 * <p>
 * **Not pure** — [pruneAndRewireInPlace] mutates retained nodes' `dependencyPredecessors`
 * to drop references to non-retained nodes. Without this, `DefaultFinalizedExecutionPlan`
 * sees stale predecessor backrefs and refuses to schedule the retained entries.
 * Callers should treat the input `ScheduledWork` as consumed.
 * <p>
 * Identity paths in `entryTaskIdentityPathsToDrop` must be canonical (e.g. `":foo:bar"`)
 * to compare equal to `task.identityPath.asString()`.
 */
internal
object WorkGraphPruner {

    /**
     * Returns [initiallyScheduled] with entry nodes whose canonical identity path is
     * in [entryTaskIdentityPathsToDrop] removed, plus any nodes no longer reachable from
     * the remaining entry nodes through `dependencySuccessors`. Mutates retained nodes'
     * `dependencyPredecessors` to drop dropped-node backrefs.
     * <p>
     * Returns the input unchanged for exact-match reuse (empty drop set) or when no entry
     * matches the drop set.
     */
    fun pruneAndRewireInPlace(initiallyScheduled: ScheduledWork, entryTaskIdentityPathsToDrop: Set<String>): ScheduledWork {
        if (entryTaskIdentityPathsToDrop.isEmpty()) return initiallyScheduled

        val requestedEntries = initiallyScheduled.entryNodes.filter { node ->
            val task = (node as? LocalTaskNode)?.task
            task == null || task.identityPath.asString() !in entryTaskIdentityPathsToDrop
        }.toSet()
        if (requestedEntries.size == initiallyScheduled.entryNodes.size) return initiallyScheduled

        val retained = bfsForwardThroughDependencies(requestedEntries)
        val retainedScheduled = initiallyScheduled.scheduledNodes.filter { it in retained }
        clearStalePredecessors(retained)

        // Original entry nodes that BFS retained as transitive deps must remain entries:
        // their ordinal-group bookkeeping references them, and dropping them produces
        // phantom groups that deadlock the executor.
        val retainedEntries = initiallyScheduled.entryNodes.filter { it in retained }.toSet()
        return ScheduledWork(retainedScheduled, retainedEntries)
    }

    /**
     * BFS through `dependencySuccessors` only. `mustRunAfter` / finalizer edges that cross
     * the requested/dropped boundary are filtered out at lookup time
     * (`ConfigurationCacheRepository.findCacheEntry`), so dependency-edge reachability is
     * the only retention rule needed here.
     */
    private fun bfsForwardThroughDependencies(roots: Set<Node>): Set<Node> {
        val retained = HashSet<Node>(roots)
        val queue = ArrayDeque<Node>().apply { addAll(roots) }
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            for (dep in node.dependencySuccessors) {
                if (retained.add(dep)) queue.addLast(dep)
            }
        }
        return retained
    }

    /**
     * Drops backref edges from retained nodes to dropped nodes (e.g. `:duplicate` retains a
     * predecessor edge from a dropped `:a:compileJava` because `:a:compileJava` originally
     * depended on `:duplicate`). Stale predecessors cause `maybeWaitingForNewNode` to
     * exclude retained entries from `waitingToStartNodes`, so the build finishes without
     * executing the retained tasks.
     */
    private fun clearStalePredecessors(retained: Set<Node>) {
        for (node in retained) {
            node.dependencyPredecessors.removeAll { it !in retained }
        }
    }
}
