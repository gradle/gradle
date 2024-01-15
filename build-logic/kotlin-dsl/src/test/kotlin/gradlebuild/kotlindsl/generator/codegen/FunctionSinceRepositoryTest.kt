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

package gradlebuild.kotlindsl.generator.codegen

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


class FunctionSinceRepositoryTest {

    @Test
    fun `can extract JavaFunction from single parameter signature string`() {
        assertThat(
            javaFunctionOf("org.gradle.SomeType.methodMan(java.lang.String)"),
            equalTo(
                JavaFunction(
                    "org.gradle.SomeType",
                    "methodMan",
                    listOf("java.lang.String"),
                    false
                )
            )
        )
    }

    @Test
    fun `can extract JavaFunction from single parameter with varargs signature string`() {
        assertThat(
            javaFunctionOf("org.gradle.SomeType.methodMan(java.lang.String[])"),
            equalTo(
                JavaFunction(
                    "org.gradle.SomeType",
                    "methodMan",
                    listOf("java.lang.String"),
                    true
                )
            )
        )
    }

    @Test
    fun `can extract JavaFunction from multiple parameters signature string`() {
        assertThat(
            javaFunctionOf("org.gradle.SomeType.methodMan(java.lang.String, java.util.List)"),
            equalTo(
                JavaFunction(
                    "org.gradle.SomeType",
                    "methodMan",
                    listOf("java.lang.String", "java.util.List"),
                    false
                )
            )
        )
    }

    @Test
    fun `can extract JavaFunction from multiple parameters with varargs signature string`() {
        assertThat(
            javaFunctionOf("org.gradle.SomeType.methodMan(java.lang.String, java.lang.Class[])"),
            equalTo(
                JavaFunction(
                    "org.gradle.SomeType",
                    "methodMan",
                    listOf("java.lang.String", "java.lang.Class"),
                    true
                )
            )
        )
    }
}
