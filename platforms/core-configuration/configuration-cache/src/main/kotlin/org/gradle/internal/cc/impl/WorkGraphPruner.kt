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
 * Prunes a loaded [ScheduledWork] to keep only the requested entry tasks plus their
 * transitive dependencies, when a stored configuration cache entry is reused for
 * a subset of its original requested-task list.
 *
 * Pure: no I/O, no service dependencies. Used by `ConfigurationCacheState` at the
 * deserialization boundary so the loader sees the pruned plan via
 * `setScheduledWork`.
 *
 * Names in [prune]'s `tasksToDrop` are compared against `task.identityPath`
 * (canonical absolute path like `":foo:bar"`). Callers are responsible for
 * passing canonical paths — see `DefaultConfigurationCache.commitCacheEntry`
 * (where stored identity paths originate) and the lookup side which
 * canonicalizes CLI input.
 */
internal
object WorkGraphPruner {

    /**
     * Returns [initiallyScheduled] with entry nodes whose canonical identity path
     * is in [tasksToDrop] removed, plus any nodes that are no longer reachable
     * from the remaining entry nodes through `dependencySuccessors`.
     *
     * Returns the input unchanged when:
     *  - [tasksToDrop] is empty (exact-match reuse — no pruning needed), or
     *  - none of the entry nodes' tasks match [tasksToDrop] (no-op for this build).
     */
    fun prune(initiallyScheduled: ScheduledWork, tasksToDrop: Set<String>): ScheduledWork {
        if (tasksToDrop.isEmpty()) return initiallyScheduled
        val requestedEntries = initiallyScheduled.entryNodes.filter { node ->
            val task = (node as? LocalTaskNode)?.task
            task == null || task.identityPath.toString() !in tasksToDrop
        }.toSet()
        if (requestedEntries.size == initiallyScheduled.entryNodes.size) return initiallyScheduled
        // BFS forward from the requested entries through real dependency edges only.
        // Entries with mustRunAfter / finalizer relationships that cross the
        // requested/dropped boundary are filtered out at lookup time
        // (see `ConfigurationCacheRepository.findCompatibleEntry`), so by the time
        // we reach this code the pruning is guaranteed to be safe under
        // dependency-graph semantics.
        val retained = HashSet<Node>()
        val queue = ArrayDeque<Node>()
        queue.addAll(requestedEntries)
        retained.addAll(requestedEntries)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            for (dep in node.dependencySuccessors) {
                if (retained.add(dep)) queue.addLast(dep)
            }
        }
        val retainedScheduled = initiallyScheduled.scheduledNodes.filter { it in retained }
        // Clean up stale incoming edges: the loaded graph's retained nodes still
        // reference DROPPED nodes via `dependencyPredecessors` (e.g. `:duplicate`
        // retains a predecessor edge from a dropped `:a:compileJava` because
        // `:a:compileJava` originally depended on `:duplicate`). Without this
        // cleanup, `DefaultFinalizedExecutionPlan.maybeWaitingForNewNode` sees
        // non-empty predecessors and doesn't add the retained entry to
        // `waitingToStartNodes` — the executor then reports "no more work to
        // start" and the build finishes without executing the retained tasks.
        for (node in retained) {
            node.dependencyPredecessors.removeAll { it !in retained }
        }
        // Entry-node set must include any *original* entry node that BFS retained as a
        // transitive dep — without this, the loaded plan's ordinal-group bookkeeping
        // ends up with phantom groups (the dropped entry's group still has destroyer-
        // location nodes scheduled but no entry, deadlocking the executor).
        // Independent dropped entries that aren't BFS-reached stay out — that's how
        // we actually shrink the work.
        val retainedEntries = initiallyScheduled.entryNodes.filter { it in retained }.toSet()
        return ScheduledWork(retainedScheduled, retainedEntries)
    }
}
