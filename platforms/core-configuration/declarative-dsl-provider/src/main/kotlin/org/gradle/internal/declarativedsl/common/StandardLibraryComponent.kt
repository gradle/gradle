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

import org.gradle.api.provider.ListProperty
import org.gradle.declarative.dsl.schema.AssignmentAugmentation
import org.gradle.declarative.dsl.schema.FqName
import org.gradle.internal.Cast
import org.gradle.internal.declarativedsl.analysis.AssignmentAugmentationKindInternal
import org.gradle.internal.declarativedsl.analysis.DefaultAssignmentAugmentation
import org.gradle.internal.declarativedsl.analysis.DefaultDataParameter
import org.gradle.internal.declarativedsl.analysis.DefaultDataTopLevelFunction
import org.gradle.internal.declarativedsl.analysis.DefaultFqName
import org.gradle.internal.declarativedsl.analysis.FunctionSemanticsInternal
import org.gradle.internal.declarativedsl.analysis.ParameterSemanticsInternal
import org.gradle.internal.declarativedsl.evaluationSchema.AnalysisSchemaComponent
import org.gradle.internal.declarativedsl.evaluationSchema.ObjectConversionComponent
import org.gradle.internal.declarativedsl.evaluationSchema.gradleConfigureLambdas
import org.gradle.internal.declarativedsl.intrinsics.IntrinsicRuntimeFunctionCandidatesProvider
import org.gradle.internal.declarativedsl.intrinsics.gradleRuntimeIntrinsicsKClass
import org.gradle.internal.declarativedsl.mappingToJvm.DeclarativeRuntimePropertySetter.Companion.skipSetterSpecialValue
import org.gradle.internal.declarativedsl.mappingToJvm.DefaultRuntimeFunctionResolver
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeFunctionResolver
import org.gradle.internal.declarativedsl.schemaBuilder.AugmentationsProvider
import org.gradle.internal.declarativedsl.schemaBuilder.CompositeTopLevelFunctionDiscovery
import org.gradle.internal.declarativedsl.schemaBuilder.DefaultImportsProvider
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaBuildingHost
import org.gradle.internal.declarativedsl.schemaBuilder.TopLevelFunctionDiscovery
import org.gradle.internal.declarativedsl.schemaBuilder.inContextOfModelMember
import kotlin.reflect.KFunction
import kotlin.reflect.full.functions
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

    override fun augmentationsProviders(): List<AugmentationsProvider> = listOf(
        object : AugmentationsProvider {
            override fun augmentations(host: SchemaBuildingHost): Map<FqName, List<AssignmentAugmentation>> {
                val listSignatureFunction = StandardLibraryComponent::class.functions.single { it.name == "listSignature" }
                return host.inContextOfModelMember(listSignatureFunction) {
                    val listOfTType = host.modelTypeRef(listSignatureFunction.parameters.last().type)
                    mapOf(
                        DefaultFqName.parse(List::class.qualifiedName!!) to listOf(
                            DefaultAssignmentAugmentation(
                                AssignmentAugmentationKindInternal.DefaultPlus,
                                DefaultDataTopLevelFunction(
                                    LIST_AUGMENTATION_FUNCTION_PACKAGE,
                                    ::builtinListAugmentation.javaMethod!!.declaringClass.name,
                                    LIST_AUGMENTATION_FUNCTION_NAME,
                                    listOf(
                                        DefaultDataParameter("lhs", listOfTType, false, ParameterSemanticsInternal.DefaultUnknown),
                                        DefaultDataParameter("rhs", listOfTType, false, ParameterSemanticsInternal.DefaultUnknown)
                                    ),
                                    FunctionSemanticsInternal.DefaultPure(listOfTType)
                                ),
                            )
                        )
                    )
                }
            }
        }
    )

    @Suppress("unused") // declared just for getting the List<T> type reflection
    private fun <T> listSignature(param: List<T>) = param

    private val LIST_AUGMENTATION_FUNCTION_PACKAGE = ::builtinListAugmentation.javaMethod!!.declaringClass.`package`.name
    private val LIST_AUGMENTATION_FUNCTION_NAME = ::builtinListAugmentation.name
}

fun builtinListAugmentation(lhs: Any, rhs: List<*>): Any =
    when (lhs) {
        is List<*> -> lhs + rhs
        is ListProperty<*> -> {
            Cast.uncheckedNonnullCast<ListProperty<Any>>(lhs).addAll(Cast.uncheckedNonnullCast<List<Any>>(rhs))
            skipSetterSpecialValue
        }

        else -> error("Unexpected augmented list property value: $lhs")
    }
