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

package org.gradle.internal.serialize.graph

import org.gradle.api.Task
import org.gradle.internal.configuration.problems.ProblemsListener
import org.gradle.internal.configuration.problems.PropertyProblem
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.configuration.problems.StructuredMessage
import org.gradle.internal.configuration.problems.StructuredMessageBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class BufferingProblemsListenerTest {

    private
    fun problem(message: String) =
        PropertyProblem(PropertyTrace.Gradle, StructuredMessage.build { text(message) })

    @Test
    fun `defers problems until replay, preserving order`() {
        val delegate = RecordingProblemsListener()
        val listener = BufferingProblemsListener(delegate)
        val p1 = problem("p1")
        val p2 = problem("p2")

        listener.onProblem(p1)
        listener.onProblem(p2)

        assertTrue("problems must not be forwarded before replay", delegate.problems.isEmpty())
        assertEquals(listOf(p1, p2), listener.bufferedProblems())

        listener.replay()
        assertEquals(listOf(p1, p2), delegate.problems)
    }

    @Test
    fun `defers execution-time problems until replay`() {
        val delegate = RecordingProblemsListener()
        val listener = BufferingProblemsListener(delegate)
        val p = problem("exec")

        listener.onExecutionTimeProblem(p)

        assertTrue(delegate.executionTimeProblems.isEmpty())
        assertEquals(listOf(p), listener.bufferedProblems())

        listener.replay()
        assertEquals(listOf(p), delegate.executionTimeProblems)
    }

    @Test
    fun `forwards errors to the delegate immediately and does not buffer them`() {
        val delegate = RecordingProblemsListener()
        val listener = BufferingProblemsListener(delegate)
        val error = RuntimeException("boom")

        listener.onError(PropertyTrace.Gradle, error) { text("boom") }

        assertEquals(listOf<Exception>(error), delegate.errors)
        assertTrue("errors must not be buffered", listener.bufferedProblems().isEmpty())
    }

    @Test
    fun `propagates a delegate error rethrow immediately`() {
        val delegate = RecordingProblemsListener().apply { rethrowErrors = true }
        val listener = BufferingProblemsListener(delegate)
        val error = IOException("must abort")

        val thrown = assertThrows(IOException::class.java) {
            listener.onError(PropertyTrace.Gradle, error) { text("must abort") }
        }
        assertSame(error, thrown)
    }

    @Test
    fun `replay routes deferred problems through the matching task-scoped delegate`() {
        val delegate = RecordingProblemsListener()
        val listener = BufferingProblemsListener(delegate)
        val p = problem("incompatible")

        val scoped = listener.forIncompatibleTask(PropertyTrace.Gradle, "reason")
        scoped.onProblem(p)

        // The scoped listener resolves its delegate eagerly, but defers the problem itself.
        assertEquals(1, delegate.incompatibleChildren.size)
        assertTrue(delegate.problems.isEmpty())
        assertTrue(delegate.incompatibleChildren.single().problems.isEmpty())

        listener.replay()
        assertEquals(listOf(p), delegate.incompatibleChildren.single().problems)
    }

    private
    class RecordingProblemsListener : ProblemsListener {
        val problems = mutableListOf<PropertyProblem>()
        val executionTimeProblems = mutableListOf<PropertyProblem>()
        val errors = mutableListOf<Exception>()
        val incompatibleChildren = mutableListOf<RecordingProblemsListener>()
        var rethrowErrors = false

        override fun onProblem(problem: PropertyProblem) {
            problems.add(problem)
        }

        override fun onError(trace: PropertyTrace, error: Exception, message: StructuredMessageBuilder) {
            errors.add(error)
            if (rethrowErrors) throw error
        }

        override fun onExecutionTimeProblem(problem: PropertyProblem) {
            executionTimeProblems.add(problem)
        }

        override fun forIncompatibleTask(trace: PropertyTrace, reason: String): ProblemsListener =
            RecordingProblemsListener().also { incompatibleChildren.add(it) }

        override fun forTask(task: Task): ProblemsListener = this
    }
}
