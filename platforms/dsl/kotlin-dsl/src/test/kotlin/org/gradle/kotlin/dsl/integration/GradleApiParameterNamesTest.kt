/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.PluginDependenciesSpecScope

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

import kotlin.reflect.KClass
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.valueParameters


class GradleApiParameterNamesTest {

    @Test
    fun `Kotlin delegation generated member has parameter names`() {

        assertHasParameterNames(
            PluginDependenciesSpecScope::class, "id", listOf(String::class), listOf("id")
        )
    }

    private
    fun assertHasParameterNames(type: KClass<*>, methodName: String, parameterTypes: List<KClass<*>>, parameterNames: List<String>) {

        // java.lang.reflect
        val javaMethod = type.java.getDeclaredMethod(
            methodName,
            *parameterTypes.map { it.java }.toTypedArray()
        )
        assertThat(
            "java.lang.reflect parameter names match",
            javaMethod.parameters.map { it.name },
            equalTo(parameterNames)
        )

        // kotlin.reflect
        val kotlinFunction = type.declaredFunctions.single {
            it.name == methodName && it.valueParameters.map { it.type.classifier } == parameterTypes
        }
        assertThat(
            "kotlin.reflect parameter names match",
            kotlinFunction.valueParameters.map { it.name!! },
            equalTo(parameterNames as List<String?>)
        )
    }
}
