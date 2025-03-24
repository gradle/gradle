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

package org.gradle.internal.declarativedsl

import org.gradle.declarative.dsl.schema.AssignmentAugmentation
import org.gradle.declarative.dsl.schema.FqName
import org.gradle.internal.declarativedsl.analysis.AssignmentAugmentationKindInternal
import org.gradle.internal.declarativedsl.analysis.DefaultAssignmentAugmentation
import org.gradle.internal.declarativedsl.analysis.DefaultDataParameter
import org.gradle.internal.declarativedsl.analysis.DefaultDataTopLevelFunction
import org.gradle.internal.declarativedsl.analysis.DefaultFqName
import org.gradle.internal.declarativedsl.analysis.FunctionSemanticsInternal
import org.gradle.internal.declarativedsl.analysis.ParameterSemanticsInternal
import org.gradle.internal.declarativedsl.schemaBuilder.AugmentationsProvider
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaBuildingHost
import org.gradle.internal.declarativedsl.schemaBuilder.inContextOfModelMember
import kotlin.reflect.full.functions

fun fakeListAugmentationProvider(): AugmentationsProvider =
    object : AugmentationsProvider {
        override fun augmentations(host: SchemaBuildingHost): Map<FqName, List<AssignmentAugmentation>> {
            val listSignatureFunction = this::class.functions.single { it.name == "listSignature" }
            return host.inContextOfModelMember(listSignatureFunction) {
                val listOfTType = host.modelTypeRef(listSignatureFunction.parameters.last().type)
                mapOf(
                    DefaultFqName.parse(List::class.qualifiedName!!) to listOf(
                        DefaultAssignmentAugmentation(
                            AssignmentAugmentationKindInternal.DefaultPlus,
                            DefaultDataTopLevelFunction(
                                "com.example.test.Augmentation",
                                "com.example.test.Augmentation.OwnerJvmType",
                                "fakeListAugmentation",
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

        @Suppress("unused") // declared just for getting the List<T> type reflection
        private fun <T> listSignature(param: List<T>) = param
    }
