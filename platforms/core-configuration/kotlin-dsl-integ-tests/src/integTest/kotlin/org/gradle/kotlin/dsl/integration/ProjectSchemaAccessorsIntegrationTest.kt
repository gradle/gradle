/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinDslPluginsIntegrationTest
import org.gradle.kotlin.dsl.fixtures.FoldersDsl
import org.gradle.kotlin.dsl.fixtures.FoldersDslExpression
import org.gradle.kotlin.dsl.fixtures.containsMultiLineString
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import java.io.File


@LeaksFileHandles("Kotlin Compiler Daemon working directory")
class ProjectSchemaAccessorsIntegrationTest : AbstractKotlinDslPluginsIntegrationTest() {

    @Test
    fun `can access sub-project specific task`() {

        withDefaultSettings().appendText(
            """
            include(":sub")
            """
        )

        withKotlinBuildSrc()
        withFile(
            "buildSrc/src/main/kotlin/my/base.gradle.kts",
            """
            package my
            tasks.register("myBaseTask")
            """
        )

        withBuildScriptIn(
            "sub",
            """
            plugins {
                base
                my.base
            }

            tasks {
                // prove accessing a `base` plugin task works
                assemble {
                }
                myBaseTask {
                    doLast {
                        println("*my base*")
                    }
                }
            }
            """
        )

        withBuildScript(
            """
            plugins {
                base
            }

            tasks {
                wrapper {
                    gradleVersion = "5.0"
                }
                assemble {
                    doFirst {
                         println("assembling!")
                    }
                }
            }
            """
        )

        assertThat(
            build(":sub:myBaseTask", "-q").output,
            containsString("*my base*")
        )
    }

    @Test
    fun `can access extension of internal type made public`() {

        lateinit var extensionSourceFile: File

        withBuildSrc {
            "src/main/kotlin" {

                extensionSourceFile =
                    withFile(
                        "Extension.kt",
                        """
                        internal
                        class Extension
                        """
                    )

                withFile(
                    "plugin.gradle.kts",
                    """
                    extensions.add("extension", Extension())
                    """
                )
            }
        }

        withBuildScript(
            """

            plugins { id("plugin") }

            inline fun <reified T> typeOf(value: T) = typeOf<T>()

            println("extension: " + typeOf(extension))
            extension { println("extension{} " + typeOf(this)) }
            """
        )

        // The internal Extension type is not accessible
        // so the accessors are typed Any (java.lang.Object)
        assertThat(
            build("help", "-q").output,
            containsMultiLineString(
                """
                extension: java.lang.Object
                extension{} java.lang.Object
                """
            )
        )

        // Making the Extension type accessible
        // should cause the accessors to be regenerated
        // with the now accessible type
        extensionSourceFile.writeText(
            """
            public
            class Extension
            """
        )

        assertThat(
            build("help", "-q").output,
            containsMultiLineString(
                """
                extension: Extension
                extension{} Extension
                """
            )
        )
    }

    @Test
    fun `can access extension of default package type`() {

        withBuildSrc {

            "src/main/kotlin" {

                withFile(
                    "Extension.kt",
                    """
                    class Extension(private val name: String) : org.gradle.api.Named {
                        override fun getName() = name
                    }
                    """
                )

                withFile(
                    "plugin.gradle.kts",
                    """
                    extensions.add("extension", Extension("foo"))
                    """
                )
            }
        }

        withBuildScript(
            """

            plugins { id("plugin") }

            inline fun <reified T> typeOf(value: T) = typeOf<T>()

            println("extension: " + typeOf(extension))
            extension { println("extension{} " + typeOf(this)) }
            """
        )

        assertThat(
            build("help", "-q").output,
            containsMultiLineString(
                """
                extension: Extension
                extension{} Extension
                """
            )
        )
    }

    @Test
    fun `can access task of default package type`() {

        withBuildSrc {
            "src/main/kotlin" {

                withFile(
                    "CustomTask.kt",
                    """
                    open class CustomTask : org.gradle.api.DefaultTask()
                    """
                )

                withFile(
                    "plugin.gradle.kts",
                    """
                    tasks.register<CustomTask>("customTask")
                    """
                )
            }
        }

        withBuildScript(
            """

            plugins { id("plugin") }

            inline fun <reified T> typeOf(value: T) = typeOf<T>()

            println("task: " + typeOf(tasks.customTask))
            tasks.customTask { println("task{} " + typeOf(this)) }
            """
        )

        assertThat(
            build("customTask", "-q").output,
            containsMultiLineString(
                """
                task: org.gradle.api.tasks.TaskProvider<CustomTask>
                task{} CustomTask
                """
            )
        )
    }

