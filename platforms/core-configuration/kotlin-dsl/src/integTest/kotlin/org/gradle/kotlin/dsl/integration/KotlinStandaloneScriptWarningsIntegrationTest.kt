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

package org.gradle.kotlin.dsl.integration

import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.kotlin.dsl.fixtures.clickableUrlFor
import org.junit.Before
import org.junit.Test
import java.io.File


class KotlinStandaloneScriptWarningsIntegrationTest : AbstractKotlinIntegrationTest() {

    @Before
    fun setup() {
        executer.beforeExecute { noDeprecationChecks() }
    }

    @Test
    fun `fails on warning in project script`() {
        withAllWarningsAsErrors()
        val script = withBuildScript(scriptContentWithWarning)
        buildAndFail("help").apply {
            assertHasWarningLineFor(script)
            assertHasDetailedErrorOutput()
        }
    }

    @Test
    fun `fails on warning in settings script`() {
        withAllWarningsAsErrors()
        val script = withSettings(scriptContentWithWarning)
        buildAndFail("help").apply {
            assertHasWarningLineFor(script)
            assertHasDetailedErrorOutput()
        }
    }

    @Test
    fun `fails on warning in initialization script`() {
        withAllWarningsAsErrors()
        val script = withFile("my-init.gradle.kts", scriptContentWithWarning)
        buildAndFail("help", "-I", script.name).apply {
            assertHasWarningLineFor(script)
            assertHasDetailedErrorOutput()
        }
    }

    @Test
    fun `fails on warning in applied script`() {
        withAllWarningsAsErrors()
        val script = withFile("my-script.gradle.kts", scriptContentWithWarning)
        withBuildScript("""apply(from = "${script.name}")""")
        buildAndFail("help").apply {
            assertHasWarningLineFor(script)
            assertHasDetailedErrorOutput()
        }
    }

    @Test
    fun `fails on warning after allWarningsOnError is enabled`() {

        val script = withBuildScript(scriptContentWithWarning)
        build("help").apply {
            assertHasWarningLineFor(script)
        }

        withAllWarningsAsErrors()
        buildAndFail("help").apply {
            assertHasWarningLineFor(script)
            assertHasDetailedErrorOutput()
        }
    }

    private
    val scriptContentWithWarning =
        """
        @Deprecated("BECAUSE")
        class SomeDeprecatedType
        SomeDeprecatedType()
        """.trimIndent()

    private
    fun withAllWarningsAsErrors() {
        withFile("gradle.properties", "org.gradle.kotlin.dsl.allWarningsAsErrors=true")
    }

    private
    fun ExecutionResult.assertHasWarningLineFor(script: File) =
        assertOutputContains(warningLineFor(script))

    private
    fun ExecutionFailure.assertHasWarningLineFor(script: File) =
        assertHasErrorOutput(warningLineFor(script))

    private
    fun warningLineFor(script: File) =
        "w: ${clickableUrlFor(script)}:3:1: 'constructor(): SomeDeprecatedType' is deprecated. BECAUSE"

    private
    fun ExecutionFailure.assertHasDetailedErrorOutput() =
        assertHasErrorOutput(
            """
            * What went wrong:
            Script compilation error:

              Line 3: SomeDeprecatedType()
                      ^ 'constructor(): SomeDeprecatedType' is deprecated. BECAUSE.

            1 error
            """.trimIndent()
        )
}
