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
import org.gradle.api.provider.MapProperty
import org.gradle.declarative.dsl.schema.AssignmentAugmentation
import org.gradle.declarative.dsl.schema.DataTopLevelFunction
import org.gradle.declarative.dsl.schema.FqName
import org.gradle.internal.Cast
import org.gradle.internal.declarativedsl.analysis.AssignmentAugmentationKindInternal
import org.gradle.internal.declarativedsl.analysis.DefaultAssignmentAugmentation
import org.gradle.internal.declarativedsl.analysis.DefaultDataParameter
import org.gradle.internal.declarativedsl.analysis.DefaultDataTopLevelFunction
import org.gradle.internal.declarativedsl.analysis.DefaultFqName
import org.gradle.internal.declarativedsl.analysis.DefaultVarargParameter
import org.gradle.internal.declarativedsl.analysis.FunctionSemanticsInternal
import org.gradle.internal.declarativedsl.analysis.ParameterSemanticsInternal
import org.gradle.internal.declarativedsl.evaluationSchema.AnalysisSchemaComponent
import org.gradle.internal.declarativedsl.evaluationSchema.FixedTypeDiscovery
import org.gradle.internal.declarativedsl.evaluationSchema.ObjectConversionComponent
import org.gradle.internal.declarativedsl.evaluationSchema.gradleConfigureLambdas
import org.gradle.internal.declarativedsl.intrinsics.IntrinsicRuntimeFunctionCandidatesProvider
import org.gradle.internal.declarativedsl.intrinsics.gradleRuntimeIntrinsicsKClass
import org.gradle.internal.declarativedsl.mappingToJvm.DeclarativeRuntimePropertySetter.Companion.skipSetterSpecialValue
import org.gradle.internal.declarativedsl.mappingToJvm.DefaultRuntimeFunctionResolver
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeFunctionResolver
import org.gradle.internal.declarativedsl.schemaBuilder.AugmentationsProvider
import org.gradle.internal.declarativedsl.schemaBuilder.CompositeTopLevelFunctionDiscovery
import org.gradle.internal.declarativedsl.schemaBuilder.DataSchemaBuilder
import org.gradle.internal.declarativedsl.schemaBuilder.DefaultImportsProvider
import org.gradle.internal.declarativedsl.schemaBuilder.FunctionExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaBuildingHost
import org.gradle.internal.declarativedsl.schemaBuilder.TopLevelFunctionDiscovery
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery
import org.gradle.internal.declarativedsl.schemaBuilder.inContextOfModelMember
import org.gradle.internal.declarativedsl.schemaBuilder.parameterTypeToRefOrError
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.kotlinFunction

/**
 * Contributes some intrinsics like [listOf], [mapOf], [to] to the schema.
 * Ensures that they get proper runtime resolution.
 */
object StandardLibraryComponent : AnalysisSchemaComponent, ObjectConversionComponent {
    override fun typeDiscovery(): List<TypeDiscovery> = listOf(FixedTypeDiscovery(null, listOf(Pair::class)))

    override fun topLevelFunctionDiscovery(): List<TopLevelFunctionDiscovery> = listOf(
        object : TopLevelFunctionDiscovery {
            override fun discoverTopLevelFunctions(): List<KFunction<*>> = listOf(
                kotlinCollectionsListOf,
                kotlinCollectionsMapOf,
                kotlinTo
            )

            private val kotlinCollectionsListOf =
                Class.forName("kotlin.collections.CollectionsKt").methods.single { it.name == "listOf" && it.parameters.singleOrNull()?.isVarArgs == true }.kotlinFunction!!
        }
    )

    private val kotlinTo: KFunction<*> =
        Class.forName("kotlin.TuplesKt").methods.single { it.name == "to" }.kotlinFunction!!

    private val kotlinCollectionsMapOf =
        Class.forName("kotlin.collections.MapsKt").methods.single { it.name == "mapOf" && it.parameters.singleOrNull()?.isVarArgs == true }.kotlinFunction!!

