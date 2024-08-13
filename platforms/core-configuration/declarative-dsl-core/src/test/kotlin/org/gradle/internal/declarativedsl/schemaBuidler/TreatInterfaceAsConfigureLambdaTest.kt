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

package org.gradle.internal.declarativedsl.schemaBuidler

import org.gradle.internal.declarativedsl.schemaBuilder.treatInterfaceAsConfigureLambda
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import kotlin.reflect.typeOf


class TreatInterfaceAsConfigureLambdaTest {

    private
    interface MyFunctionalInterface<T> {
        fun configure(t: T)
    }

    private
    val customConfigureLambdas =
        treatInterfaceAsConfigureLambda(MyFunctionalInterface::class)

    @Test
    fun recognizesLambdaType() {
        assertEquals(typeOf<Int>(), customConfigureLambdas.getTypeConfiguredByLambda(typeOf<MyFunctionalInterface<Int>>()))
        assertTrue { customConfigureLambdas.isConfigureLambdaForType(typeOf<Int>(), typeOf<MyFunctionalInterface<Int>>()) }
        assertFalse { customConfigureLambdas.isConfigureLambdaForType(typeOf<String>(), typeOf<MyFunctionalInterface<Int>>()) }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `value captor captures the argument`() {
        fun f(fn: MyFunctionalInterface<String>) {
            fn.configure("test")
        }

        val valueCaptor1 = customConfigureLambdas.produceValueCaptor(typeOf<MyFunctionalInterface<Int>>())
        f(valueCaptor1.lambda as MyFunctionalInterface<String>)
        assertEquals("test", valueCaptor1.value)

        val valueCaptor2 = customConfigureLambdas.produceValueCaptor(typeOf<MyFunctionalInterface<*>>())
        f(valueCaptor2.lambda as MyFunctionalInterface<String>)
        assertEquals("test", valueCaptor2.value)

        assertThrows<IllegalArgumentException> {
            customConfigureLambdas.produceValueCaptor(typeOf<Runnable>())
        }
    }
}
