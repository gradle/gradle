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

package org.gradle.internal.declarativedsl.mappingToJvm

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.internal.declarativedsl.analysis.ResolutionResult
import org.gradle.internal.declarativedsl.analysis.SchemaTypeRefContext
import org.gradle.internal.declarativedsl.demo.assignmentTrace
import org.gradle.internal.declarativedsl.objectGraph.ReflectionContext
import org.gradle.internal.declarativedsl.objectGraph.reflect
import org.gradle.internal.declarativedsl.schemaBuilder.ConfigureLambdaHandler


internal
fun <T : Any> runtimeInstanceFromResult(
    schema: AnalysisSchema,
    resolution: ResolutionResult,
    configureLambdas: ConfigureLambdaHandler,
    customAccessors: RuntimeCustomAccessors,
    createInstance: () -> T
): T {
    val trace = assignmentTrace(resolution)
    val context = ReflectionContext(SchemaTypeRefContext(schema), resolution, trace)
    val topLevel = reflect(resolution.topLevelReceiver, context)

    return createInstance().also {
        DeclarativeReflectionToObjectConverter(
            emptyMap(), it, MemberFunctionResolver(configureLambdas), ReflectionRuntimePropertyResolver, customAccessors
        ).apply(topLevel)
    }
}