    @Test
    fun `can access extension of nested type`() {

        withBuildSrc {
            "src/main/kotlin/my" {

                withFile(
                    "Extension.kt",
                    """
                    package my

                    class Nested {
                        class Extension(private val name: String) : org.gradle.api.Named {
                            override fun getName() = name
                        }
                    }
                    """
                )

                withFile(
                    "plugin.gradle.kts",
                    """
                    package my

                    extensions.add("nested", Nested.Extension("foo"))
                    extensions.add("beans", container(Nested.Extension::class))
                    """
                )
            }
        }

        withBuildScript(
            """

            plugins { id("my.plugin") }

            inline fun <reified T> typeOf(value: T) = typeOf<T>()

            println("nested: " + typeOf(nested))
            nested { println("nested{} " + typeOf(this)) }

            println("beans: " + typeOf(beans))
            beans { println("beans{} " + typeOf(beans)) }

            // ensure API usage
            println(beans.create("bar").name)
            beans.create("baz") { println(name) }
            """
        )

        assertThat(
            build("help", "-q").output,
            containsMultiLineString(
                """
                nested: my.Nested.Extension
                nested{} my.Nested.Extension
                beans: org.gradle.api.NamedDomainObjectContainer<my.Nested.Extension>
                beans{} org.gradle.api.NamedDomainObjectContainer<my.Nested.Extension>
                bar
                baz
                """
            )
        )
    }

    @Test
    fun `multiple generic extension targets`() {

        withBuildSrc {

            "src/main/kotlin" {
                withFile(
                    "types.kt",
                    """

                    package my

                    data class NamedString(val name: String, var value: String? = null)

                    data class NamedLong(val name: String, var value: Long? = null)
                    """
                )

                withFile(
                    "plugin.gradle.kts",
                    """

                    package my

                    val strings = container(NamedString::class) { NamedString(it) }
                    extensions.add("strings", strings)

                    val longs = container(NamedLong::class) { NamedLong(it) }
                    extensions.add("longs", longs)

                    tasks.register("printStringsAndLongs") {
                        val stringList = strings.toList()
                        val longList = longs.toList()
                        doLast {
                            stringList.forEach { println("string: " + it) }
                            longList.forEach { println("long: " + it) }
                        }
                    }
                    """
                )
            }
        }

        withBuildScript(
            """

            plugins { id("my.plugin") }

            strings.create("foo") { value = "bar" }
            longs.create("ltuae") { value = 42L }
            """
        )

        assertThat(
            build("printStringsAndLongs", "-q").output,
            containsMultiLineString(
                """
                string: NamedString(name=foo, value=bar)
                long: NamedLong(name=ltuae, value=42)
                """
            )
        )
    }

    @Test
    fun `conflicting extensions across build scripts with same body`() {

        withFolders {

            "buildSrc" {

                withKotlinDslPlugin()

                "src/main/kotlin" {
                    withFile(
                        "my/extensions.kt",
                        """
                        package my
                        open class App { lateinit var name: String }
                        open class Lib { lateinit var name: String }
                        """
                    )
                    withFile(
                        "app.gradle.kts",
                        """
                        extensions.create("my", my.App::class)
                        """
                    )
                    withFile(
                        "lib.gradle.kts",
                        """
                        extensions.create("my", my.Lib::class)
                        """
                    )
                }
            }

            fun FoldersDsl.withPlugin(plugin: String) =
                withFile(
                    "build.gradle.kts",
                    """
                    plugins { id("$plugin") }
                    my { name = "kotlin-dsl" }
                    """
                )

            "app" {
                withPlugin("app")
            }

            "lib" {
                withPlugin("lib")
            }

            withFile(
                "settings.gradle.kts",
                """
                include("app", "lib")
                """
            )
        }

        build("tasks")
    }

