package org.gradle.kotlin.dsl.integration

import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.kotlin.dsl.provider.BUILDSCRIPT_COMPILE_AVOIDANCE_ENABLED
import org.gradle.kotlin.dsl.provider.SCRIPT_CACHE_BASE_DIR_OVERRIDE_PROPERTY
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID
import java.util.regex.Pattern


class BuildScriptCompileAvoidanceIntegrationTest : AbstractKotlinIntegrationTest() {

    companion object CacheBuster {
        var cacheBuster = UUID.randomUUID()
    }

    @Before
    fun init() {
        assumeTrue(BUILDSCRIPT_COMPILE_AVOIDANCE_ENABLED)

        cacheBuster = UUID.randomUUID()

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
        configureProject().assertBuildScriptCompiled().assertOutputContains("foo")

        givenJavaClassInBuildSrcContains(
            """
            public void foo() {
                System.out.println("bar");
            }
            """
        )
        configureProject().assertBuildScriptCompilationAvoided().assertOutputContains("bar")
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
        configureProject().assertBuildScriptCompiled().assertOutputContains("foo")

        givenJavaClassInBuildSrcContains(
            """
            public void foo() {
                System.out.println("bar");
            }
            public void bar() {}
            """
        )
        configureProject().assertBuildScriptBodyRecompiled().assertOutputContains("bar")
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
        configureProject().assertBuildScriptCompiled().assertOutputContains("foo")

        buildJarForBuildScriptClasspath(
            """
            public void foo() {
                System.out.println("bar");
            }
            """
        )
        configureProject().assertBuildScriptCompilationAvoided().assertOutputContains("bar")
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
        configureProject().assertBuildScriptCompiled().assertOutputContains("foo")

        buildJarForBuildScriptClasspath(
            """
            public void foo() {
                System.out.println("bar");
            }
            public void bar() {}
            """
        )
        configureProject().assertBuildScriptBodyRecompiled().assertOutputContains("bar")
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
        configureProjectWithDebugOutput().assertBuildScriptCompiled().assertOutputContains("foo")

        withPrecompiledScriptPluginInBuildSrc(
            pluginId,
            """
                println("bar")
            """
        )
        configureProjectWithDebugOutput().assertBuildScriptCompilationAvoided().assertOutputContains("bar")
    }

    @ToBeFixedForConfigurationCache
    @Test
    fun `recompiles buildscript when new task is registered in precompiled script plugin`() {
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
        configureProjectWithDebugOutput().assertBuildScriptCompiled().assertOutputContains("foo")

        withPrecompiledScriptPluginInBuildSrc(
            pluginId,
            """
                println("bar")
                tasks.register("foo")
            """
        )
        configureProjectWithDebugOutput().assertBuildScriptCompiled().assertOutputContains("bar")
    }

    @ToBeFixedForConfigurationCache
    @Test
    fun `avoids buildscript recompilation when task is configured in precompiled script plugin`() {
        val pluginId = "my-plugin"
        withPrecompiledScriptPluginInBuildSrc(
            pluginId,
            """
                println("foo")
                tasks.register("foo")
            """
        )
        withBuildScript(
            """
                plugins {
                    id("$pluginId")
                }
            """
        )
        configureProjectWithDebugOutput().assertBuildScriptCompiled().assertOutputContains("foo")

        withPrecompiledScriptPluginInBuildSrc(
            pluginId,
            """
                tasks.register("foo") { doLast { println("bar from task") } }
            """
        )
        configureProjectWithDebugOutput("foo").assertBuildScriptCompilationAvoided().assertOutputContains("bar from task")
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
        configureProjectWithDebugOutput().assertBuildScriptCompiled().assertOutputContains("foo")

        withPrecompiledScriptPluginInBuildSrc(
            pluginId,
            """
                plugins {
                    id("java")
                }
                println("bar")
            """
        )
        configureProjectWithDebugOutput().assertBuildScriptCompiled().assertOutputContains("bar")
    }

    @ToBeFixedForConfigurationCache
    @Test
    fun `recompiles buildscript on inline function change in buildSrc class`() {
        val className = givenKotlinClassInBuildSrcContains(
            """
            inline fun foo() {
                println("foo")
            }
            """
        )
        withBuildScript("$className().foo()")
        configureProject().assertBuildScriptCompiled().assertOutputContains("foo")

        givenKotlinClassInBuildSrcContains(
            """
            inline fun foo() {
                println("bar")
            }
            """
        )
        configureProject().assertBuildScriptBodyRecompiled().assertOutputContains("bar")
    }

