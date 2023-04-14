package org.gradle.kotlin.dsl.integration

import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.kotlin.dsl.provider.KOTLIN_SCRIPT_COMPILATION_AVOIDANCE_ENABLED_PROPERTY
import org.gradle.util.Matchers.isEmpty
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.endsWith
import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasSize
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.regex.Pattern


class BuildScriptCompileAvoidanceIntegrationTest : AbstractKotlinIntegrationTest() {

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

    @Test
    fun `script compilation avoidance can be disabled via a system property`() {

        withFile("gradle.properties", "systemProp.$KOTLIN_SCRIPT_COMPILATION_AVOIDANCE_ENABLED_PROPERTY=false")

        val className = givenJavaClassInBuildSrcContains("""public void foo() { System.out.println("foo"); }""")
        withUniqueScript("$className().foo()")
        configureProject().assertBuildScriptCompiled().assertOutputContains("foo")

        givenJavaClassInBuildSrcContains("""public void foo() { System.out.println("bar"); }""")
        configureProject().assertBuildScriptBodyRecompiled().assertOutputContains("bar")
    }

    @Test
    @UnsupportedWithConfigurationCache(because = "test rely on configuration phase output")
    fun `avoids buildscript recompilation on included build JAR rebuild`() {

        withDefaultSettingsIn("build-logic")
            .appendText("""rootProject.name = "build-logic"""")
        withKotlinDslPluginIn("build-logic")
        withFile("build-logic/src/main/kotlin/my-plugin.gradle.kts", "")
        val className = kotlinClassSourceFile("build-logic", """
            inline fun foo() { println("bar") }
        """)
        withSettings(""" pluginManagement { includeBuild("build-logic") } """)

        withUniqueScript("""
            plugins { id("my-plugin") }
            $className().foo()
        """)
        configureProject().assertBuildScriptCompiled().assertOutputContains("bar")

        // Delete the JAR as this is not cacheable and by default JARs are not reproducible
        require(existing("build-logic/build/libs/build-logic.jar").delete())

        configureProject().assertBuildScriptCompilationAvoided().assertOutputContains("bar")
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
        withUniqueScript("$className().foo()")
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
    fun `avoids buildscript recompilation on resource file change in buildSrc`() {
        val className = givenJavaClassInBuildSrcContains(
            """
            public void foo() {
                System.out.println("foo");
            }
            """
        )
        withFile("buildSrc/src/main/resources/foo.txt", "foo")
        withUniqueScript("$className().foo()")
        configureProject().assertBuildScriptCompiled().assertOutputContains("foo")

        withFile("buildSrc/src/main/resources/foo.txt", "bar")
        configureProject().assertBuildScriptCompilationAvoided().assertOutputContains("foo")
    }

    @Test
    fun `avoids buildscript recompilation on non-code change in buildSrc`() {
        val className = givenJavaClassInBuildSrcContains(
            """
            public void foo() {
                System.out.println("foo");
            }
            """
        )
        withUniqueScript("$className().foo()")
        configureProject().assertBuildScriptCompiled().assertOutputContains("foo")

        givenJavaClassInBuildSrcContains(
            """
            public void foo() {
                // a comment
                System.out.println("foo");
            }
            """
        )
        configureProject().assertBuildScriptCompilationAvoided().assertOutputContains("foo")
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
        withUniqueScript("$className().foo()")
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

    @Test
    @UnsupportedWithConfigurationCache(because = "test rely on configuration phase output")
    fun `avoids buildscript recompilation on non ABI change in buildscript classpath`() {
        val (className, jarPath) = buildJarForBuildScriptClasspath(
            """
            public void foo() {
                System.out.println("foo");
            }
            """
        )

        withUniqueScript(
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

    @Test
    @UnsupportedWithConfigurationCache(because = "test rely on configuration phase output")
    fun `recompiles buildscript on ABI change in buildscript classpath`() {
        val (className, jarPath) = buildJarForBuildScriptClasspath(
            """
            public void foo() {
                System.out.println("foo");
            }
            """
        )

        withUniqueScript(
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

    @Test
    @UnsupportedWithConfigurationCache(because = "test rely on configuration phase output")
    fun `avoids buildscript recompilation when jar that can not be used for compile avoidance initially on buildsript classpath is touched`() {
        val (className, jarPath) = buildKotlinJarForBuildScriptClasspath(
            """
            inline fun foo() {
                val sum: (Int, Int) -> Int = { x, y -> x + y }
                println("foo = " + sum(2, 2))
            }
            """
        )

        withUniqueScript(
            """
            buildscript {
                dependencies { classpath(files("$jarPath")) }
            }
            $className().foo()
            """
        )
        configureProjectAndExpectCompileAvoidanceWarnings().assertBuildScriptCompiled().assertOutputContains("foo = 4")

        existing(jarPath).setLastModified(1)
        configureProject().assertBuildScriptCompilationAvoided().assertOutputContains("foo = 4")
    }

    @Test
    fun `avoids buildscript recompilation on non ABI change in precompiled script plugin`() {
        val pluginId = "my-plugin"
        withPrecompiledScriptPluginInBuildSrc(
            pluginId,
            """
                println("foo")
            """
        )
        withUniqueScript(
            """
                plugins {
                    id("$pluginId")
                }
            """
        )
        configureProject().assertBuildScriptCompiled().assertOutputContains("foo")

        withPrecompiledScriptPluginInBuildSrc(
            pluginId,
            """
                println("bar")
            """
        )
        configureProject().assertBuildScriptCompilationAvoided().assertOutputContains("bar")
    }

    @Test
    fun `recompiles buildscript when new task is registered in precompiled script plugin`() {
        val pluginId = "my-plugin"
        withPrecompiledScriptPluginInBuildSrc(
            pluginId,
            """
                println("foo")
            """
        )
        withUniqueScript(
            """
                plugins {
                    id("$pluginId")
                }
            """
        )
        configureProject().assertBuildScriptCompiled().assertOutputContains("foo")

        withPrecompiledScriptPluginInBuildSrc(
            pluginId,
            """
                println("bar")
                tasks.register("foo")
            """
        )
        configureProject().assertBuildScriptCompiled().assertOutputContains("bar")
    }

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
        withUniqueScript(
            """
                plugins {
                    id("$pluginId")
                }
            """
        )
        configureProject().assertBuildScriptCompiled().assertOutputContains("foo")

        withPrecompiledScriptPluginInBuildSrc(
            pluginId,
            """
                tasks.register("foo") { doLast { println("bar from task") } }
            """
        )
        configureProject("foo").assertBuildScriptCompilationAvoided().assertOutputContains("bar from task")
    }

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
        withUniqueScript(
            """
                plugins {
                    id("$pluginId")
                }
            """
        )
        configureProject().assertBuildScriptCompiled().assertOutputContains("foo")

        withPrecompiledScriptPluginInBuildSrc(
            pluginId,
            """
                plugins {
                    id("java")
                }
                println("bar")
            """
        )
        configureProject().assertBuildScriptCompiled().assertOutputContains("bar")
    }

    @Test
    fun `recompiles buildscript on inline function change in buildSrc class`() {
        val className = givenKotlinClassInBuildSrcContains(
            """
            inline fun foo() {
                println("foo")
            }
            """
        )
        withUniqueScript("$className().foo()")
        configureProjectAndExpectCompileAvoidanceWarnings().assertBuildScriptCompiled().assertOutputContains("foo")
            .assertContainsCompileAvoidanceWarning("buildSrc.jar: class com/example/Foo: inline fun foo(): compile avoidance is not supported with public inline functions")

        givenKotlinClassInBuildSrcContains(
            """
            inline fun foo() {
                println("bar")
            }
            """
        )
        configureProjectAndExpectCompileAvoidanceWarnings().assertBuildScriptBodyRecompiled().assertOutputContains("bar")
            .assertContainsCompileAvoidanceWarning("buildSrc.jar: class com/example/Foo: inline fun foo(): compile avoidance is not supported with public inline functions")
    }

    @Test
    fun `recompiles buildscript on inline lambda function change in buildSrc class`() {
        val className = givenKotlinClassInBuildSrcContains(
            """
            inline fun foo() {
                val sum: (Int, Int) -> Int = { x, y -> x + y }
                println("foo = " + sum(2, 2))
            }
            """
        )
        withUniqueScript("$className().foo()")
        configureProjectAndExpectCompileAvoidanceWarnings().assertBuildScriptCompiled().assertOutputContains("foo = 4")
            .assertContainsCompileAvoidanceWarning("buildSrc.jar: class com/example/Foo: inline fun foo(): compile avoidance is not supported with public inline functions")
            .assertNumberOfCompileAvoidanceWarnings(1)

        givenKotlinClassInBuildSrcContains(
            """
            inline fun foo() {
                val sum: (Int, Int) -> Int = { x, y -> x - y }
                println("foo = " + sum(2, 2))
            }
            """
        )
        configureProjectAndExpectCompileAvoidanceWarnings().assertBuildScriptCompiled().assertOutputContains("foo = 0")
            .assertContainsCompileAvoidanceWarning("buildSrc.jar: class com/example/Foo: inline fun foo(): compile avoidance is not supported with public inline functions")
            .assertNumberOfCompileAvoidanceWarnings(1)
    }

    @Test
    @UnsupportedWithConfigurationCache(because = "test rely on configuration phase output")
    fun `avoids buildscript recompilation when resource file metadata is changed`() {
        val className = givenKotlinClassInBuildSrcContains(
            """
            inline fun foo() {
                val sum: (Int, Int) -> Int = { x, y -> x + y }
                println("foo = " + sum(2, 2))
            }
            """
        )
        withUniqueScript("$className().foo()")
        val resourceFile = withFile("buildSrc/src/main/resources/foo.txt", "foo")
        configureProjectAndExpectCompileAvoidanceWarnings().assertBuildScriptCompiled().assertOutputContains("foo")

        resourceFile.setLastModified(1)
        resourceFile.setReadOnly()
        configureProjectAndExpectCompileAvoidanceWarnings().assertBuildScriptCompilationAvoided().assertOutputContains("foo")
    }

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
        withUniqueScript("$packageName.foo()")
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
        withUniqueScript("$packageName.foo()")
        configureProjectAndExpectCompileAvoidanceWarnings().assertBuildScriptCompiled().assertOutputContains("foo")
            .assertContainsCompileAvoidanceWarning("buildSrc.jar: class com/example/FooKt: inline fun foo(): compile avoidance is not supported with public inline functions")

        givenKotlinScriptInBuildSrcContains(
            "Foo",
            """
            inline fun foo() {
                println("bar")
            }
            """
        )
        configureProjectAndExpectCompileAvoidanceWarnings().assertBuildScriptBodyRecompiled().assertOutputContains("bar")
            .assertContainsCompileAvoidanceWarning("buildSrc.jar: class com/example/FooKt: inline fun foo(): compile avoidance is not supported with public inline functions")
    }

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
        withUniqueScript("$className().foo()")
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

    @Test
    fun `recompiles buildscript on const val field change in buildSrc script`() {
        val packageName = givenKotlinScriptInBuildSrcContains(
            "foo",
            """
            const val FOO = "foo"
            """
        )
        withUniqueScript("println($packageName.FOO)")
        configureProject().assertBuildScriptCompiled().assertOutputContains("foo")

        givenKotlinScriptInBuildSrcContains(
            "foo",
            """
            const val FOO = "bar"
            """
        )
        configureProject().assertBuildScriptBodyRecompiled().assertOutputContains("bar")
    }

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
        withUniqueScript(
            """
                plugins {
                    id("$pluginId")
                }
                foo {
                    message = "foo"
                }
            """
        )
        configureProject().assertBuildScriptCompiled()

        withPrecompiledScriptPluginInBuildSrc(
            pluginId,
            """
                $extensionClass
                project.extensions.create<TestExtension>("bar")
            """
        )
        configureProjectAndExpectCompileFailure("Unresolved reference: foo")
    }

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
        withUniqueScript("println($packageName.foo() + $packageName.bar())")
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
        withUniqueScript("println($packageName.foo() + $packageName.bar())")
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
        withUniqueScript("println($packageName.foo() + $packageName.bar())")
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
        withUniqueScript("println(\"foo\")")
        configureProjectAndExpectCompileAvoidanceWarnings().assertBuildScriptCompiled().assertOutputContains("foo")
            .assertContainsCompileAvoidanceWarning("buildSrc.jar: class com/example/Foo: Unknown Kotlin metadata with kind: 42 on class com/example/Foo - this can happen if this class is compiled with a later Kotlin version than the Kotlin compiler used by Gradle")

        givenJavaClassInBuildSrcContains(
            """
            public static String foo() {
                return "bar";
            }
            """,
            "@kotlin.Metadata(k=42, mv={1, 4, 0})"
        )
        configureProjectAndExpectCompileAvoidanceWarnings().assertBuildScriptBodyRecompiled().assertOutputContains("foo")
            .assertContainsCompileAvoidanceWarning("buildSrc.jar: class com/example/Foo: Unknown Kotlin metadata with kind: 42 on class com/example/Foo - this can happen if this class is compiled with a later Kotlin version than the Kotlin compiler used by Gradle")
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
        withUniqueScript("println(\"foo\")")
        configureProjectAndExpectCompileAvoidanceWarnings().assertBuildScriptCompiled().assertOutputContains("foo")
            .assertContainsCompileAvoidanceWarning("buildSrc.jar: class com/example/Foo: Unknown Kotlin metadata with kind: 42 on class com/example/Foo - this can happen if this class is compiled with a later Kotlin version than the Kotlin compiler used by Gradle")
        configureProject().assertBuildScriptCompilationAvoided()
    }

    private
    fun withUniqueScript(script: String) = withBuildScript(script).apply {
        bustScriptCache()
    }

    private
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
    fun buildKotlinJarForBuildScriptClasspath(classBody: String): Pair<String, String> {
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
                    `kotlin-dsl`
                    id("java-library")
                }
                repositories {
                    mavenCentral()
                }
            """
        )
        val className = kotlinClassSourceFile(baseDir, classBody)
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
        val output = executer.withTasks(*tasks).run().normalizedOutput
        return BuildOperationsAssertions(buildOperations, output)
    }

    private
    fun configureProjectAndExpectCompileAvoidanceWarnings(vararg tasks: String): BuildOperationsAssertions {
        val buildOperations = BuildOperationsFixture(executer, testDirectoryProvider)
        val output = executer.withArgument("--info").withTasks(*tasks).run().normalizedOutput
        return BuildOperationsAssertions(buildOperations, output, true)
    }

    private
    fun configureProjectAndExpectCompileFailure(expectedFailure: String) {
        val error = executer.runWithFailure().error
        assertThat(error, containsString(expectedFailure))
    }
}


private
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
            assertThat(compileAvoidanceWarnings, isEmpty())
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
        assertThat(output, containsString(expectedOutput))
        return this
    }

    fun assertContainsCompileAvoidanceWarning(end: String): BuildOperationsAssertions {
        assertThat(compileAvoidanceWarnings, hasItem(endsWith(end)))
        return this
    }

    fun assertNumberOfCompileAvoidanceWarnings(n: Int): BuildOperationsAssertions {
        assertThat(compileAvoidanceWarnings, hasSize(n))
        return this
    }
}
