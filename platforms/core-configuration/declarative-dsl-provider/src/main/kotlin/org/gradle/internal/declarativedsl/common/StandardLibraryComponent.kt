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

package org.gradle.internal.declarativedsl.common

import org.gradle.declarative.dsl.schema.FqName
import org.gradle.internal.declarativedsl.analysis.DefaultFqName
import org.gradle.internal.declarativedsl.evaluationSchema.AnalysisSchemaComponent
import org.gradle.internal.declarativedsl.evaluationSchema.ObjectConversionComponent
import org.gradle.internal.declarativedsl.evaluationSchema.gradleConfigureLambdas
import org.gradle.internal.declarativedsl.intrinsics.IntrinsicRuntimeFunctionCandidatesProvider
import org.gradle.internal.declarativedsl.intrinsics.gradleRuntimeIntrinsicsKClass
import org.gradle.internal.declarativedsl.mappingToJvm.DefaultRuntimeFunctionResolver
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeFunctionResolver
import org.gradle.internal.declarativedsl.schemaBuilder.CompositeTopLevelFunctionDiscovery
import org.gradle.internal.declarativedsl.schemaBuilder.DefaultImportsProvider
import org.gradle.internal.declarativedsl.schemaBuilder.TopLevelFunctionDiscovery
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.kotlinFunction

/**
 * Contributes some intrinsics like [listOf] to the schema. Ensures that they get proper runtime resolution.
 */
object StandardLibraryComponent : AnalysisSchemaComponent, ObjectConversionComponent {
    override fun topLevelFunctionDiscovery(): List<TopLevelFunctionDiscovery> = listOf(
        object : TopLevelFunctionDiscovery {
            override fun discoverTopLevelFunctions(): List<KFunction<*>> = listOf(kotlinCollectionsListOf)

            private val kotlinCollectionsListOf =
                Class.forName("kotlin.collections.CollectionsKt").methods.single { it.name == "listOf" && it.parameters.singleOrNull()?.isVarArgs == true }.kotlinFunction!!
        }
    )

    override fun runtimeFunctionResolvers(): List<RuntimeFunctionResolver> = listOf(
        DefaultRuntimeFunctionResolver(gradleConfigureLambdas, IntrinsicRuntimeFunctionCandidatesProvider(listOf(gradleRuntimeIntrinsicsKClass)))
    )

    override fun defaultImportsProvider(): List<DefaultImportsProvider> = listOf(
        object : DefaultImportsProvider {
            override fun defaultImports(): List<FqName> =
                CompositeTopLevelFunctionDiscovery(topLevelFunctionDiscovery()).discoverTopLevelFunctions().map { function ->
                    DefaultFqName(function.javaMethod!!.declaringClass.`package`.name.orEmpty(), function.name)
                }
        }
    )
}