    @ToBeFixedForConfigurationCache
    @Test
    fun `avoids buildscript recompilation on public function change in buildSrc script`() {
        val packageName = givenKotlinScriptInBuildSrcContains(
            "Foo",
            """
            fun foo() {
                println("foo")
            }
            """
        )
        withBuildScript("$packageName.foo()")
        configureProject().assertBuildScriptCompiled().assertOutputContains("foo")

        givenKotlinScriptInBuildSrcContains(
            "Foo",
            """
            fun foo() {
                println("bar")
            }
            """
        )
        configureProject().assertBuildScriptCompilationAvoided().assertOutputContains("bar")
    }

    @ToBeFixedForConfigurationCache
    @Test
    fun `recompiles buildscript on inline function change in buildSrc script`() {
        val packageName = givenKotlinScriptInBuildSrcContains(
            "Foo",
            """
            inline fun foo() {
                println("foo")
            }
            """
        )
        withBuildScript("$packageName.foo()")
        configureProject().assertBuildScriptCompiled().assertOutputContains("foo")

        givenKotlinScriptInBuildSrcContains(
            "Foo",
            """
            inline fun foo() {
                println("bar")
            }
            """
        )
        configureProject().assertBuildScriptBodyRecompiled().assertOutputContains("bar")
    }

    @ToBeFixedForConfigurationCache
    @Test
    fun `avoids buildscript recompilation on internal inline function change in buildSrc class`() {
        val className = givenKotlinClassInBuildSrcContains(
            """
            fun foo() = bar()
            internal inline fun bar() {
                println("foo")
            }
            """
        )
        withBuildScript("$className().foo()")
        configureProject().assertBuildScriptCompiled().assertOutputContains("foo")

        givenKotlinClassInBuildSrcContains(
            """
            fun foo() = bar()
            internal inline fun bar() {
                println("bar")
            }
            """
        )
        configureProject().assertBuildScriptCompilationAvoided().assertOutputContains("bar")
    }

    @ToBeFixedForConfigurationCache
    @Test
    fun `recompiles buildscript on const val field change in buildSrc script`() {
        val packageName = givenKotlinScriptInBuildSrcContains(
            "foo",
            """
            const val FOO = "foo"
            """
        )
        withBuildScript("println($packageName.FOO)")
        configureProject().assertBuildScriptCompiled().assertOutputContains("foo")

        givenKotlinScriptInBuildSrcContains(
            "foo",
            """
            const val FOO = "bar"
            """
        )
        configureProject().assertBuildScriptBodyRecompiled().assertOutputContains("bar")
    }

    @ToBeFixedForConfigurationCache
    @Test
    fun `recompiles buildscript when plugin extension registration name changes from a precompiled plugin`() {
        val pluginId = "my-plugin"
        val extensionClass = """
            open class TestExtension {
                var message = "some-message"
            }
        """
        withPrecompiledScriptPluginInBuildSrc(
            pluginId,
            """
                $extensionClass
                project.extensions.create<TestExtension>("foo")
            """
        )
        withBuildScript(
            """
                plugins {
                    id("$pluginId")
                }
                foo {
                    message = "foo"
                }
            """
        )
        configureProjectWithDebugOutput().assertBuildScriptCompiled()

        withPrecompiledScriptPluginInBuildSrc(
            pluginId,
            """
                $extensionClass
                project.extensions.create<TestExtension>("bar")
            """
        )
        configureProjectAndExpectCompileFailure("Unresolved reference: foo")
    }

