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

import org.gradle.api.internal.TaskInternal
import org.gradle.execution.plan.LocalTaskNode
import org.gradle.execution.plan.Node
import org.gradle.execution.plan.ScheduledWork
import org.gradle.util.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock


/**
 * Unit tests for [WorkGraphPruner]. The pruner compares `task.identityPath`
 * (canonical absolute path) against `entryTaskIdentityPathsToDrop`, so all task names in these
 * tests are written in `:`-prefixed canonical form.
 */
class WorkGraphPrunerTest {

    @Test
    fun `empty entryTaskIdentityPathsToDrop is a true no-op — same instance returned`() {
        val first = taskNode(":first")
        val second = taskNode(":second")
        val input = ScheduledWork(listOf(first, second), setOf(first, second))

        val result = WorkGraphPruner.pruneAndRewireInPlace(input, emptySet())

        // Identity equality — no defensive copy, no reconstruction, exact instance.
        assertSame(input, result)
        assertSame(input.entryNodes, result.entryNodes)
        assertSame(input.scheduledNodes, result.scheduledNodes)
    }

    @Test
    fun `independent tasks get dropped without retaining each other`() {
        val first = taskNode(":first")
        val second = taskNode(":second")
        // first and second are independent — neither lists the other as a dependency successor.
        val input = ScheduledWork(listOf(first, second), setOf(first, second))

        // Request was just :second; :first is in entryTaskIdentityPathsToDrop.
        val result = WorkGraphPruner.pruneAndRewireInPlace(input, setOf(":first"))

        assertEquals(setOf<Node>(second), result.entryNodes)
        assertEquals(listOf<Node>(second), result.scheduledNodes)
    }

    @Test
    fun `BFS-retained transitive dep stays in entryNodes so ordinal-group bookkeeping holds`() {
        // Stored entry was `gradle :originalInputs :incrementalReverse` (both entries,
        // different ordinal groups). Re-request is `:incrementalReverse` alone.
        // :incrementalReverse depends on :originalInputs, so BFS retains :originalInputs
        // in the scheduled set. The Node for :originalInputs still carries its
        // original ordinal group from the cold-store build — if we drop it from
        // entryNodes, the loaded plan ends up with an "ownerless" ordinal group whose
        // destroyer-location node is scheduled but no entry node satisfies it, and the
        // executor deadlocks.
        val originalInputs = taskNode(":originalInputs")
        val incrementalReverse = taskNode(":incrementalReverse", dependencies = listOf(originalInputs))
        val input = ScheduledWork(
            listOf(originalInputs, incrementalReverse),
            setOf(originalInputs, incrementalReverse)
        )

        val result = WorkGraphPruner.pruneAndRewireInPlace(input, setOf(":originalInputs"))

        assertEquals(setOf<Node>(originalInputs, incrementalReverse), result.entryNodes)
        assertEquals(listOf<Node>(originalInputs, incrementalReverse), result.scheduledNodes)
    }

    @Test
    fun `nested-project identity paths drop only the matching task`() {
        val fooBar = taskNode(":foo:bar")
        val fooBaz = taskNode(":foo:baz")
        val input = ScheduledWork(listOf(fooBar, fooBaz), setOf(fooBar, fooBaz))

        // Stored had :foo:bar + :foo:baz; current request is just :foo:bar.
        val result = WorkGraphPruner.pruneAndRewireInPlace(input, setOf(":foo:baz"))

        assertEquals(setOf<Node>(fooBar), result.entryNodes)
        assertEquals(listOf<Node>(fooBar), result.scheduledNodes)
    }

    private
    fun taskNode(identityPath: String, dependencies: List<Node> = emptyList()): LocalTaskNode {
        val task = mock<TaskInternal> {
            on { getIdentityPath() } doReturn Path.path(identityPath)
        }
        return mock<LocalTaskNode> {
            on { getTask() } doReturn task
            on { dependencySuccessors } doReturn dependencies.toMutableSet()
            // Mutable set so the pruner's stale-predecessor cleanup (removeAll) doesn't NPE.
            on { dependencyPredecessors } doReturn mutableSetOf()
        }
    }
}
