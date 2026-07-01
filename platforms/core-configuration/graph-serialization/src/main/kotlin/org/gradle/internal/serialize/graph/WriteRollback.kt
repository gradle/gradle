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
import org.gradle.internal.configuration.problems.StructuredMessageBuilder


/**
 * Byte-level control over a rollback scope, provided by the I/O layer that owns the encoder and its
 * output. Everything written between [beginScope] and [commitScope] is relayed to the real
 * destination verbatim; everything written between [beginScope] and [rollbackScope] is discarded.
 *
 * Nesting is not supported: [beginScope] must be balanced by exactly one [commitScope] or
 * [rollbackScope].
 */
interface WriteRollback {
    fun beginScope()

    fun commitScope()

    fun rollbackScope()
}


/**
 * A rollback scope opened by [WriteContext.beginRollbackScope].
 *
 * Must be finished with exactly one [commit] or [rollback], in the same coroutine frame that opened
 * it and only after the guarded write has returned or thrown (never while it is suspended).
 */
interface WriteRollbackScope {
    /**
     * Keeps everything written since the scope began and replays the problems that were deferred
     * during the scope to the enclosing problems listener.
     */
    fun commit()

    /**
     * Discards everything written since the scope began and restores the serialization state to the
     * point the scope was opened.
     *
     * @return the problems that were deferred during the scope, for the caller to inspect and, if
     * desired, re-report (e.g. collapsed into a single placeholder problem) via the context.
     */
    fun rollback(): List<PropertyProblem>
}


/**
 * A [ProblemsListener] that defers the problems reported to it instead of forwarding, so that
 * problems discovered during a speculative write are only surfaced once the write is committed
 * (via [replay]), or are handed back to the caller on rollback (via [bufferedProblems]).
 *
 * [onError] is NOT deferred: an error carries control-flow meaning (e.g. the configuration cache
 * problems listener re-throws [java.io.IOException] to abort serialization), so it is forwarded to
 * the [delegate] immediately. Deferring it would let the write appear to succeed and only surface
 * the failure later, during replay, outside the caller's rollback handling.
 *
 * Task routing is preserved: a [forIncompatibleTask]/[forTask] listener defers onto the
 * corresponding scoped listener of the [delegate].
 */
class BufferingProblemsListener private constructor(
    private val delegate: ProblemsListener,
    private val deferred: MutableList<() -> Unit>,
    private val problems: MutableList<PropertyProblem>
) : ProblemsListener {

    constructor(delegate: ProblemsListener) : this(delegate, mutableListOf(), mutableListOf())

    override fun onProblem(problem: PropertyProblem) {
        problems.add(problem)
        deferred.add { delegate.onProblem(problem) }
    }

    override fun onError(trace: PropertyTrace, error: Exception, message: StructuredMessageBuilder) {
        delegate.onError(trace, error, message)
    }

    override fun onExecutionTimeProblem(problem: PropertyProblem) {
        problems.add(problem)
        deferred.add { delegate.onExecutionTimeProblem(problem) }
    }

    override fun forIncompatibleTask(trace: PropertyTrace, reason: String): ProblemsListener =
        BufferingProblemsListener(delegate.forIncompatibleTask(trace, reason), deferred, problems)

    override fun forTask(task: Task): ProblemsListener =
        BufferingProblemsListener(delegate.forTask(task), deferred, problems)

    /**
     * The problems deferred during the scope, for inspection on rollback.
     */
    fun bufferedProblems(): List<PropertyProblem> = problems.toList()

    /**
     * Forwards all deferred problems to the delegate, preserving order and task routing.
     */
    fun replay() {
        deferred.forEach { it() }
    }
}
