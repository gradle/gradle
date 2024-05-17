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

package org.gradle.kotlin.dsl.compile

import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.util.Matchers
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import java.util.regex.Pattern


class BuildOperationsAssertions(buildOperationsFixture: BuildOperationsFixture, val output: String, val expectWarnings: Boolean = false) {
    private
    val classpathCompileOperations = buildOperationsFixture.all(Pattern.compile("Compile script build.gradle.kts \\(CLASSPATH\\)"))

    private
    val bodyCompileOperations = buildOperationsFixture.all(Pattern.compile("Compile script build.gradle.kts \\(BODY\\)"))

    private
    val compileAvoidanceWarnings = output.lines()
        .filter { it.startsWith("Cannot use Kotlin build script compile avoidance with") }
        // filter out avoidance warnings for versioned jars - those come from Kotlin/libraries that don't change when code under test changes
        .filterNot { it.contains(Regex("\\d.jar: ")) }

    init {
        if (!expectWarnings) {
            MatcherAssert.assertThat(compileAvoidanceWarnings, Matchers.isEmpty())
        }
    }

    fun assertBuildScriptCompiled(): BuildOperationsAssertions {
        if (classpathCompileOperations.isNotEmpty() || bodyCompileOperations.isNotEmpty()) {
            return this
        }
        throw AssertionError("Expected script to be compiled, but it wasn't.")
    }

    fun assertBuildScriptBodyRecompiled(): BuildOperationsAssertions {
        if (bodyCompileOperations.size == 1) {
            return this
        }
        if (bodyCompileOperations.isEmpty()) {
            throw AssertionError("Expected build script body to be recompiled, but it wasn't.")
        }
        throw AssertionError("Expected build script body to be recompiled, but there was more than one body compile operation: $bodyCompileOperations")
    }

    fun assertBuildScriptCompilationAvoided(): BuildOperationsAssertions {
        if (classpathCompileOperations.isEmpty() && bodyCompileOperations.isEmpty()) {
            return this
        }
        throw AssertionError(
            "Expected script compilation to be avoided, but the buildscript was recompiled. " +
                "classpath compile operations: $classpathCompileOperations, body compile operations: $bodyCompileOperations"
        )
    }

    fun assertOutputContains(expectedOutput: String): BuildOperationsAssertions {
        MatcherAssert.assertThat(output, CoreMatchers.containsString(expectedOutput))
        return this
    }

    fun assertContainsCompileAvoidanceWarning(end: String): BuildOperationsAssertions {
        MatcherAssert.assertThat(compileAvoidanceWarnings, CoreMatchers.hasItem(CoreMatchers.endsWith(end)))
        return this
    }

    fun assertNumberOfCompileAvoidanceWarnings(n: Int): BuildOperationsAssertions {
        MatcherAssert.assertThat(compileAvoidanceWarnings, org.hamcrest.Matchers.hasSize(n))
        return this
    }
}
