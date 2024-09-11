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

package org.gradle.internal.declarativedsl.mappingToJvm

import org.gradle.declarative.dsl.model.annotations.Adding
import org.gradle.declarative.dsl.model.annotations.Configuring
import org.gradle.declarative.dsl.model.annotations.Restricted
import org.gradle.internal.declarativedsl.demo.reflection.reflect
import org.gradle.internal.declarativedsl.schemaBuilder.plus
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.gradle.internal.declarativedsl.schemaBuilder.treatInterfaceAsConfigureLambda
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.Test


class CustomLambdasTest {
    @Test
    fun `custom lambda interface with no generic`() {
        val o = applyToOuter(
            """
            configureInner {
                x = 2
                f()
                f()
            }
            """.trimIndent()
        )

        assertEquals(4, o.inner.x)
    }

    @Test
    fun `custom lambda interface with a generic`() {
        val o = applyToOuter(
            """
            configureInnerWithGeneric {
                x = 123
                f()
            }
            """.trimIndent()
        )

        assertEquals(124, o.inner.x)
    }

    private
    fun applyToOuter(code: String): Outer {
        val reflection = schema.reflect(code)

        val outer = Outer()
        val converter = DeclarativeReflectionToObjectConverter(
            emptyMap(), outer, MemberFunctionResolver(functionalLambdaHandler), ReflectionRuntimePropertyResolver, RuntimeCustomAccessors.none
        )
        converter.apply(reflection)

        return outer
    }
}


class Outer {
    @get:Restricted
    val inner: Inner = Inner()

    @Configuring(propertyName = "inner")
    fun configureInner(fn: Functional) {
        fn.configure(inner)
    }

    @Configuring(propertyName = "inner")
    fun configureInnerWithGeneric(fn: GenericFunctional<Inner>) {
        fn.configure(inner)
    }
}


interface Functional {
    fun configure(inner: Inner)
}


interface GenericFunctional<T> {
    fun configure(something: T)
}


class Inner {
    @get:Restricted
    var x = 0

    @Adding
    fun f() {
        ++x
    }
}


private
val functionalLambdaHandler =
    treatInterfaceAsConfigureLambda(Functional::class)
        .plus(treatInterfaceAsConfigureLambda(GenericFunctional::class))


private
val schema = schemaFromTypes(
    Outer::class, listOf(
        Outer::class,
        Inner::class,
        Functional::class,
        GenericFunctional::class
    ), emptyList(), emptyMap(), emptyList(),
    configureLambdas = functionalLambdaHandler
)
