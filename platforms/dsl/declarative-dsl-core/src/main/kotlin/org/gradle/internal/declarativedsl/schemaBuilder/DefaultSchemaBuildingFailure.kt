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

package org.gradle.internal.declarativedsl.schemaBuilder

import org.gradle.declarative.dsl.evaluation.SchemaBuildingFailure
import org.gradle.declarative.dsl.evaluation.SchemaIssue
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaBuildingContextElement.TagContextElement
import kotlin.reflect.KFunction
import kotlin.reflect.full.instanceParameter

data class DefaultSchemaBuildingFailure(
    override val issue: SchemaIssue,
    override val context: List<SchemaBuildingFailure.FailureContext>
) : SchemaBuildingFailure

internal fun SchemaResult.Failure.asReportableFailure(): SchemaBuildingFailure =
DefaultSchemaBuildingFailure(issue, contextElements.map { element -> element.asFailureContext() })

internal fun SchemaBuildingContextElement.asFailureContext(): SchemaBuildingFailure.FailureContext = when (this) {
    is SchemaBuildingContextElement.ModelClassContextElement -> DefaultFailureContext.ClassFailureContext(kClass.qualifiedName ?: kClass.java.name)
    is TagContextElement -> DefaultFailureContext.TagFailureContext(userVisibleTag)
    is SchemaBuildingContextElement.ModelMemberContextElement -> DefaultFailureContext.MemberFailureContext(
        buildString {
            append(kCallable.name)
            if (kCallable is KFunction<*>) {
                append("(")
                append(
                    kCallable.parameters
                        .filter { it != kCallable.instanceParameter }
                        .joinToString { "${it.name}: ${it.type}" })
                append(")")
            }
        },
        "member '${kCallable}'"
    )

}
