/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.declarativedsl.schemaBuilder

import org.gradle.internal.declarativedsl.analysis.DeclarativeDslInterpretationException

class DeclarativeDslSchemaBuildingException(message: String) : DeclarativeDslInterpretationException(message)

/**
 * Represents an unexpected condition during schema building, such as a failed invariant of an implementation, or a failed assumption
 * about the behavior of a dependency.
 */
class UnexpectedSchemaBuildingException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

/**
 * Produces a failure result of a schema building operation.
 * The failure carries the [SchemaBuildingHost.context].
 */
fun SchemaBuildingHost.schemaBuildingFailure(issue: SchemaBuildingIssue): SchemaResult.Failure = SchemaResult.Failure(issue, context.toList())

/**
 * Throws an error due to a failing expectation of the schema builder (for example, if an assumption about a Kotlin reflection contract does not hold, or the implementation failed to find the type
 * legally referenced somewhere in the schema)
 */
fun SchemaBuildingHost.schemaBuildingError(message: String): Nothing = throw UnexpectedSchemaBuildingException(
    "Schema building error: $message" + (context.takeIf { it.isNotEmpty()} ?.let { "\n" + SchemaFailureMessageFormatter.contextRepresentation(context) } ?: "")
)



