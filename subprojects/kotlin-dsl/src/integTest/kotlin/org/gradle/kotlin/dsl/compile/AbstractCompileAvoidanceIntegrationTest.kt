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
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.Before
import java.io.File
import java.util.UUID


abstract class AbstractCompileAvoidanceIntegrationTest : AbstractKotlinIntegrationTest() {

    companion object CacheBuster {
        var cacheBuster = UUID.randomUUID()
    }

    @Before
    fun init() {
        cacheBuster = UUID.randomUUID()

        withSettings(
            """
            rootProject.name = "test-project"
            """
        )
    }

    protected
    fun givenJavaClassInBuildSrcContains(classBody: String, classAnnotations: String = ""): String =
        javaSourceFile("buildSrc", classBody, classAnnotations)

    protected
    fun givenKotlinClassInBuildSrcContains(classBody: String): String {
        withKotlinDslPluginInBuildSrc()
        return kotlinClassSourceFile("buildSrc", classBody)
    }

    protected
    fun givenKotlinScriptInBuildSrcContains(scriptName: String, scriptBody: String, scriptPrefix: String = ""): String {
        withKotlinDslPluginInBuildSrc()
        return kotlinScriptSourceFile("buildSrc", scriptName, scriptBody, scriptPrefix)
    }

    protected
    fun withKotlinDslPluginInBuildSrc() {
        // this is to force buildSrc/build.gradle.kts to be written to test-local buildscript cache
        // and not to be reused from daemon's cache from other tests when daemon is in use
        withBuildScriptIn("buildSrc", scriptWithKotlinDslPlugin())
            .bustScriptCache()
    }

    private
    fun File.bustScriptCache() {
        appendText(
            """
                val cacheBuster = "$cacheBuster"
            """
        )
    }

    protected
    fun javaSourceFile(baseDir: String, classBody: String, classAnnotations: String = ""): String {
        val className = "Foo"
        withFile(
            "$baseDir/src/main/java/com/example/$className.java",
            """
            package com.example;
            $classAnnotations
            public class $className {
                $classBody
            }
            """
        )
        return "com.example.$className"
    }

    private
    fun kotlinScriptSourceFile(baseDir: String, scriptName: String, scriptBody: String, scriptPrefix: String = ""): String {
        withFile(
            "$baseDir/src/main/kotlin/com/example/$scriptName.kt",
            """
            $scriptPrefix
            package com.example
            $scriptBody
            """
        )
        return "com.example"
    }

    protected
    fun kotlinClassSourceFile(baseDir: String, classBody: String): String {
        val className = "Foo"
        val packageName = kotlinScriptSourceFile(
            baseDir,
            className,
            """
            class $className {
                $classBody
            }
            """
        )
        return "$packageName.$className"
    }

    protected
    fun withUniqueScript(script: String) = withBuildScript(script).apply {
        bustScriptCache()
    }

    protected
    fun configureProject(vararg tasks: String): BuildOperationsAssertions {
        val buildOperations = BuildOperationsFixture(executer, testDirectoryProvider)
        val output = executer.withTasks(*tasks).run().normalizedOutput
        return BuildOperationsAssertions(buildOperations, output)
    }

    protected
    fun configureProjectAndExpectCompileAvoidanceWarnings(vararg tasks: String): BuildOperationsAssertions {
        val buildOperations = BuildOperationsFixture(executer, testDirectoryProvider)
        val output = executer.withArgument("--info").withTasks(*tasks).run().normalizedOutput
        return BuildOperationsAssertions(buildOperations, output, true)
    }

    protected
    fun configureProjectAndExpectCompileFailure(expectedFailure: String) {
        val error = executer.runWithFailure().error
        MatcherAssert.assertThat(error, CoreMatchers.containsString(expectedFailure))
    }
}
