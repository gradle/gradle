/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.junit.Test


class KotlinDslContainerIntegrationTest : AbstractKotlinIntegrationTest() {
    @Test
    fun `TaskContainerScope String#invoke() extension is deprecated in Kotlin DSL`() {
        withBuildScript("""
            tasks {
                "help"()
            }
        """)

        executer.expectDocumentedDeprecationWarning(
            "Task 'help' found by String.invoke() notation. This behavior has been deprecated. " +
                "This behavior is scheduled to be removed in Gradle 9.0. " +
                "The \"name\"() notation can cause confusion with methods provided by Kotlin or the JDK. Use named(String) instead. " +
                "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#string_invoke")
        build("help")
    }

    @Test
    fun `NamedDomainObjectContainerScope String#invoke() extension is deprecated in Kotlin DSL`() {
        withBuildScript("""
            configurations {
                register("foo")
                "foo"()
            }
        """)

        executer.expectDocumentedDeprecationWarning(
            "Domain object 'foo' found by String.invoke() notation. This behavior has been deprecated. " +
                "This behavior is scheduled to be removed in Gradle 9.0. " +
                "The \"name\"() notation can cause confusion with methods provided by Kotlin or the JDK. Use named(String) instead. " +
                "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#string_invoke")
        build("help")
    }
}
