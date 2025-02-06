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

package org.gradle.internal.declarativedsl.dom.resolution

import org.gradle.declarative.dsl.model.annotations.Adding
import org.gradle.declarative.dsl.model.annotations.Restricted
import org.gradle.declarative.dsl.schema.VarargParameter
import org.gradle.internal.declarativedsl.analysis.ErrorReason
import org.gradle.internal.declarativedsl.analysis.ObjectOrigin
import org.gradle.internal.declarativedsl.analysis.ResolutionResult
import org.gradle.internal.declarativedsl.assertIs
import org.gradle.internal.declarativedsl.demo.resolve
import org.gradle.internal.declarativedsl.language.FunctionCall
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.jupiter.api.fail

class VarargResolutionTest {
    @Test
    fun `resolves calls to vararg functions with concrete types`() {
        assertHasSingleVarargInvocationWithLiterals(schemaFromTypes.resolve("acceptsStringVarargs()"), listOf())
        assertHasSingleVarargInvocationWithLiterals(schemaFromTypes.resolve("acceptsStringVarargs(\"one\")"), listOf("one"))
        assertHasSingleVarargInvocationWithLiterals(schemaFromTypes.resolve("acceptsStringVarargs(\"one\", \"two\")"), listOf("one", "two"))

        assertHasSingleVarargInvocationWithLiterals(schemaFromTypes.resolve("acceptsIntThenStringVarargs(1)"), listOf())
        assertHasSingleVarargInvocationWithLiterals(schemaFromTypes.resolve("acceptsIntThenStringVarargs(1, \"one\", \"two\", \"three\")"), listOf("one", "two", "three"))
    }

    @Test
    fun `rejects a vararg call that has missing non-vararg arguments`() {
        assertUnresolvedFunctionSignature(schemaFromTypes.resolve("acceptsIntThenStringVarargs()"), "acceptsIntThenStringVarargs")
    }

    @Test
    fun `rejects vararg call with a type mismatch in basic types`() {
        assertUnresolvedFunctionSignature(schemaFromTypes.resolve("acceptsStringVarargs(\"one\", 2, 3)"), "acceptsStringVarargs")
    }

    @Test
    fun `rejects vararg call with a type mismatch in generic types`() {
        assertUnresolvedFunctionSignature(schemaFromTypes.resolve("acceptsListOfStringVarargs(myListOf(\"one\", 2))"), "acceptsListOfStringVarargs")
        assertUnresolvedFunctionSignature(schemaFromTypes.resolve("acceptsListOfStringVarargs(myListOf(\"one\"), myListOf(2))"), "acceptsListOfStringVarargs")
    }

    private fun assertHasSingleVarargInvocationWithLiterals(resolution: ResolutionResult, expectedValues: List<Any>) {
        val argument = varargObjectOriginFromCall(resolution)
        assertEquals(expectedValues, argument.elementValues.map { (it as ObjectOrigin.ConstantOrigin).literal.value })
    }

    @Test
    fun `resolves calls to vararg functions with generic types`() {
        assertEquals(2, varargObjectOriginFromCall(schemaFromTypes.resolve("acceptsListOfStringVarargs(myListOf(\"one\", \"two\"), myListOf())")).elementValues.size)

        assertHasSingleVarargInvocationWithLiterals(schemaFromTypes.resolve("listStringProperty = myListOf(\"one\", \"two\")"), listOf("one", "two"))
        assertHasSingleVarargInvocationWithLiterals(schemaFromTypes.resolve("listIntProperty = myListOf(1, 2, 3)"), listOf(1, 2, 3))
    }

    @Test
    fun `rejects as ambiguous calls resolving to multiple overloads where one is vararg`() {
        assertTrue(schemaFromTypes.resolve("listStringProperty = myListOf(ambiguous(\"test\"))")
            .errors.any { it.errorReason is ErrorReason.AmbiguousFunctions && (it.element as FunctionCall).name == "ambiguous"})
    }

    @Test
    fun `accepts a call to a vararg overload if there is enough argument type information to disambiguate`() {
        assertTrue(schemaFromTypes.resolve("listStringProperty = myListOf(ambiguous(\"test\", 1))").errors.isEmpty())
    }

    private fun varargObjectOriginFromCall(resolution: ResolutionResult): ObjectOrigin.GroupedVarargValue {
        val assigned = resolution.additions.singleOrNull()?.dataObject
            ?: resolution.assignments.singleOrNull()?.rhs
            ?: fail { "unexpected resolution results" }
        assertIs<ObjectOrigin.FunctionInvocationOrigin>(assigned)
        val argument = assigned.parameterBindings.bindingMap.entries.single { it.key is VarargParameter }.value.objectOrigin
        assertIs<ObjectOrigin.GroupedVarargValue>(argument)
        return argument
    }

    private fun assertUnresolvedFunctionSignature(resolution: ResolutionResult, functionName: String) {
        assertTrue(resolution.errors.any { it.errorReason is ErrorReason.UnresolvedFunctionCallSignature && (it.element as FunctionCall).name == functionName})
    }


    val schemaFromTypes = schemaFromTypes(Schema::class, listOf(Schema::class, List::class))

    private interface Schema {
        @Suppress("unused")
        @Adding
        fun acceptsStringVarargs(vararg strings: String): String

        @Suppress("unused")
        @Adding
        fun acceptsIntThenStringVarargs(int: Int, vararg strings: String): String

        @Suppress("unused")
        @Adding
        fun acceptsListOfStringVarargs(vararg strings: List<String>): String

        @Suppress("unused")
        @Restricted
        fun <T> myListOf(vararg items: T): List<T>

        @Suppress("unused")
        @Restricted
        fun ambiguous(vararg items: String): String

        @Suppress("unused")
        @Restricted
        fun ambiguous(string: String, vararg others: Int): String

        @Suppress("unused")
        @get:Restricted
        var listStringProperty: List<String>

        @Suppress("unused")
        @get:Restricted
        var listIntProperty: List<Int>
    }
}