    @Test
    fun `conflicting extensions across build runs`() {

        withFolders {

            "buildSrc" {

                withKotlinDslPlugin()

                "src/main/kotlin" {
                    withFile(
                        "my/extensions.kt",
                        """
                        package my
                        open class App { lateinit var name: String }
                        open class Lib { lateinit var name: String }
                        """
                    )
                    withFile(
                        "app-or-lib.gradle.kts",
                        """
                        val my: String? by project
                        val extensionType = if (my == "app") my.App::class else my.Lib::class
                        extensions.create("my", extensionType)
                        """
                    )
                }
            }

            withFile(
                "build.gradle.kts",
                """
                plugins { id("app-or-lib") }
                my { name = "kotlin-dsl" }
                """
            )
        }

        build("tasks", "-Pmy=lib")

        build("tasks", "-Pmy=app")
    }

    private
    fun FoldersDsl.withKotlinDslPlugin() {
        withFile("settings.gradle.kts", defaultSettingsScript)
        withFile(
            "build.gradle.kts",
            """

            plugins {
                `kotlin-dsl`
            }

            $repositoriesBlock
            """
        )
    }

    @Test
    fun `can configure publishing extension`() {

        withBuildScript(
            """

            plugins {
                `java-library`
                `maven-publish`
            }

            dependencies {
                api("com.google.guava:guava:21.0")
            }

            publishing {
                publications.create<MavenPublication>("mavenJavaLibrary") {
                    from(components["java"])
                }
            }

            dependencies {
                api("org.apache.commons:commons-lang3:3.5")
            }

            """
        )

        build("generatePom")

        val pom = existing("build/publications/mavenJavaLibrary/pom-default.xml").readText()
        assertThat(pom, containsString("com.google.guava"))
        assertThat(pom, containsString("commons-lang3"))
    }

    @Test
    fun `can access NamedDomainObjectContainer extension via generated accessor`() {

        withBuildSrc {
            withFile(
                "src/main/kotlin/my/DocumentationPlugin.kt",
                """
                package my

                import org.gradle.api.*
                import org.gradle.kotlin.dsl.*

                class DocumentationPlugin : Plugin<Project> {

                    override fun apply(project: Project) {
                        val books = project.container(Book::class, ::Book)
                        project.extensions.add("the books", books)
                    }
                }

                data class Book(val name: String)

                """
            )
            existing("build.gradle.kts").appendText(
                """
                gradlePlugin {
                    plugins {
                        register("my.documentation") {
                            id = name
                            implementationClass = "my.DocumentationPlugin"
                        }
                    }
                }
                """
            )
        }

        withBuildScript(
            """

            plugins {
                id("my.documentation")
            }

            (`the books`) {
                register("quickStart") {
                }
                register("userGuide") {
                }
            }

            tasks {
                register("books") {
                    val books = `the books`.toList()
                    doLast { println(books.joinToString { it.name }) }
                }
            }

            """
        )

        assertThat(
            build("books").output,
            containsString("quickStart, userGuide")
        )
    }

    @Test
    fun `can access extensions registered by declared plugins via jit accessor`() {

        withBuildScript(
            """
            plugins { application }

            application { mainClass.set("App") }

            task("mainClassName") {
                val mainClass = application.mainClass
                doLast { println("*" + mainClass.get() + "*") }
            }
            """
        )

        assertThat(
            build("mainClassName").output,
            containsString("*App*")
        )
    }

    @Test
    fun `can access configurations registered by declared plugins via jit accessor`() {

        withDefaultSettings().appendText(
            """
            include("a", "b", "c")
            """
        )

        withBuildScriptIn(
            "a",
            """
            plugins { `java-library` }
            """
        )

        withBuildScriptIn(
            "b",
            """
            plugins { `java-library` }

            ${RepoScriptBlockUtil.mavenCentralRepository(GradleDsl.KOTLIN)}

            dependencies {
                implementation("org.apache.commons:commons-io:1.3.2")
            }
            """
        )

        withBuildScriptIn(
            "c",
            """
            plugins { `java-library` }

            dependencies {
                compileOnly(group = "org.slf4j", name = "slf4j-api", version = "1.7.25")
                api("com.google.guava:guava:21.0")
                implementation("ch.qos.logback:logback-classic:1.2.3") {
                    isTransitive = false
                }
                implementation(project(":a"))
                implementation(project(":b")) {
                    exclude(group = "org.apache.commons")
                }
            }

            ${RepoScriptBlockUtil.mavenCentralRepository(GradleDsl.KOTLIN)}

            configurations.compileClasspath.get().files.forEach {
                println(org.gradle.util.internal.TextUtil.normaliseFileSeparators(it.path))
            }
            """
        )

        val result = build("help", "-q")

        assertThat(
            result.output,
            allOf(
                containsString("slf4j-api-1.7.25.jar"),
                containsString("guava-21.0.jar"),
                containsString("logback-classic-1.2.3.jar"),
                containsString("a/build/classes/java/main"),
                containsString("b/build/classes/java/main"),
                not(containsString("logback-core")),
                not(containsString("commons-io"))
            )
        )
    }

