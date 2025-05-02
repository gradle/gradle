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

package org.gradle.internal.declarativedsl.analysis

import org.gradle.declarative.dsl.model.annotations.Restricted
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.internal.declarativedsl.assertIs
import org.gradle.internal.declarativedsl.demo.resolve
import org.gradle.internal.declarativedsl.language.DataTypeInternal
import org.gradle.internal.declarativedsl.schemaBuilder.CollectedPropertyInformation
import org.gradle.internal.declarativedsl.schemaBuilder.DefaultPropertyExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.PropertyExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaBuildingHost
import org.gradle.internal.declarativedsl.schemaBuilder.inContextOfModelClass
import org.gradle.internal.declarativedsl.schemaBuilder.plus
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf


class PropertyTest {
    @Test
    fun `read-only property cannot be written`() {
        val result = schema().resolve("x = y")
        val singleError = result.errors.single().errorReason
        assertIs<ErrorReason.ReadOnlyPropertyAssignment>(singleError)
        assertEquals("x", singleError.property.name)
    }

    @Test
    fun `write-only property cannot be read`() {
        val result = schema().resolve("y = z")
        assertEquals(2, result.errors.size)
        assertTrue { result.errors.any { it.errorReason is ErrorReason.UnresolvedAssignmentRhs } }
        val nonReadablePropertyError = result.errors.mapNotNull { it.errorReason as? ErrorReason.NonReadableProperty }.single()
        assertEquals("z", nonReadablePropertyError.property.name)
    }

    @Test
    fun `if multiple property extractors have properties with the same name, first wins`() {
        val expectedName = "test"
        val schema = schemaFromTypes(
            MyReceiver::class,
            listOf(MyReceiver::class),
            propertyExtractor = testPropertyContributor(expectedName, typeOf<Int>()) + testPropertyContributor(expectedName, typeOf<String>())
        )

        val property = (schema.dataClassTypesByFqName[DefaultFqName.parse(MyReceiver::class.qualifiedName!!)]!! as DataClass).properties.single()
        assertEquals(expectedName, property.name)
        assertEquals(DataTypeInternal.DefaultIntDataType.ref, property.valueType)
    }

    @Suppress("unused")
    private
    interface MyReceiver {
        @get:Restricted
        val x: Int

        @get:Restricted
        var y: Int
    }

    // don't make this private, will produce failures on Java 8 (due to https://youtrack.jetbrains.com/issue/KT-37660)
    val writeOnlyPropertyContributor = object : PropertyExtractor {
        override fun extractProperties(host: SchemaBuildingHost, kClass: KClass<*>, propertyNamePredicate: (String) -> Boolean): Iterable<CollectedPropertyInformation> {
            return if (kClass == MyReceiver::class) {
                listOf(
                    CollectedPropertyInformation(
                        "z",
                        typeOf<Int>(),
                        DataTypeInternal.DefaultIntDataType.ref,
                        DefaultDataProperty.DefaultPropertyMode.DefaultWriteOnly,
                        hasDefaultValue = false,
                        isHiddenInDeclarativeDsl = false,
                        isDirectAccessOnly = false,
                        claimedFunctions = emptyList()
                    )
                )
            } else emptyList()
        }
    }

    private
    fun testPropertyContributor(name: String, type: KType) = object : PropertyExtractor {
        override fun extractProperties(host: SchemaBuildingHost, kClass: KClass<*>, propertyNamePredicate: (String) -> Boolean): Iterable<CollectedPropertyInformation> =
            host.inContextOfModelClass(kClass) {
                val returnType = host.modelTypeRef(type)
                listOf(CollectedPropertyInformation(name, type, returnType, DefaultDataProperty.DefaultPropertyMode.DefaultReadWrite, false, false, false, emptyList()))
                    .filter { propertyNamePredicate(it.name) }
            }
    }

    private
    fun schema() = schemaFromTypes(
        MyReceiver::class,
        PropertyTest::class.nestedClasses,
        propertyExtractor = DefaultPropertyExtractor() + writeOnlyPropertyContributor
    )
}
