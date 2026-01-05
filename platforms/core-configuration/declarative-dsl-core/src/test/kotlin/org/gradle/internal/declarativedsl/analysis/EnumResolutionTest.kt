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

import org.gradle.declarative.dsl.model.annotations.Adding
import org.gradle.declarative.dsl.model.annotations.HiddenInDefinition
import org.gradle.internal.declarativedsl.demo.resolve
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.junit.Assert
import org.junit.Test

class EnumResolutionTest {
    @Test
    fun `fails to resolve a non-existent enum entry`() {
        val result = schema.resolve("enum = QUX")
        Assert.assertEquals(2, result.errors.size)
        Assert.assertTrue(result.errors.any { it.errorReason is ErrorReason.UnresolvedAssignmentRhs })
        Assert.assertTrue(result.errors.any { it.errorReason is ErrorReason.UnresolvedReference })
    }

    @Test
    fun `resolves unqualified references to enums in a property position`() {
        val result = schema.resolve("enum = BAR")
        Assert.assertTrue(result.assignments.single().rhs is ObjectOrigin.EnumConstantOrigin)
    }

    @Test
    fun `resolves unqualified references to enums in a generic factory argument position`() {
        val result = schema.resolve("boxOfEnum = boxOf(BAR)")
        val rhs = result.assignments.single().rhs
        Assert.assertTrue(rhs is ObjectOrigin.FunctionInvocationOrigin)
        (rhs as ObjectOrigin.FunctionInvocationOrigin).parameterBindings.bindingMap.values.single().let {
            Assert.assertTrue(it.objectOrigin is ObjectOrigin.EnumConstantOrigin)
        }
    }

    @Test
    fun `resolves unqualified references to enums in a model function argument position`() {
        val result = schema.resolve("addEnum(BAZ)")
        val rhs = result.additions.single().dataObject
        Assert.assertTrue(rhs is ObjectOrigin.FunctionInvocationOrigin)
        (rhs as ObjectOrigin.FunctionInvocationOrigin).parameterBindings.bindingMap.values.single().let {
            Assert.assertTrue(it.objectOrigin is ObjectOrigin.EnumConstantOrigin)
        }
    }

    @Test
    fun `resolves unqualified references to enums in a generic function argument position`() {
        val result = schema.resolve("addBoxOfEnum(boxOf(FOO))")
        val rhs = result.additions.single().dataObject
        Assert.assertTrue(rhs is ObjectOrigin.FunctionInvocationOrigin)
        (rhs as ObjectOrigin.FunctionInvocationOrigin).parameterBindings.bindingMap.values.single().let {
            Assert.assertTrue(it.objectOrigin is ObjectOrigin.FunctionInvocationOrigin)
        }
    }

    @Test
    fun `resolves unqualified references to enums in a transitively generic vararg function call position`() {
        val result = schema.resolve("myListOfMyListOfEnum = myListOf(myListOf(FOO), myListOf(BAR, BAZ))")
        val rhs = result.assignments.single().rhs
        Assert.assertTrue(rhs is ObjectOrigin.FunctionInvocationOrigin)
        (rhs as ObjectOrigin.FunctionInvocationOrigin).parameterBindings.bindingMap.values.single().let {
            val arg = it.objectOrigin
            Assert.assertTrue(arg is ObjectOrigin.GroupedVarargValue)
            Assert.assertTrue((arg as ObjectOrigin.GroupedVarargValue).elementValues.all { argElement ->
                ((argElement as ObjectOrigin.FunctionInvocationOrigin).parameterBindings.bindingMap.values.single().objectOrigin as ObjectOrigin.GroupedVarargValue).elementValues.all { varargValue ->
                    varargValue is ObjectOrigin.EnumConstantOrigin
                }
            })
        }
    }

    val schema = schemaFromTypes(Receiver::class, listOf(Receiver::class, Enum::class, Box::class))

    class Receiver {
        var enum: Enum = Enum.FOO

        @Suppress("unused")
        var boxOfEnum: Box<Enum> = Box(Enum.FOO)

        @Suppress("unused")
        var myListOfMyListOfEnum: MyList<MyList<Enum>> = MyList(listOf())

        @Adding
        fun addEnum(enum: Enum) {
            println(enum)
        }

        @Suppress("unused")
        @Adding
        fun addBoxOfEnum(boxOfEnum: Box<Enum>) {
            println(boxOfEnum)
        }

        fun <T> boxOf(t: T) = Box(t)

        fun <T> myListOf(vararg t: T) = MyList(listOf(*t))
    }

    class Box<T>(@get:HiddenInDefinition val t: T)
    class MyList<T>(val items: List<T>)

    enum class Enum {
        FOO, BAR, BAZ
    }
}