    @Test
    fun `can add artifacts using generated accessors for configurations`() {

        withDefaultSettingsIn("buildSrc")

        withFile(
            "buildSrc/build.gradle.kts",
            """
            plugins {
                `kotlin-dsl`
            }

            gradlePlugin {
                plugins {
                    register("my-plugin") {
                        id = "my-plugin"
                        implementationClass = "plugins.MyPlugin"
                    }
                }
            }

            $repositoriesBlock
            """
        )

        withFile(
            "buildSrc/src/main/kotlin/plugins/MyPlugin.kt",
            """
            package plugins

            import org.gradle.api.*

            class MyPlugin : Plugin<Project> {
                override fun apply(project: Project): Unit = project.run {
                    configurations.create("myConfig")
                }
            }
            """
        )

        withBuildScript(
            """
            plugins {
                id("my-plugin")
            }

            artifacts {
                myConfig(file("first.txt"))
                myConfig(file("second.txt")) {
                    setType("other-type")
                }
            }

            val adhocConfig by configurations.creating
            configurations.create("for-string-invoke")

            (artifacts) {
                adhocConfig(file("first.txt"))
                adhocConfig(file("second.txt")) {
                    setType("other-type")
                }
                "for-string-invoke"(file("first.txt"))
                "for-string-invoke"(file("second.txt")) {
                    setType("other-type")
                }
            }

            listOf(configurations.myConfig.get(), adhocConfig, configurations["for-string-invoke"]).forEach { config ->
                config.artifacts.forEach { artifact ->
                    println("${'$'}{config.name} -> ${'$'}{artifact.name}:${'$'}{artifact.extension}:${'$'}{artifact.type}")
                }
            }
            """
        )

        val result = build("help", "-q")

        assertThat(
            result.output,
            allOf(
                containsString("myConfig -> first:txt:txt"),
                containsString("myConfig -> second:txt:other-type"),
                containsString("adhocConfig -> first:txt:txt"),
                containsString("adhocConfig -> second:txt:other-type")
            )
        )
    }

    @Test
    fun `accessors tasks applied in a mixed Groovy-Kotlin multi-project build`() {

        withDefaultSettings().appendText(
            """
            include("a")
            """
        )
        withBuildScriptIn("a", "")

        val aTasks = build(":a:tasks").output
        assertThat(aTasks, containsString("kotlinDslAccessorsReport"))

        val rootTasks = build(":tasks").output
        assertThat(
            rootTasks,
            containsString("kotlinDslAccessorsReport")
        )
    }

    @Test
    fun `accessor to extension of jvm type is accessible and typed`() {
        withKotlinBuildSrc()
        withFile("buildSrc/src/main/kotlin/my-plugin.gradle.kts", """
            import java.security.MessageDigest
            extensions.add(MessageDigest::class, "javaTypeExtension", MessageDigest.getInstance("MD5"))
        """)
        withBuildScript("""
            plugins {
                id("my-plugin")
            }

            inline fun <reified T> typeOf(t: T) = T::class.simpleName

            javaTypeExtension {
                println("Type of `javaTypeExtension` receiver is " + typeOf(this@javaTypeExtension))
                println("`javaTypeExtension` is MessageDigest? " + (this is java.security.MessageDigest))
            }
        """)

        val result = build("help")
        assertThat(result.output, containsString("Type of `javaTypeExtension` receiver is MessageDigest"))
        assertThat(result.output, containsString("`javaTypeExtension` is MessageDigest? true"))
    }

    @Test
    fun `given extension with inaccessible type, its accessor is typed Any`() {

        withFile(
            "init.gradle",
            """
            class TestExtension {}
            rootProject {
                it.extensions.create("testExtension", TestExtension)
            }
            """
        )

        withBuildScript(
            """

            inline fun <reified T> typeOf(t: T) = T::class.simpleName

            testExtension {
                println("Type of `testExtension` receiver is " + typeOf(this@testExtension))
            }
            """
        )

        val result = build("help", "-I", "init.gradle")
        assertThat(result.output, containsString("Type of `testExtension` receiver is Any"))
    }

