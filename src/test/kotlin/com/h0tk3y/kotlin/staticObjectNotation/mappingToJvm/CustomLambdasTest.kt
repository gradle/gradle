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

package com.example.com.h0tk3y.kotlin.staticObjectNotation.mappingToJvm

import com.example.com.h0tk3y.kotlin.staticObjectNotation.demo.reflection.reflect
import com.h0tk3y.kotlin.staticObjectNotation.Adding
import com.h0tk3y.kotlin.staticObjectNotation.Configuring
import com.h0tk3y.kotlin.staticObjectNotation.Restricted
import com.h0tk3y.kotlin.staticObjectNotation.mappingToJvm.RestrictedReflectionToObjectConverter
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.plus
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.schemaFromTypes
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.treatInterfaceAsConfigureLambda
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

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

    private fun applyToOuter(code: String): Outer {
        val reflection = schema.reflect(code)

        val outer = Outer()
        val converter = RestrictedReflectionToObjectConverter(emptyMap(), outer, functionalLambdaHandler)
        converter.apply(reflection)

        return outer
    }
}

class Outer {
    @Restricted
    val inner: Inner = Inner()

    @Configuring("inner")
    fun configureInner(fn: Functional) {
        fn.configure(inner)
    }
    @Configuring("inner")
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
    @Restricted
    var x = 0

    @Adding
    fun f() {
        ++x
    }
}

private val functionalLambdaHandler =
    treatInterfaceAsConfigureLambda(Functional::class)
        .plus(treatInterfaceAsConfigureLambda(GenericFunctional::class))

private val schema = schemaFromTypes(
    Outer::class, listOf(
        Outer::class,
        Inner::class,
        Functional::class,
        GenericFunctional::class
    ), emptyList(), emptyMap(), emptyList(), functionalLambdaHandler
)
