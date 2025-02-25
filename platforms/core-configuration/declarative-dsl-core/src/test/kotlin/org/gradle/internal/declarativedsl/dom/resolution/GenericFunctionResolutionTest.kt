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

import org.gradle.declarative.dsl.model.annotations.Restricted
import org.gradle.internal.declarativedsl.analysis.ErrorReason
import org.gradle.internal.declarativedsl.analysis.ErrorReason.UnresolvedAssignmentRhs
import org.gradle.internal.declarativedsl.analysis.ErrorReason.UnresolvedFunctionCallSignature
import org.gradle.internal.declarativedsl.analysis.ObjectOrigin
import org.gradle.internal.declarativedsl.assertIs
import org.gradle.internal.declarativedsl.demo.resolve
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenericFunctionResolutionTest {
    @Test
    fun `matches a function call typed with substitution against a property typed with substitution`() {
        resolvesAssignmentRhsToFunctionCall("boxOfString = boxOfString()")
    }

    @Test
    fun `matches a function call typed with type parameter against a property typed with substitution`() {
        resolvesAssignmentRhsToFunctionCall("boxOfString = boxOfT()")
    }

    @Test
    fun `rejects a function call with mismatching type arguments`() {
        failsToResolveWithReasons("boxOfString = boxOfInt()")
            .run { assertIs<ErrorReason.AssignmentTypeMismatch>(single()) }
    }

    @Test
    fun `matches a function call with type parameter and an argument against a property`() {
        resolvesAssignmentRhsToFunctionCall("boxOfString = tToBoxOfT(\"abc\")")
    }

    @Test
    fun `rejects a function call type argument violating the type substitution`() {
        failsToResolveWithReasons("boxOfString = tToBoxOfT(123)")
            .let { reasons ->
                assertEquals(2, reasons.size)
                assertTrue(reasons.any { it is UnresolvedAssignmentRhs })
                assertTrue(reasons.any { (it as? UnresolvedFunctionCallSignature)?.functionCall?.name == "tToBoxOfT" })
            }
    }


    @Test
    fun `matches a function call with nested generics and concrete return type`() {
        resolvesAssignmentRhsToFunctionCall("boxOfBoxOfString = boxOfBoxOfString()")
    }

    @Test
    fun `matches a function call with nested generics and generic return type`() {
        resolvesAssignmentRhsToFunctionCall("boxOfBoxOfString = tToBoxOfBoxOfT(\"test\")")
    }

    @Test
    fun `matches a function call with nested generics and expected argument type`() {
        resolvesAssignmentRhsToFunctionCall("boxOfBoxOfString = tToBoxOfT(tToBoxOfT(\"test\"))")
    }

    @Test
    fun `with an invariant type, rejects a function call with with subtype used in expected supertype position`() {
        assertIs<ErrorReason.AssignmentTypeMismatch>(
            failsToResolveWithReasons("boxOfSuper = boxOfSub()").single()
        )
    }

    @Test
    fun `with an out-projected type, accepts a function call with with subtype used in expected supertype position`() {
        resolvesAssignmentRhsToFunctionCall("boxOutOfSuper = boxOutOfSuper()")
        resolvesAssignmentRhsToFunctionCall("boxOutOfSuper = boxOutOfSub()")
    }

    @Test
    fun `with an out-projected type, accepts a function call with with nested generic of subtype in expected supertype position`() {
        resolvesAssignmentRhsToFunctionCall("boxOutOfBoxOutOfSuper = tToBoxOutOfT(boxOutOfSub())")
        resolvesAssignmentRhsToFunctionCall("boxOutOfBoxOutOfSuper = tToBoxOutOfT(tToBoxOutOfT(sub()))")
    }

    @Test
    fun `rejects a function call with nested generics and a wrong nested generic return type`() {
        failsToResolveWithReasons("boxOfBoxOfString = tToBoxOfT(tToBoxOfT(123))")
            .let { reasons ->
                assertEquals(4, reasons.size)
                assertEquals(1, reasons.count { it is UnresolvedAssignmentRhs })
                assertEquals(2, reasons.count { (it as? UnresolvedFunctionCallSignature)?.functionCall?.name == "tToBoxOfT" })
                assertEquals(1, reasons.count { (it as? ErrorReason.UnresolvedFunctionCallArguments)?.functionCall?.name == "tToBoxOfT" })
            }
    }

    @Test
    fun `rejects a function call with nested generics and a wrong concrete return type`() {
        failsToResolveWithReasons("boxOfBoxOfString = boxOfBoxOfInt()")
            .run { assertIs<ErrorReason.AssignmentTypeMismatch>(single()) }
    }

    @Test
    fun `rejects a function call with nested generics and a wrong generic return type`() {
        failsToResolveWithReasons("boxOfBoxOfString = tToBoxOfBoxOfT(123)")
            .let { reasons ->
                assertEquals(2, reasons.size)
                assertTrue(reasons.any { it is UnresolvedAssignmentRhs })
                assertTrue(reasons.any { (it as? UnresolvedFunctionCallSignature)?.functionCall?.name == "tToBoxOfBoxOfT" })
            }
    }

    @Test
    fun `matches a pair type with both types substituted with the expected type`() {
        resolvesAssignmentRhsToFunctionCall("pairOfStringAndInt = pairOfKAndV(\"one\", 2)")
    }

    @Test
    fun `rejects a pair type with one of the components not matching the expected type's component`() {
        listOf(
            "pairOfStringAndInt = pairOfKAndV(1, 2)",
            "pairOfStringAndInt = pairOfKAndV(\"one\", \"two\")"
        ).forEach {
            failsToResolveWithReasons(it)
                .let { reasons ->
                    assertEquals(2, reasons.size)
                    assertTrue(reasons.any { it is UnresolvedAssignmentRhs })
                    assertTrue(reasons.any { (it as? UnresolvedFunctionCallSignature)?.functionCall?.name == "pairOfKAndV" })
                }
        }
    }

    private fun resolvesAssignmentRhsToFunctionCall(code: String) {
        val resolution = schema.resolve(code)
        val rhs = resolution.assignments.single().rhs
        assertIs<ObjectOrigin.FunctionInvocationOrigin>(rhs)
    }

    private fun failsToResolveWithReasons(code: String): Set<ErrorReason> {
        val resolution = schema.resolve(code)
        assertTrue(resolution.assignments.isEmpty())
        return resolution.errors.map { it.errorReason }.toSet()
    }

    private val schema = schemaFromTypes(Receiver::class, listOf(Receiver::class, Box::class, Super::class, Sub::class))

    @Suppress("unused")
    interface Receiver {
        @Restricted
        fun <T> boxOfT(): Box<T>

        @Restricted
        fun <T> boxOfString(): Box<String>

        @Restricted
        fun <T> boxOfInt(): Box<Int>

        @Restricted
        fun <T> tToBoxOfT(t: T): Box<T>

        @Restricted
        fun <T> tToBoxOfBoxOfT(t: T): Box<Box<T>>

        @Restricted
        fun <T> boxOfBoxOfString(): Box<Box<String>>

        @Restricted
        fun <T> boxOfBoxOfInt(): Box<Box<Int>>

        @Restricted
        fun <K, V> pairOfKAndV(k: K, v: V): Pair<K, V>

        @Restricted
        fun boxOfSub(): Box<Sub>

        @Restricted
        fun boxOutOfSub(): BoxOut<Sub>

        @Restricted
        fun boxOutOfSuper(): BoxOut<Super>

        @Restricted
        fun <T> tToBoxOutOfT(t: T): BoxOut<T>

        @Restricted
        fun sub(): Sub

        @get:Restricted
        var boxOfString: Box<String>

        @get:Restricted
        var boxOfBoxOfString: Box<Box<String>>

        @get:Restricted
        var boxOfSuper: Box<Super>

        @get:Restricted
        var boxOutOfSuper: BoxOut<Super>

        @get:Restricted
        var boxOutOfBoxOutOfSuper: BoxOut<BoxOut<Super>>

        @get:Restricted
        var pairOfStringAndInt: Pair<String, Int>
    }

    interface Box<@Suppress("unused") T>
    interface BoxOut<@Suppress("unused") out T>

    interface Super
    interface Sub : Super
}