    @Test
    fun `given extension with erased generic type parameters, its accessor is typed Any`() {

        withDefaultSettingsIn("buildSrc")

        withFile(
            "buildSrc/build.gradle.kts",
            """
            plugins {
                `kotlin-dsl`
            }

            gradlePlugin {
                plugins {
                    register("mine") {
                        id = "mine"
                        implementationClass = "foo.FooPlugin"
                    }
                }
            }

            $repositoriesBlock
            """
        )

        withFile(
            "buildSrc/src/main/kotlin/foo/FooPlugin.kt",
            """
            package foo

            import org.gradle.api.*

            open class MyExtension<T>

            class FooPlugin : Plugin<Project> {
                override fun apply(project: Project): Unit = project.run {
                    // Using add() without specifying the public type causes type erasure
                    extensions.add("mine", MyExtension<String>())
                }
            }
            """
        )

        withBuildScript(
            """
            plugins {
                id("mine")
            }

            inline fun <reified T> typeOf(t: T) = T::class.simpleName

            mine {
                println("Type of `mine` receiver is " + typeOf(this@mine))
            }
            """
        )

        val result = build("help")
        assertThat(result.output, containsString("Type of `mine` receiver is Any"))
    }

    @Test
    fun `can access nested extensions and conventions registered by declared plugins via jit accessors`() {

        withDefaultSettingsIn("buildSrc")

        withBuildScriptIn(
            "buildSrc",
            """
            plugins {
                `kotlin-dsl`
            }

            gradlePlugin {
                plugins {
                    register("my-plugin") {
                        id = "my-plugin"
                        implementationClass = "plugins.MyPlugin"
                    }
                }
            }

            $repositoriesBlock
            """
        )

        withFile(
            "buildSrc/src/main/kotlin/plugins/MyPlugin.kt",
            """
            package plugins

            import org.gradle.api.*
            import org.gradle.api.plugins.*
            import org.gradle.api.internal.*

            import org.gradle.internal.reflect.*
            import org.gradle.internal.extensibility.*

            import org.gradle.kotlin.dsl.support.serviceOf


            class MyPlugin : Plugin<Project> {
                override fun apply(project: Project): Unit = project.run {
                    val rootExtension = extensions.create("rootExtension", MyExtension::class.java, "root")

                    val rootExtensionNestedExtension = rootExtension.extensions.create("nestedExtension", MyExtension::class.java, "nested-in-extension")
                    rootExtensionNestedExtension.extensions.add("deepExtension", listOf("foo", "bar"))

                    val rootExtensionNestedConvention = objects.newInstance(MyConvention::class.java, "nested-in-extension")
                    rootExtensionNestedConvention.extensions.add("deepExtension", mapOf("foo" to "bar"))

                    val rootConvention = objects.newInstance(MyConvention::class.java, "root")
                    val rootConventionNestedConvention = objects.newInstance(MyConvention::class.java, "nested-in-convention")

                    convention.plugins.put("rootConvention", rootConvention)

                    val rootConventionNestedExtension = rootConvention.extensions.create("nestedExtension", MyExtension::class.java, "nested-in-convention")
                    rootConventionNestedExtension.extensions.add("deepExtension", listOf("bazar", "cathedral"))

                    rootConventionNestedConvention.extensions.add("deepExtension", mapOf("bazar" to "cathedral"))
                }
            }

            abstract class MyExtension(val value: String) : ExtensionAware {
            }

            abstract class MyConvention @javax.inject.Inject constructor(val value: String) : ExtensionAware {
            }
            """
        )

        withBuildScript(
            """
            plugins {
                id("my-plugin")
            }

            rootExtension {
                nestedExtension {
                    require(value == "nested-in-extension", { "rootExtension.nestedExtension" })
                    require(deepExtension == listOf("foo", "bar"), { "rootExtension.nestedExtension.deepExtension" })
                }
            }

            rootConvention {
                nestedExtension {
                    require(value == "nested-in-convention", { "rootConvention.nestedExtension" })
                    require(deepExtension == listOf("bazar", "cathedral"), { "rootConvention.nestedExtension.deepExtension" })
                }
            }
            """
        )

        executer.expectDocumentedDeprecationWarning("The Project.getConvention() method has been deprecated. This is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_access_to_conventions")
        executer.expectDocumentedDeprecationWarning("The org.gradle.api.plugins.Convention type has been deprecated. This is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_access_to_conventions")

        build("help")
    }

