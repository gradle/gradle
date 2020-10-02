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
        val className = givenJavaClassInBuildSrcContains(
            """
            public void t1() {
                System.out.println("foo");
            }
            """
        )
        withBuildScript("$className().t1()")
        configureProject().buildScriptCompiled().andOutputContains("foo")

        givenJavaClassInBuildSrcContains(
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
        val className = givenJavaClassInBuildSrcContains(
            """
            public void t2() {
                System.out.println("foo");
            }
            """
        )
        withBuildScript("$className().t2()")
        configureProject().buildScriptCompiled().andOutputContains("foo")

        givenJavaClassInBuildSrcContains(
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

    @ToBeFixedForConfigurationCache
    @Test
    fun `does not recompile buildscript on non ABI change in precompiled script plugin`() {
        val pluginId = "my-plugin"
        withPrecompiledScriptPluginInBuildSrc(
            pluginId,
            """
                println("foo")
            """
        )
        withBuildScript(
            """
                plugins {
                    id("$pluginId")
                }
            """
        )
        configureProject().buildScriptCompiled().andOutputContains("foo")

        withPrecompiledScriptPluginInBuildSrc(
            pluginId,
            """
                println("bar")
            """
        )
        configureProject().buildScriptNotCompiled().andOutputContains("bar")
    }

    @ToBeFixedForConfigurationCache
    @Test
    fun `recompiles buildscript when plugins applied from a precompiled plugin change`() {
        val pluginId = "my-plugin"
        withPrecompiledScriptPluginInBuildSrc(
            pluginId,
            """
                plugins {
                    id("java-library")
                }
                println("foo")
            """
        )
        withBuildScript(
            """
                plugins {
                    id("$pluginId")
                }
            """
        )
        configureProject().buildScriptCompiled().andOutputContains("foo")

        withPrecompiledScriptPluginInBuildSrc(
            pluginId,
            """
                plugins {
                    id("java")
                }
                println("bar")
            """
        )
        configureProject().buildScriptCompiled().andOutputContains("bar")
    }

    @ToBeFixedForConfigurationCache
    @Test
    fun `recompiles buildscript on inline function change in buildSrc`() {
        val className = givenKotlinClassInBuildSrcContains(
            """
            inline fun t5() {
                println("foo");
            }
            """
        )
        withBuildScript("$className().t5()")
        configureProject().buildScriptCompiled().andOutputContains("foo")

        givenKotlinClassInBuildSrcContains(
            """
            inline fun t5() {
                println("bar");
            }
            """
        )
        configureProject().buildScriptCompiled().andOutputContains("bar")
    }

    private
    fun withPrecompiledScriptPluginInBuildSrc(pluginId: String, pluginSource: String) {
        withKotlinDslPluginIn("buildSrc")
        withFile("buildSrc/src/main/kotlin/$pluginId.gradle.kts", pluginSource)
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
        val className = javaSourceFile(baseDir, classBody)
        build(existing(baseDir), "build")
        val jarPath = "$baseDir/build/libs/buildscript.jar"
        assertTrue(existing(jarPath).exists())
        return Pair(className, jarPath)
    }

    private
    fun givenJavaClassInBuildSrcContains(classBody: String): String =
        javaSourceFile("buildSrc", classBody)

    private
    fun givenKotlinClassInBuildSrcContains(classBody: String): String {
        withKotlinDslPluginIn("buildSrc")
        return kotlinSourceFile("buildSrc", classBody)
    }

    private
    fun javaSourceFile(baseDir: String, classBody: String): String {
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
    fun kotlinSourceFile(baseDir: String, classBody: String): String {
        withFile(
            "$baseDir/src/main/kotlin/com/example/Foo.kt",
            """
            package com.example
            class Foo {
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
