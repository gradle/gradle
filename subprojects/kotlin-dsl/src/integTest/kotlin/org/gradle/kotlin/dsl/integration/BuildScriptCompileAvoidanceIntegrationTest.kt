package org.gradle.kotlin.dsl.integration

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test


class BuildScriptCompileAvoidanceIntegrationTest : AbstractKotlinIntegrationTest() {

    @Before
    fun init() {
        withSettings(
            """
            rootProject.name = "test-project"
            """
        )
    }

    @Test
    fun `does not recompile buildscript on non ABI change in buildSrc`() {
        val className = givenClassInBuildSrcContains(
            """
            public void t1() {
                System.out.println("foo");
            }
            """
        )
        withBuildScript("$className().t1()")
        configureProject().buildScriptCompiled().andOutputContains("foo")

        givenClassInBuildSrcContains(
            """
            public void t1() {
                System.out.println("bar");
            }
            """
        )
        configureProject().buildScriptNotCompiled().andOutputContains("bar")
    }

    @Test
    fun `recompiles buildscript on ABI change in buildSrc`() {
        val className = givenClassInBuildSrcContains(
            """
            public void t2() {
                System.out.println("foo");
            }
            """
        )
        withBuildScript("$className().t2()")
        configureProject().buildScriptCompiled().andOutputContains("foo")

        givenClassInBuildSrcContains(
            """
            public void t2() {
                System.out.println("bar");
            }
            public void bar() {}
            """
        )
        configureProject().buildScriptCompiled().andOutputContains("bar")
    }

    @ToBeFixedForConfigurationCache
    @Test
    fun `does not recompile buildscript on non ABI change in buildscript classpath`() {
        val (className, jarPath) = buildJarForBuildScriptClasspath(
            """
            public void t3() {
                System.out.println("foo");
            }
            """
        )

        withBuildScript(
            """
            buildscript {
                dependencies { classpath(files("$jarPath")) }
            }
            $className().t3()
            """
        )
        configureProject().buildScriptCompiled().andOutputContains("foo")

        buildJarForBuildScriptClasspath(
            """
            public void t3() {
                System.out.println("bar");
            }
            """
        )
        configureProject().buildScriptNotCompiled().andOutputContains("bar")
    }

    @ToBeFixedForConfigurationCache
    @Test
    fun `recompiles buildscript on ABI change in buildscript classpath`() {
        val (className, jarPath) = buildJarForBuildScriptClasspath(
            """
            public void t4() {
                System.out.println("foo");
            }
            """
        )

        withBuildScript(
            """
            buildscript {
                dependencies { classpath(files("$jarPath")) }
            }
            $className().t4()
            """
        )
        configureProject().buildScriptCompiled().andOutputContains("foo")

        buildJarForBuildScriptClasspath(
            """
            public void t4() {
                System.out.println("bar");
            }
            public void foo() {}
            """
        )
        configureProject().buildScriptCompiled().andOutputContains("bar")
    }

    private
    fun buildJarForBuildScriptClasspath(classBody: String): Pair<String, String> {
        val baseDir = "buildscript"
        withSettingsIn(
            baseDir,
            """
                rootProject.name = "buildscript"
            """
        )
        withBuildScriptIn(
            baseDir,
            """
                plugins {
                    id("java-library")
                }
            """
        )
        val className = sourceFile(baseDir, classBody)
        build(existing(baseDir), "build")
        val jarPath = "$baseDir/build/libs/buildscript.jar"
        assertTrue(existing(jarPath).exists())
        return Pair(className, jarPath)
    }

    private
    fun givenClassInBuildSrcContains(classBody: String): String =
        sourceFile("buildSrc", classBody)

    private
    fun sourceFile(baseDir: String, classBody: String): String {
        withFile(
            "$baseDir/src/main/java/com/example/Foo.java",
            """
            package com.example;
            public class Foo {
                $classBody
            }
            """
        )
        return "com.example.Foo"
    }

    private
    fun configureProject() =
        OutputFixture(gradleExecuterFor(arrayOf("--debug")).withStackTraceChecksDisabled().run().output)

    class OutputFixture(val output: String) {
        private
        val scriptClasspathCompileOperationStartMarker = "Build operation 'Compile script build.gradle.kts (CLASSPATH)' started"

        private
        val scriptBodyCompileOperationStartMarker = "Build operation 'Compile script build.gradle.kts (BODY)' started"

        fun buildScriptCompiled(): OutputFixture {
            if (!output.contains(scriptClasspathCompileOperationStartMarker) && !output.contains(scriptBodyCompileOperationStartMarker)) {
                throw AssertionError("Expected script to be compiled, but it wasn't")
            }
            return this
        }

        fun buildScriptNotCompiled(): OutputFixture {
            if (output.contains(scriptClasspathCompileOperationStartMarker) || output.contains(scriptBodyCompileOperationStartMarker)) {
                throw AssertionError("Expected script compilation to be avoided, but the buildscript was recompiled")
            }
            return this
        }

        fun andOutputContains(expectedOutput: String) {
            assertThat(output, CoreMatchers.containsString("[system.out] $expectedOutput"))
        }
    }
}