    @ToBeFixedForConfigurationCache
    @Test
    fun `avoids buildscript recompilation on non ABI changes to multifile class in buildSrc`() {
        val multifileAnnotations = """
            @file:JvmName("Utils")
            @file:JvmMultifileClass
        """
        val packageName = givenKotlinScriptInBuildSrcContains(
            "foo",
            """
            fun foo() = "foo"
            """,
            multifileAnnotations
        )
        givenKotlinScriptInBuildSrcContains(
            "bar",
            """
            fun bar() = "bar"
            """,
            multifileAnnotations
        )
        withBuildScript("println($packageName.foo() + $packageName.bar())")
        configureProject().assertBuildScriptCompiled().assertOutputContains("foobar")

        givenKotlinScriptInBuildSrcContains(
            "foo",
            """
            fun foo() = "bar"
            """,
            multifileAnnotations
        )
        givenKotlinScriptInBuildSrcContains(
            "bar",
            """
            fun bar() = "foo"
            """,
            multifileAnnotations
        )
        configureProject().assertBuildScriptCompilationAvoided().assertOutputContains("barfoo")
    }

    @ToBeFixedForConfigurationCache
    @Test
    fun `recompiles buildscript when inline function changes in multifile class in buildSrc`() {
        val multifileAnnotations = """
            @file:JvmName("Utils")
            @file:JvmMultifileClass
        """
        val packageName = givenKotlinScriptInBuildSrcContains(
            "foo",
            """
            inline fun foo() = "foo"
            """,
            multifileAnnotations
        )
        givenKotlinScriptInBuildSrcContains(
            "bar",
            """
            inline fun bar() = "bar"
            """,
            multifileAnnotations
        )
        withBuildScript("println($packageName.foo() + $packageName.bar())")
        configureProject().assertBuildScriptCompiled().assertOutputContains("foobar")

        givenKotlinScriptInBuildSrcContains(
            "foo",
            """
            inline fun foo() = "bar"
            """,
            multifileAnnotations
        )
        givenKotlinScriptInBuildSrcContains(
            "bar",
            """
            inline fun bar() = "foo"
            """,
            multifileAnnotations
        )
        configureProject().assertBuildScriptBodyRecompiled().assertOutputContains("barfoo")
    }

    @ToBeFixedForConfigurationCache
    @Test
    fun `avoids buildscript recompilation on internal inline function changes in multifile class in buildSrc`() {
        val multifileAnnotations = """
            @file:JvmName("Utils")
            @file:JvmMultifileClass
        """
        val packageName = givenKotlinScriptInBuildSrcContains(
            "foo",
            """
            fun foo() = fooInternal()
            internal inline fun fooInternal() = "foo"
            """,
            multifileAnnotations
        )
        givenKotlinScriptInBuildSrcContains(
            "bar",
            """
            fun bar() = barInternal()
            internal inline fun barInternal() = "bar"
            """,
            multifileAnnotations
        )
        withBuildScript("println($packageName.foo() + $packageName.bar())")
        configureProject().assertBuildScriptCompiled().assertOutputContains("foobar")

        givenKotlinScriptInBuildSrcContains(
            "foo",
            """
            fun foo() = fooInternal()
            internal inline fun fooInternal() = "bar"
            """,
            multifileAnnotations
        )
        givenKotlinScriptInBuildSrcContains(
            "bar",
            """
            fun bar() = barInternal()
            internal inline fun barInternal() = "foo"
            """,
            multifileAnnotations
        )
        configureProject().assertBuildScriptCompilationAvoided().assertOutputContains("barfoo")
    }

    @Test
    fun `recompiles buildscript when not able to determine Kotlin metadata kind for class on buildscript classpath`() {
        givenJavaClassInBuildSrcContains(
            """
            public static String foo() {
                return "foo";
            }
            """,
            "@kotlin.Metadata(k=42, mv={1, 4, 0})"
        )
        withBuildScript("println(\"foo\")")
        configureProject().assertBuildScriptCompiled().assertOutputContains("foo")

        givenJavaClassInBuildSrcContains(
            """
            public static String foo() {
                return "bar";
            }
            """,
            "@kotlin.Metadata(k=42, mv={1, 4, 0})"
        )
        configureProject().assertBuildScriptBodyRecompiled().assertOutputContains("foo")
    }

    @Test
    fun `avoids recompiling buildscript when not able to determine Kotlin metadata kind for unchanged class on buildscript classpath`() {
        givenJavaClassInBuildSrcContains(
            """
            public static String bar() {
                return "bar";
            }
            """,
            "@kotlin.Metadata(k=42, mv={1, 4, 0})"
        )
        withBuildScript("println(\"foo\")")
        configureProject().assertBuildScriptCompiled().assertOutputContains("foo")
        configureProject().assertBuildScriptCompilationAvoided()
    }

