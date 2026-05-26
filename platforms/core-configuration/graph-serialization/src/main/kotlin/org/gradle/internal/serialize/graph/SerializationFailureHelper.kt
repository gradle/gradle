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

import org.gradle.internal.configuration.problems.PropertyProblem
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.configuration.problems.StructuredMessage
import org.gradle.internal.problems.failure.FailureFactory


/**
 * Returns a human-readable description of the task currently being serialized,
 * for use in error messages. Falls back to the full property trace when no
 * task frame is on the trace.
 */
fun PropertyTrace.taskDescription(): String =
    sequence
        .filterIsInstance<PropertyTrace.Task>()
        .firstOrNull()
        ?.let { "task ${it.path} of type ${it.type.simpleName}" }
        ?: toString()


/**
 * Reports a serialization failure as a non-fatal configuration cache problem
 * at the [current trace][WriteContext.trace].
 *
 * This does not interrupt encoding. The caller is expected to write a
 * self-consistent placeholder in place of the offending value. The
 * [exception] is attached to the resulting
 * [org.gradle.internal.configuration.problems.PropertyProblem], so it
 * surfaces as a cause of `ConfigurationCacheProblemsException` when the
 * build later fails due to deferred problems, and its
 * [resolutions][org.gradle.internal.exceptions.ResolutionProvider]
 * remain visible to `failure.assertHasResolution(...)`.
 *
 * Callers who need a new trace frame (e.g., bean-field encoders that have
 * not yet entered the field's property trace) should wrap with
 * [withPropertyTrace] themselves; this keeps the trace surface explicit at
 * the call site instead of conflating it with the problem-reporting helper.
 */
suspend fun WriteContext.reportSerializationProblem(exception: Exception) {
    val failureFactory = ownerService<FailureFactory>()
    val message = StructuredMessage.build {
        text("failed to serialize value of ")
        reference(trace.toString())
    }
    onProblem(
        PropertyProblem(
            trace = trace,
            message = message,
            exception = exception,
            stackTracingFailure = failureFactory.create(exception)
        )
    )
}
