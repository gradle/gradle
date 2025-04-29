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

package org.gradle.internal.declarativedsl.analysis

import org.gradle.declarative.dsl.model.annotations.Restricted
import org.gradle.declarative.dsl.schema.AssignmentAugmentation
import org.gradle.declarative.dsl.schema.FqName
import org.gradle.internal.declarativedsl.analysis.ErrorReason.AugmentingAssignmentNotResolved
import org.gradle.internal.declarativedsl.assertIs
import org.gradle.internal.declarativedsl.demo.resolve
import org.gradle.internal.declarativedsl.schemaBuilder.AugmentationsProvider
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaBuildingContextElement
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaBuildingHost
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.gradle.internal.declarativedsl.schemaBuilder.withTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.typeOf

class AugmentationResolutionTest {
    @Test
    fun `resolves augmenting assignment`() {
        val result = schema.resolve("data = newData(); data += newData()")
        assertEquals(2, result.assignments.size)
        val augmentation = result.assignments.last()
        val origin = augmentation.rhs as ObjectOrigin.AugmentationOrigin

        val operand = origin.augmentationOperand
        assertIs<ObjectOrigin.NewObjectFromMemberFunction>(operand)
        assertEquals("data", origin.augmentedProperty.property.name)

        val augmentationResult = origin.augmentationResult
        assertIs<ObjectOrigin.NewObjectFromTopLevelFunction>(augmentationResult)
        assertEquals("augment", augmentationResult.function.simpleName)
        assertEquals("com.example.TestJvm", (augmentationResult.function as DefaultDataTopLevelFunction).ownerJvmTypeName)
    }

    @Test
    fun `reports an unresolved augmentation for types that don't have one`() {
        val result = schema.resolve("data = newData(); str += \"123\"")
        assertTrue(result.assignments.none { it.lhs.property.name == "str" })
        assertEquals(2, result.errors.size)
        assertTrue(result.errors.any { it.errorReason is AugmentingAssignmentNotResolved && it.element.sourceData.text() == "str += \"123\"" })
        assertTrue(result.errors.any {
            val errorReason = it.errorReason
            errorReason is ErrorReason.UnresolvedFunctionCallSignature &&
                errorReason.functionCall.name == "+=" && // this asserts the current behavior, but the name might change in the future
                it.element.sourceData.text() == "str += \"123\""
        })
    }

    @Test
    fun `reports an unresolved augmentation for an incorrect operand type`() {
        val result = schema.resolve("data = newData(); data += 123")
        assertTrue(result.assignments.none { it.lhs.property.name == "str" })
        assertEquals(1, result.errors.size)
        // TODO: The current implementation checks the operand for being of the same type as the property.
        //  With more augmentation options like `list += item()` or `mutableList += listOf(...)`, the error is going to change.
        assertTrue(result.errors.any { it.errorReason is ErrorReason.AssignmentTypeMismatch && it.element.sourceData.text() == "data += 123" })
    }

    @Test
    fun `reports an unresolved augmentation for an erroneous expression`() {
        val result = schema.resolve("data = newData(); data += unresolved()")
        assertTrue(result.assignments.none { it.lhs.property.name == "str" })
        assertEquals(2, result.errors.size)
        assertTrue(result.errors.any { it.errorReason is ErrorReason.UnresolvedAssignmentRhs && it.element.sourceData.text() == "data += unresolved()" })
        assertTrue(result.errors.any { it.errorReason is ErrorReason.UnresolvedFunctionCallSignature && it.element.sourceData.text() == "unresolved()" })
    }

    private val schema = schemaFromTypes(TopLevelReceiver::class, listOf(TopLevelReceiver::class, Data::class), augmentationsProvider = object : AugmentationsProvider {
        override fun augmentations(host: SchemaBuildingHost): Map<FqName, List<AssignmentAugmentation>> =
            mapOf(DefaultFqName.parse(Data::class.qualifiedName!!) to listOf(DefaultAssignmentAugmentation(AssignmentAugmentationKindInternal.DefaultPlus, augmentationFunction(host))))

        private fun augmentationFunction(host: SchemaBuildingHost) =
            host.withTag(SchemaBuildingContextElement.TagContextElement("augmentation function")) {
                DefaultDataTopLevelFunction(
                    "com.example", "com.example.TestJvm", "augment", listOf(
                        DefaultDataParameter("left", host.modelTypeRef(typeOf<Data>()), false, ParameterSemanticsInternal.DefaultUnknown),
                        DefaultDataParameter("right", host.modelTypeRef(typeOf<Data>()), false, ParameterSemanticsInternal.DefaultUnknown)
                    ), FunctionSemanticsInternal.DefaultPure(host.modelTypeRef(typeOf<Data>()))
                )
            }
    })


    interface TopLevelReceiver {
        @get:Restricted
        var data: Data

        @Restricted
        fun newData(): Data

        @get:Restricted
        var str: String
    }

    interface Data
}
