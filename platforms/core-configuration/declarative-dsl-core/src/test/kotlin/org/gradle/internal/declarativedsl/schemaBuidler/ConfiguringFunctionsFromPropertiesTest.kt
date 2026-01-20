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

package org.gradle.internal.declarativedsl.schemaBuidler

import org.gradle.declarative.dsl.schema.ConfigureAccessor
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.FunctionSemantics
import org.gradle.internal.declarativedsl.analysis.DefaultFqName
import org.gradle.internal.declarativedsl.analysis.isReadOnly
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfiguringFunctionsFromPropertiesTest {
    @Test
    fun `can extract configuring functions from read-only Kotlin properties`() {
        val schema = schemaFromTypes(TopLevel::class, listOf(TopLevel::class, Nested::class))
        val topLevelClass = schema.dataClassTypesByFqName[DefaultFqName.parse(TopLevel::class.qualifiedName!!)] as DataClass
        val configuringFunction = topLevelClass.memberFunctions.single { it.simpleName == "nested" }
        val semantics = configuringFunction.semantics
        assertTrue(semantics is FunctionSemantics.AccessAndConfigure && semantics.accessor is ConfigureAccessor.Property)

        // Also still extracts a read-only property in case it is needed for nested value factory access
        val property = topLevelClass.properties.single { it.name == "nested" }
        assertTrue(property.isReadOnly)
    }

    @Test
    fun `does not extract functions from mutable Kotlin properties`() {
        val schema = schemaFromTypes(TopLevel::class, listOf(TopLevel::class, Nested::class))
        val topLevelClass = schema.dataClassTypesByFqName[DefaultFqName.parse(TopLevel::class.qualifiedName!!)] as DataClass
        assertTrue(topLevelClass.memberFunctions.none { it.simpleName == "mutableNested" })
    }

    interface TopLevel {
        val nested: Nested
        var mutableNested: Nested
    }

    interface Nested {
        var x: Int
        fun f(): Nested
    }
}
