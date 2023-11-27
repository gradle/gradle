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

package com.example.com.h0tk3y.kotlin.staticObjectNotation.schemaBuidler

import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.treatInterfaceAsConfigureLambda
import org.junit.jupiter.api.assertThrows
import java.lang.IllegalArgumentException
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TreatInterfaceAsConfigureLambdaTest {

    private interface MyFunctionalInterface<T> {
        fun configure(t: T): Unit
    }

    private val customConfigureLambdas =
        treatInterfaceAsConfigureLambda(MyFunctionalInterface::class)

    @Test
    fun recognizesLambdaType() {
        assertTrue { customConfigureLambdas.isConfigureLambda(typeOf<MyFunctionalInterface<Int>>()) }
        assertTrue { customConfigureLambdas.isConfigureLambdaForType(typeOf<Int>(), typeOf<MyFunctionalInterface<Int>>()) }
        assertFalse { customConfigureLambdas.isConfigureLambdaForType(typeOf<String>(), typeOf<MyFunctionalInterface<Int>>()) }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun producesNoopLambda() {
        fun f(fn: MyFunctionalInterface<String>) {
            fn.configure("test")
        }
        f(customConfigureLambdas.produceNoopConfigureLambda(typeOf<MyFunctionalInterface<Int>>()) as MyFunctionalInterface<String>)
        f(customConfigureLambdas.produceNoopConfigureLambda(typeOf<MyFunctionalInterface<*>>()) as MyFunctionalInterface<String>)

        assertThrows<IllegalArgumentException> {
            customConfigureLambdas.produceNoopConfigureLambda(typeOf<Runnable>())
        }
    }
}