    @Test
    fun `convention accessors honor HasPublicType`() {

        withDefaultSettingsIn("buildSrc")

        withBuildScriptIn(
            "buildSrc",
            """
            plugins {
                `kotlin-dsl`
            }

            gradlePlugin {
                plugins {
                    register("my-plugin") {
                        id = "my-plugin"
                        implementationClass = "plugins.MyPlugin"
                    }
                }
            }

            $repositoriesBlock
            """
        )

        withFile(
            "buildSrc/src/main/kotlin/plugins/MyPlugin.kt",
            """
            package plugins

            import org.gradle.api.*
            import org.gradle.api.plugins.*

            class MyPlugin : Plugin<Project> {
                override fun apply(project: Project): Unit = project.run {
                    convention.plugins.put("myConvention", MyPrivateConventionImpl())
                }
            }
            """
        )

        withFile(
            "buildSrc/src/main/kotlin/plugins/MyConvention.kt",
            """
            package plugins

            interface MyConvention

            internal
            class MyPrivateConventionImpl : MyConvention
            """
        )

        withBuildScript(
            """
            plugins {
                id("my-plugin")
            }

            inline fun <reified T> typeOf(t: T) = T::class.simpleName

            myConvention {
                println("Type of `myConvention` receiver is " + typeOf(this@myConvention))
            }
            """
        )

        executer.beforeExecute {
            it.expectDocumentedDeprecationWarning("The Project.getConvention() method has been deprecated. This is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_access_to_conventions")
            it.expectDocumentedDeprecationWarning("The org.gradle.api.plugins.Convention type has been deprecated. This is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_access_to_conventions")
        }

        assertThat(
            build("help").output,
            containsString("Type of `myConvention` receiver is Any")
        )

        withFile(
            "buildSrc/src/main/kotlin/plugins/MyConvention.kt",
            """
            package plugins

            import org.gradle.api.reflect.*
            import org.gradle.kotlin.dsl.*

            interface MyConvention

            internal
            class MyPrivateConventionImpl : MyConvention, HasPublicType {
                override fun getPublicType() = typeOf<MyConvention>()
            }
            """
        )

        assertThat(
            build("help").output,
            containsString("Type of `myConvention` receiver is MyConvention")
        )
    }

    @Test
    fun `accessors to existing configurations`() {

        withBuildScript(
            """
            plugins {
                java
            }

            configurations {
                runtimeOnly {
                    extendsFrom(implementation.get())
                }
            }

            configurations.implementation {
                resolutionStrategy {
                    failOnVersionConflict()
                }
            }
            """
        )

        build("help")
    }

    @Test
    fun `accessors to existing tasks`() {

        withBuildScript(
            """
            plugins {
                java
            }

            tasks.compileJava {
                options.isWarnings = true
            }

            tasks {
                val myCheck by registering {
                    dependsOn(testClasses)
                    doLast {
                        println(testClasses.get().description)
                    }
                }
                test {
                    dependsOn(myCheck)
                }
            }
            """
        )

        build("help")
    }

    @Test
    fun `accessors to existing source sets`() {

        withBuildScript(
            """
            plugins {
                java
            }

            sourceSets {
                main {
                    java {
                        srcDir("src/main/java-too")
                    }
                }
                val integTest by registering {
                    java.srcDir(file("src/integTest/java"))
                    resources.srcDir(file("src/integTest/resources"))
                    compileClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
                    runtimeClasspath += output + compileClasspath
                }
            }

            tasks {
                val integTest by registering(Test::class) {
                    description = "Runs the integration tests."
                    group = "verification"
                    testClassesDirs = sourceSets["integTest"].output.classesDirs
                    classpath = sourceSets["integTest"].runtimeClasspath
                    mustRunAfter(project.tasks.test)
                }

                check { dependsOn(integTest) }
            }

            """
        )

        build("help")
    }

    @Test
    fun `accessors to existing elements of extensions that are containers`() {

        withBuildScript(
            """
            plugins {
                distribution
            }

            distributions {
                main {
                    distributionBaseName.set("the-distro")
                }
            }
            """
        )

        build("help")
    }

