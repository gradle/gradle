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

package org.gradle.internal.declarativedsl.evaluationSchema

import org.gradle.internal.declarativedsl.demo.resolve
import org.gradle.internal.declarativedsl.evaluator.conversion.EvaluationAndConversionSchema
import org.gradle.internal.declarativedsl.mappingToJvm.CompositeCustomAccessors
import org.gradle.internal.declarativedsl.mappingToJvm.CompositeFunctionResolver
import org.gradle.internal.declarativedsl.mappingToJvm.runtimeInstanceFromResult

fun EvaluationAndConversionSchema.interpretWithConversion(target: Any, code: String) {
    val conversionComponents = conversionSchemaForScriptTarget(target)
    val resolution = analysisSchema.resolve(code)
    runtimeInstanceFromResult(
        analysisSchema,
        resolution,
        gradleConfigureLambdas,
        CompositeCustomAccessors(conversionComponents.runtimeCustomAccessors),
        { target },
        CompositeFunctionResolver(conversionComponents.runtimeFunctionResolvers)
    )
}
