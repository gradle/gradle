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

package gradlebuild.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.rules.KotlinCoreEnvironmentTest
import io.gitlab.arturbosch.detekt.test.compileAndLintWithContext
import io.gitlab.arturbosch.detekt.test.lint
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@KotlinCoreEnvironmentTest
internal class MyRuleTest(private val env: KotlinCoreEnvironment) {

    @Test
    fun `reports inner classes`() {
        val code = """
        class A {
          inner class B
        }
        """
        val findings = MyRule(Config.empty).compileAndLintWithContext(env, code)
        assertEquals(1, findings.size)
    }

    @Test
    fun `doesn't report inner classes`() {
        val code = """
        class A {
          class B
        }
        """
        val findings = MyRule(Config.empty).compileAndLintWithContext(env, code)
        assertEquals(0, findings.size)
    }

    @Test
    fun `build script`() {
        val code = """
        someBlock {
            someProperty = 7
        }

        dependencies {
            implementation(project(":lib"))

            testImplementation("junit:junit:4.13")
            testImplementation(testFixtures(project(":lib")))
        }
        """

        val findings = MyRule(Config.empty).lint(code)
        assertEquals(0, findings.size)
    }
}
