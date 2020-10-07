package org.gradle.kotlin.dsl.integration

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.kotlin.dsl.integration.BuildScriptCompileAvoidanceIntegrationTest.CacheBuster.cacheBuster
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test


class BuildScriptCompileAvoidanceIntegrationTest : AbstractKotlinIntegrationTest() {

    object CacheBuster {
        var cacheBuster: Int = 0
    }

    @Before
    fun init() {
        cacheBuster++
        withSettings(
            """
            rootProject.name = "test-project"
            """
        )
    }

    @Test
    fun `avoids buildscript recompilation on non ABI change in buildSrc`() {
        val className = givenJavaClassInBuildSrcContains(
            """
            public void foo() {
                System.out.println("foo");
            }
            """
        )
        withBuildScript("$className().foo()")
        configureProject().buildScriptCompiled().andOutputContains("foo")

        givenJavaClassInBuildSrcContains(
            """
            public void foo() {
                System.out.println("bar");
            }
            """
        )
        configureProject().buildScriptCompilationAvoided().andOutputContains("bar")
    }

    @Test
    fun `recompiles buildscript on ABI change in buildSrc`() {
        val className = givenJavaClassInBuildSrcContains(
            """
            public void foo() {
                System.out.println("foo");
            }
            """
        )
        withBuildScript("$className().foo()")
        configureProject().buildScriptCompiled().andOutputContains("foo")

        givenJavaClassInBuildSrcContains(
            """
            public void foo() {
                System.out.println("bar");
            }
            public void bar() {}
            """
        )
        configureProject().buildScriptCompiled().andOutputContains("bar")
    }

    @ToBeFixedForConfigurationCache
    @Test
    fun `avoids buildscript recompilation on non ABI change in buildscript classpath`() {
        val (className, jarPath) = buildJarForBuildScriptClasspath(
            """
            public void foo() {
                System.out.println("foo");
            }
            """
        )

        withBuildScript(
            """
            buildscript {
                dependencies { classpath(files("$jarPath")) }
            }
            $className().foo()
            """
        )
        configureProject().buildScriptCompiled().andOutputContains("foo")

        buildJarForBuildScriptClasspath(
            """
            public void foo() {
                System.out.println("bar");
            }
            """
        )
        configureProject().buildScriptCompilationAvoided().andOutputContains("bar")
    }

    @ToBeFixedForConfigurationCache
    @Test
    fun `recompiles buildscript on ABI change in buildscript classpath`() {
        val (className, jarPath) = buildJarForBuildScriptClasspath(
            """
            public void foo() {
                System.out.println("foo");
            }
            """
        )

        withBuildScript(
            """
            buildscript {
                dependencies { classpath(files("$jarPath")) }
            }
            $className().foo()
            """
        )
        configureProject().buildScriptCompiled().andOutputContains("foo")

        buildJarForBuildScriptClasspath(
            """
            public void foo() {
                System.out.println("bar");
            }
            public void bar() {}
            """
        )
        configureProject().buildScriptCompiled().andOutputContains("bar")
    }

    @ToBeFixedForConfigurationCache
    @Test
    fun `avoids buildscript recompilation on non ABI change in precompiled script plugin`() {
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
        configureProject().buildScriptCompilationAvoided().andOutputContains("bar")
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
    fun `recompiles buildscript on inline function change in buildSrc class`() {
        val className = givenKotlinClassInBuildSrcContains(
            """
            inline fun foo() {
                println("foo");
            }
            """
        )
        withBuildScript("$className().foo()")
        configureProject().buildScriptCompiled().andOutputContains("foo")

        givenKotlinClassInBuildSrcContains(
            """
            inline fun foo() {
                println("bar");
            }
            """
        )
        configureProject().buildScriptCompiled().andOutputContains("bar")
    }

    @ToBeFixedForConfigurationCache
    @Test
    fun `avoids buildscript recompilation on public function change in buildSrc script`() {
        val packageName = givenKotlinScriptInBuildSrcContains(
            "Foo",
            """
            fun foo() {
                println("foo");
            }
            """
        )
        withBuildScript("$packageName.foo()")
        configureProject().buildScriptCompiled().andOutputContains("foo")

        givenKotlinScriptInBuildSrcContains(
            "Foo",
            """
            fun foo() {
                println("bar");
            }
            """
        )
        configureProject().buildScriptCompilationAvoided().andOutputContains("bar")
    }

    @ToBeFixedForConfigurationCache
    @Test
    fun `recompiles buildscript on inline function change in buildSrc script`() {
        val packageName = givenKotlinScriptInBuildSrcContains(
            "Foo",
            """
            inline fun foo() {
                println("foo");
            }
            """
        )
        withBuildScript("$packageName.foo()")
        configureProject().buildScriptCompiled().andOutputContains("foo")

        givenKotlinScriptInBuildSrcContains(
            "Foo",
            """
            inline fun foo() {
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
        return kotlinClassSourceFile("buildSrc", classBody)
    }

    private
    fun givenKotlinScriptInBuildSrcContains(scriptName: String, scriptBody: String): String {
        withKotlinDslPluginIn("buildSrc")
        return kotlinScriptSourceFile("buildSrc", scriptName, scriptBody)
    }

    private
    fun javaSourceFile(baseDir: String, classBody: String): String {
        val className = "Foo$cacheBuster"
        withFile(
            "$baseDir/src/main/java/com/example/$className.java",
            """
            package com.example;
            public class $className {
                $classBody
            }
            """
        )
        return "com.example.$className"
    }

    private
    fun kotlinClassSourceFile(baseDir: String, classBody: String): String {
        val className = "Foo$cacheBuster"
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

    private
    fun kotlinScriptSourceFile(baseDir: String, scriptName: String, scriptBody: String): String {
        withFile(
            "$baseDir/src/main/kotlin/com/example/$scriptName.kt",
            """
            package com.example
            $scriptBody
            """
        )
        return "com.example"
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

        fun buildScriptCompilationAvoided(): OutputFixture {
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
