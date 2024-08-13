/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.declarative.dsl.model.annotations.Adding
import org.gradle.declarative.dsl.model.annotations.Restricted
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.internal.declarativedsl.demo.resolve
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.Test
import org.gradle.internal.declarativedsl.assertIs


class SubtypingTest {
    val schema = schemaFromTypes(
        TopLevelForSubtyping::class,
        listOf(
            TopLevelForSubtyping::class, SuperClass::class, SuperInterface::class, Subtype::class, NotASubtype::class
        )
    )

    @Test
    fun `type-checks assignment of subtype to superclass type`() {
        val result = schema.resolve(
            """
            superClassProp = sub()
            """.trimIndent()
        )

        assertEquals(1, result.assignments.size)
    }

    @Test
    fun `type-checks assignment of subtype to super interface type`() {
        val result = schema.resolve(
            """
            superInterfaceProp = sub()
            """.trimIndent()
        )

        assertEquals(1, result.assignments.size)
    }

    @Test
    fun `type-checks argument of subtype passed as superclass parameter`() {
        val result = schema.resolve(
            """
            addSuperClass(sub())
            """.trimIndent()
        )

        assertEquals(1, result.additions.size)
    }

    @Test
    fun `type-checks argument of subtype passed as superinterface parameter`() {
        val result = schema.resolve(
            """
            addSuperInterface(sub())
            """.trimIndent()
        )

        assertEquals(1, result.additions.size)
    }

    @Test
    fun `rejects assignments of a type that is not a subtype`() {
        val result = schema.resolve(
            """
            superInterfaceProp = notASub()
            superClassProp = notASub()
            """.trimIndent()
        )

        assertEquals(0, result.assignments.size)
        assertEquals(2, result.errors.size)

        result.errors.forEach { error ->
            assertNotNull(error)
            val reason = error.errorReason
            assertIs<ErrorReason.AssignmentTypeMismatch>(reason)
            assertEquals(NotASubtype::class.simpleName, (((reason.actual.ref as DataTypeRef.Type).dataType as DataClass).name as DefaultFqName).simpleName)
        }
    }

    @Test
    fun `rejects function calls because of type mismatches`() {
        val result = schema.resolve(
            """
            addSuperInterface(notASub())
            addSuperClass(notASub())
            """.trimIndent()
        )

        assertEquals(2, result.errors.size)
        assertTrue { result.errors.all { it.errorReason is ErrorReason.UnresolvedFunctionCallSignature } }
    }
}


private
abstract class TopLevelForSubtyping {
    @get:Restricted
    abstract var superClassProp: SuperClass

    @get:Restricted
    abstract var superInterfaceProp: SuperInterface

    @Adding
    abstract fun addSuperClass(superclass: SuperClass)

    @Adding
    abstract fun addSuperInterface(superclass: SuperInterface)

    @Restricted
    abstract fun sub(): Subtype

    @Restricted
    abstract fun notASub(): NotASubtype
}


private
abstract class SuperClass


private
interface SuperInterface


private
class Subtype : SuperClass(), SuperInterface


private
class NotASubtype