    @Test
    fun `accessors to extensions of the dependency handler`() {

        withKotlinBuildSrc()
        withFile(
            "buildSrc/src/main/kotlin/Mine.kt",
            """
            open class Mine {
                val some = 19
                val more = 23
            }
            """
        )
        withFile(
            "buildSrc/src/main/kotlin/my-plugin.gradle.kts",
            """
            dependencies.extensions.create<Mine>("mine")
            """
        )

        withBuildScript(
            """
            plugins {
                `my-plugin`
            }

            dependencies {
                println(mine.some + project.dependencies.mine.more)
            }
            """.trimIndent()
        )

        build("help").apply {
            assertThat(output, containsString("42"))
        }
    }

    @Test
    fun `accessors to extensions of the repository handler`() {

        withKotlinBuildSrc()
        withFile(
            "buildSrc/src/main/kotlin/Mine.kt",
            """
            open class Mine {
                val some = 19
                val more = 23
            }
            """
        )
        withFile(
            "buildSrc/src/main/kotlin/my-plugin.gradle.kts",
            """
            (repositories as ExtensionAware).extensions.create<Mine>("mine")
            """
        )

        withBuildScript(
            """
            plugins {
                `my-plugin`
            }

            repositories {
                println(mine.some + project.repositories.mine.more)
            }
            """.trimIndent()
        )

        build("help").apply {
            assertThat(output, containsString("42"))
        }
    }

    @Test
    fun `can access project extension of nested type compiled to Java 11`() {

        assumeJava11OrHigher()

        withFolders {
            "buildSrc" {
                "src/main/java/build" {
                    withFile(
                        "Java11Plugin.java",
                        """
                        package build;

                        import org.gradle.api.*;

                        public class Java11Plugin implements Plugin<Project> {

                            public static class Java11Extension {}

                            @Override public void apply(Project project) {
                                project.getExtensions().create("java11", Java11Extension.class);
                            }
                        }
                        """
                    )
                }
                withFile("settings.gradle.kts")
                withFile(
                    "build.gradle.kts",
                    """
                    plugins {
                        `java-library`
                        `java-gradle-plugin`
                    }

                    java {
                        sourceCompatibility = JavaVersion.VERSION_11
                        targetCompatibility = JavaVersion.VERSION_11
                    }

                    gradlePlugin {
                        plugins {
                            register("java11") {
                                id = "java11"
                                implementationClass = "build.Java11Plugin"
                            }
                        }
                    }
                    """
                )
            }
        }

        withBuildScript(
            """
            plugins { id("java11") }

            java11 { println(this.javaClass.name) }
            """
        )

        assertThat(
            build("-q").output,
            containsString("build.Java11Plugin${'$'}Java11Extension")
        )
    }

    @Test
    fun `accessors to kotlin internal task types are typed with the first kotlin public parent type`() {

        withDefaultSettings()
        withKotlinBuildSrc()
        withFile(
            "buildSrc/src/main/kotlin/my/CustomTasks.kt",
            """
            package my

            import org.gradle.api.*

            abstract class MyCustomTask : DefaultTask()
            internal open class MyCustomTaskImpl : MyCustomTask()

            internal open class MyOtherInternalTask : DefaultTask()
            """
        )
        withFile(
            "buildSrc/src/main/kotlin/my/custom.gradle.kts",
            """
            package my

            tasks.register<MyCustomTaskImpl>("custom")
            tasks.register<MyOtherInternalTask>("other")
            """
        )

        withBuildScript(
            """
            plugins {
                my.custom
            }

            inline fun <reified T> typeOf(value: T) = typeOf<T>()

            println("tasks.custom: " + typeOf(tasks.custom))
            tasks.custom { println("tasks.custom{}: " + typeOf(this)) }

            println("tasks.other: " + typeOf(tasks.other))
            tasks.other { println("tasks.other{}: " + typeOf(this)) }
            """
        )

        assertThat(
            build("custom", "other", "-q").output,
            containsMultiLineString(
                """
                tasks.custom: org.gradle.api.tasks.TaskProvider<my.MyCustomTask>
                tasks.other: org.gradle.api.tasks.TaskProvider<org.gradle.api.DefaultTask>
                tasks.custom{}: my.MyCustomTask
                tasks.other{}: org.gradle.api.DefaultTask
                """
            )
        )
    }

    private
    fun withBuildSrc(contents: FoldersDslExpression) {
        withFolders {
            "buildSrc" {
                withKotlinDslPlugin()
                contents()
            }
        }
    }
}