    private
    fun withKotlinDslPluginInBuildSrc() {
        // this is to force buildSrc/build.gradle.kts to be written to test-local buildscript cache
        // and not to be reused from daemon's cache from other tests when daemon is in use
        withBuildScriptIn("buildSrc", scriptWithKotlinDslPlugin()).appendText(
            """
                val cacheBuster = "$cacheBuster"
            """
        )
    }

    private
    fun withPrecompiledScriptPluginInBuildSrc(pluginId: String, pluginSource: String) {
        withKotlinDslPluginInBuildSrc()
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
    fun givenJavaClassInBuildSrcContains(classBody: String, classAnnotations: String = ""): String =
        javaSourceFile("buildSrc", classBody, classAnnotations)

    private
    fun givenKotlinClassInBuildSrcContains(classBody: String): String {
        withKotlinDslPluginInBuildSrc()
        return kotlinClassSourceFile("buildSrc", classBody)
    }

    private
    fun givenKotlinScriptInBuildSrcContains(scriptName: String, scriptBody: String, scriptPrefix: String = ""): String {
        withKotlinDslPluginInBuildSrc()
        return kotlinScriptSourceFile("buildSrc", scriptName, scriptBody, scriptPrefix)
    }

    private
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

    private
    fun configureProject(vararg tasks: String): BuildOperationsAssertions {
        val buildOperations = BuildOperationsFixture(executer, testDirectoryProvider)
        executer.withArgument("-D$SCRIPT_CACHE_BASE_DIR_OVERRIDE_PROPERTY=${testDirectoryProvider.testDirectory}")
        val output = executer.withTasks(*tasks).run().normalizedOutput
        return BuildOperationsAssertions(buildOperations, output)
    }

    private
    fun configureProjectAndExpectCompileFailure(expectedFailure: String) {
        executer.withArgument("-D$SCRIPT_CACHE_BASE_DIR_OVERRIDE_PROPERTY=${testDirectoryProvider.testDirectory}")
        val error = executer.runWithFailure().error
        assertThat(error, containsString(expectedFailure))
    }

    // There seems to be a bug in BuildOperationTrace handling of projects with precompiled script plugins
    // An assertion fails at BuildOperationTrace.java:338
    // leaving this one as a workaround for test cases that have precompiled script plugins until the underlying issue is fixed
    private
    fun configureProjectWithDebugOutput(vararg tasks: String): DebugOutputFixture {
        executer.withArgument("-D$SCRIPT_CACHE_BASE_DIR_OVERRIDE_PROPERTY=${testDirectoryProvider.testDirectory}")
        return DebugOutputFixture(executer.withArgument("--debug").withTasks(*tasks).run().normalizedOutput)
    }
}


private
class BuildOperationsAssertions(buildOperationsFixture: BuildOperationsFixture, val output: String) {
    private
    val classpathCompileOperations = buildOperationsFixture.all(Pattern.compile("Compile script build.gradle.kts \\(CLASSPATH\\)"))

    private
    val bodyCompileOperations = buildOperationsFixture.all(Pattern.compile("Compile script build.gradle.kts \\(BODY\\)"))

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
        assertThat(output, containsString(expectedOutput))
        return this
    }
}


private
class DebugOutputFixture(val output: String) {
    private
    val scriptClasspathCompileOperationStartMarker = "Build operation 'Compile script build.gradle.kts (CLASSPATH)' started"

    private
    val scriptBodyCompileOperationStartMarker = "Build operation 'Compile script build.gradle.kts (BODY)' started"

    fun assertBuildScriptCompiled(): DebugOutputFixture {
        if (output.contains(scriptClasspathCompileOperationStartMarker) || output.contains(scriptBodyCompileOperationStartMarker)) {
            return this
        }
        throw AssertionError("Expected script to be compiled, but it wasn't")
    }

    fun assertBuildScriptCompilationAvoided(): DebugOutputFixture {
        if (output.contains(scriptClasspathCompileOperationStartMarker) || output.contains(scriptBodyCompileOperationStartMarker)) {
            throw AssertionError("Expected script compilation to be avoided, but the buildscript was recompiled")
        }
        return this
    }

    fun assertOutputContains(expectedOutput: String) {
        assertThat(output, containsString("[system.out] $expectedOutput"))
    }
}