    override fun functionExtractors(): List<FunctionExtractor> = listOf(
        /**
         * Function extractor for [to], which needs to be represented as a top-level receiver-less function in the schema.
         * This function extractor needs to run before the default one, so this one takes priority and handles the [to] function.
         */
        object : FunctionExtractor {
            override fun topLevelFunction(
                host: SchemaBuildingHost,
                function: KFunction<*>,
                preIndex: DataSchemaBuilder.PreIndex
            ): DataTopLevelFunction? = when (function) {
                kotlinTo -> host.inContextOfModelMember(kotlinTo) {
                    val typeA = host.modelTypeRef(kotlinTo.parameters[0].type)
                    val typeB = host.modelTypeRef(kotlinTo.parameters[1].type)
                    val returnType = host.modelTypeRef(kotlinTo.returnType)

                    DefaultDataTopLevelFunction(
                        packageName = "kotlin",
                        /** use the [org.gradle.internal.declarativedsl.intrinsics.to] intrinsic bridge as the runtime invocation target */
                        ownerJvmTypeName = gradleRuntimeIntrinsicsKClass.java.name,
                        simpleName = "to",
                        listOf(
                            DefaultDataParameter("first", typeA, false, ParameterSemanticsInternal.DefaultIdentityKey(null)), // import the receiver parameter as a data parameter
                            DefaultDataParameter("second", typeB, false, ParameterSemanticsInternal.DefaultUnknown)
                        ),
                        FunctionSemanticsInternal.DefaultPure(returnType)
                    )
                }

                kotlinCollectionsMapOf -> host.inContextOfModelMember(kotlinCollectionsMapOf) {
                    val returnType = host.modelTypeRef(kotlinCollectionsMapOf.returnType)

                    DefaultDataTopLevelFunction(
                        packageName = "kotlin.collections",
                        /** We use the [org.gradle.internal.declarativedsl.intrinsics.mapOf] bridge as the runtime invocation target. */
                        ownerJvmTypeName = gradleRuntimeIntrinsicsKClass.java.name,
                        simpleName = "mapOf",
                        listOf(
                            DefaultVarargParameter("pairs", kotlinCollectionsMapOf.parameters.last().parameterTypeToRefOrError(host), isDefault = false, ParameterSemanticsInternal.DefaultUnknown)
                        ),
                        FunctionSemanticsInternal.DefaultPure(returnType)
                    )
                }

                else -> null
            }
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
                val mapSignatureFunction: KFunction<Map<Any, Any>> = ::mapSignature
                val listSignatureFunction: KFunction<List<Any>> = ::listSignature

                return mapOf(
                    DefaultFqName.parse(List::class.qualifiedName!!) to listOf(
                        host.inContextOfModelMember(listSignatureFunction) {
                            val listOfTType = host.modelTypeRef(listSignatureFunction.parameters.last().type)

                            DefaultAssignmentAugmentation(
                                AssignmentAugmentationKindInternal.DefaultPlus,
                                DefaultDataTopLevelFunction(
                                    AUGMENTATION_FUNCTION_PACKAGE,
                                    ::builtinListAugmentation.javaMethod!!.declaringClass.name,
                                    LIST_AUGMENTATION_FUNCTION_NAME,
                                    listOf(
                                        DefaultDataParameter("lhs", listOfTType, false, ParameterSemanticsInternal.DefaultUnknown),
                                        DefaultDataParameter("rhs", listOfTType, false, ParameterSemanticsInternal.DefaultUnknown)
                                    ),
                                    FunctionSemanticsInternal.DefaultPure(listOfTType)
                                ),
                            )
                        }),

                    DefaultFqName.parse(Map::class.qualifiedName!!) to listOf(
                        host.inContextOfModelMember(mapSignatureFunction) {
                            val mapOfKvType = host.modelTypeRef(mapSignatureFunction.parameters.last().type)

                            DefaultAssignmentAugmentation(
                                AssignmentAugmentationKindInternal.DefaultPlus,
                                DefaultDataTopLevelFunction(
                                    AUGMENTATION_FUNCTION_PACKAGE,
                                    ::builtinMapAugmentation.javaMethod!!.declaringClass.name,
                                    MAP_AUGMENTATION_FUNCTION_NAME,
                                    listOf(
                                        DefaultDataParameter("lhs", mapOfKvType, false, ParameterSemanticsInternal.DefaultUnknown),
                                        DefaultDataParameter("rhs", mapOfKvType, false, ParameterSemanticsInternal.DefaultUnknown),
                                    ),
                                    FunctionSemanticsInternal.DefaultPure(mapOfKvType)
                                )
                            )
                        }
                    )
                )
            }
        })

    /** Declared just for getting the List<T> type reflection */
    private fun <T> listSignature(param: List<T>) = param

    /** Declared just for getting the List<T> type reflection */
    private fun <K, V> mapSignature(param: Map<K, V>) = param

    private val AUGMENTATION_FUNCTION_PACKAGE = ::builtinListAugmentation.javaMethod!!.declaringClass.`package`.name
    private val LIST_AUGMENTATION_FUNCTION_NAME = ::builtinListAugmentation.name
    private val MAP_AUGMENTATION_FUNCTION_NAME = ::builtinMapAugmentation.name
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

fun builtinMapAugmentation(lhs: Any, rhs: Map<*, *>): Any =
    when (lhs) {
        is Map<*, *> -> lhs + rhs
        is MapProperty<*, *> -> {
            Cast.uncheckedNonnullCast<MapProperty<Any, Any>>(lhs).putAll(Cast.uncheckedNonnullCast<Map<Any, Any>>(rhs))
            skipSetterSpecialValue
        }
        else -> error("Unexpected augmented map property value: $lhs")
    }
