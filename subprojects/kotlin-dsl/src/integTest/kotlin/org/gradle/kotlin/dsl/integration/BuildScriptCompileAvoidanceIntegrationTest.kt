package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


class BuildScriptCompileAvoidanceIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    fun `does not recompile buildscript on non ABI change in buildSrc`() {
        withSettings(
            """
            rootProject.name = "foo"
            """
        )
        val className = givenClassInBuildSrcContains(
            """
            public void t1() {
                System.out.println("foo");
            }
            """
        )
        withBuildScript(
            """
                $className().t1()
            """
        )
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
        withSettings(
            """
                rootProject.name = "foo"
            """
        )
        val className = givenClassInBuildSrcContains(
            """
            public void t2() {
                System.out.println("foo");
            }
            """
        )
        withBuildScript(
            """
                $className().t2()
            """
        )
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

    private
    fun givenClassInBuildSrcContains(classBody: String): String {
        withFile(
            "buildSrc/src/main/java/com/example/Foo.java",
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
        OutputFixture(build("--debug").output)

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
