/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import kotlin.test.Test

class OptInAccessorsIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    fun `when put in buildSrc directly, accessors to plugin code are valid`() {
        withBuildSrcDependingOnAnotherPlugin(pluginCode = noopPlugin) // instead, we put the plugin source to buildSrc

        withFile("buildSrc/src/main/kotlin/SomeExperimentalApi.kt", """
            package com.example

            annotation class Foo

            enum class Bar { FOO, BAR }

            @RequiresOptIn("Some Experimental API", RequiresOptIn.Level.ERROR)
            annotation class SomeExperimentalApi(val foo: Foo, val bar: Bar, val i: Int, val s: String, vararg val a: Foo)
        """.trimIndent())

        withFile(
            "buildSrc/src/main/kotlin/myPlugin.gradle.kts",
            """
                import com.example.*

                @SomeExperimentalApi(Foo(), Bar.BAR, 42, "some-string", a = [Foo(), Foo()])
                abstract class SomeExtension

                @OptIn(SomeExperimentalApi::class)
                extensions.create("someExtension", SomeExtension::class.java)
            """.trimIndent()
        )

        withBuildScript(
            """
                import com.example.SomeExperimentalApi

                plugins {
                    myPlugin
                }

                // opt-in needed here
                someExtension {
                    println(this)
                }
            """.trimIndent()
        )

        buildAndFail().assertHasErrorOutput("Some Experimental API")

        file("build.gradle.kts").run {
            text = text.replace("// opt-in needed here", "@OptIn(SomeExperimentalApi::class)")
        }

        build()
    }


    @Test
    fun `copies simple opt-in annotations from plugins to accessors`() {
        withBuildSrcDependingOnAnotherPlugin(
            pluginCode = pluginAddingAnExtensionWithOptInAnnotations(
                """
                    @RequiresOptIn("Some Experimental API", RequiresOptIn.Level.ERROR)
                    annotation class SomeExperimentalApi
                """.trimIndent(), """
                    @SomeExperimentalApi
                """.trimIndent(), """
                    @OptIn(SomeExperimentalApi::class)
                """.trimIndent()
            )
        )

        hasValidAccessorSource(
            """
                /**
                 * Retrieves the [someExtension][com.example.SomeExtension] extension.
                 */
                @com.example.SomeExperimentalApi
                internal
                val org.gradle.api.Project.`someExtension`: com.example.SomeExtension get() =
                    (this as org.gradle.api.plugins.ExtensionAware).extensions.getByName("someExtension") as com.example.SomeExtension

                /**
                 * Configures the [someExtension][com.example.SomeExtension] extension.
                 */
                @com.example.SomeExperimentalApi
                internal
                fun org.gradle.api.Project.`someExtension`(configure: Action<com.example.SomeExtension>): Unit =
                    (this as org.gradle.api.plugins.ExtensionAware).extensions.configure("someExtension", configure)
            """.trimIndent()
        )

    }

    @Test
    fun `copies annotations with values from plugins to accessors`() {
        withBuildSrcDependingOnAnotherPlugin(
            pluginCode = pluginAddingAnExtensionWithOptInAnnotations(
                """
                    enum class Foo { FOO, BAR }

                    @RequiresOptIn("Some Experimental API", RequiresOptIn.Level.ERROR)
                    annotation class SomeExperimentalApi(val someString: String, val someInt: Int, val foo: Foo)
                """.trimIndent(), """
                    @SomeExperimentalApi("some-string", 42, Foo.FOO)
                """.trimIndent(), """
                    @OptIn(SomeExperimentalApi::class)
                """.trimIndent()
            )
        )

        hasValidAccessorSource(
            """
                /**
                 * Retrieves the [ext][org.gradle.api.plugins.ExtraPropertiesExtension] extension.
                 */
                @com.example.SomeExperimentalApi(foo = com.example.Foo.FOO, someInt = 42, someString = "some-string")
                internal
                val com.example.SomeExtension.`ext`: org.gradle.api.plugins.ExtraPropertiesExtension get() =
                    (this as org.gradle.api.plugins.ExtensionAware).extensions.getByName("ext") as org.gradle.api.plugins.ExtraPropertiesExtension

                /**
                 * Configures the [ext][org.gradle.api.plugins.ExtraPropertiesExtension] extension.
                 */
                @com.example.SomeExperimentalApi(foo = com.example.Foo.FOO, someInt = 42, someString = "some-string")
                internal
                fun com.example.SomeExtension.`ext`(configure: Action<org.gradle.api.plugins.ExtraPropertiesExtension>): Unit =
                    (this as org.gradle.api.plugins.ExtensionAware).extensions.configure("ext", configure)
            """.trimIndent()
        )
    }


    @Test
    fun `when the opt-in requirement annotation appears on multiple types used in the annotation, copies just one opt-in requirement annotation`() {
        withBuildSrcDependingOnAnotherPlugin(
            pluginCode = pluginAddingAnExtensionWithOptInAnnotations(
                """
                    @RequiresOptIn("Other Experimental API", RequiresOptIn.Level.ERROR)
                    annotation class OtherExperimentalApi(val someString: String)

                    @OtherExperimentalApi("foo")
                    annotation class Foo

                    @OtherExperimentalApi("bar")
                    annotation class Bar

                    @OptIn(OtherExperimentalApi::class)
                    @RequiresOptIn("Some Experimental API", RequiresOptIn.Level.ERROR)
                    annotation class SomeExperimentalApi(val foo: Foo, val bar: Bar)
                """.trimIndent(), """
                    @OptIn(OtherExperimentalApi::class)
                    @SomeExperimentalApi(Foo(), Bar())
                """.trimIndent(), """
                    @OptIn(SomeExperimentalApi::class, OtherExperimentalApi::class)
                """.trimIndent()
            )
        )

        hasValidAccessorSource(
            """
                /**
                 * Retrieves the [someExtension][com.example.SomeExtension] extension.
                 */
                @com.example.OtherExperimentalApi(someString = "bar")
                @com.example.SomeExperimentalApi(bar = com.example.Bar(), foo = com.example.Foo())
                internal
                val org.gradle.api.Project.`someExtension`: com.example.SomeExtension get() =
                    (this as org.gradle.api.plugins.ExtensionAware).extensions.getByName("someExtension") as com.example.SomeExtension

                /**
                 * Configures the [someExtension][com.example.SomeExtension] extension.
                 */
                @com.example.OtherExperimentalApi(someString = "bar")
                @com.example.SomeExperimentalApi(bar = com.example.Bar(), foo = com.example.Foo())
                internal
                fun org.gradle.api.Project.`someExtension`(configure: Action<com.example.SomeExtension>): Unit =
                    (this as org.gradle.api.plugins.ExtensionAware).extensions.configure("someExtension", configure)
            """.trimIndent()
        )
    }


    @Test
    fun `copies annotations with annotation values from plugins to accessors`() {
        withBuildSrcDependingOnAnotherPlugin(
            pluginCode = pluginAddingAnExtensionWithOptInAnnotations(
                """
                    annotation class OtherArg(val id: Int)
                    annotation class ElementArg(val id: Int)

                    annotation class Arg(val argValue: String, val otherArg: OtherArg, vararg val elementArgs: ElementArg)

                    @RequiresOptIn("Some Experimental API", RequiresOptIn.Level.ERROR)
                    annotation class SomeExperimentalApi(val someString: String, val arg: Arg, vararg val elementArg: ElementArg)
                """.trimIndent(), """
                    @SomeExperimentalApi("some-string", Arg("arg-value", OtherArg(42), ElementArg(1), ElementArg(2)), elementArg = [ElementArg(3), ElementArg(4)])
                """.trimIndent(), """
                    @OptIn(SomeExperimentalApi::class)
                """.trimIndent()
            )
        )

        hasValidAccessorSource(
            """
                /**
                 * Retrieves the [someExtension][com.example.SomeExtension] extension.
                 */
                @com.example.SomeExperimentalApi(arg = com.example.Arg(argValue = "arg-value", elementArgs = [com.example.ElementArg(id = 1), com.example.ElementArg(id = 2)], otherArg = com.example.OtherArg(id = 42)), elementArg = [com.example.ElementArg(id = 3), com.example.ElementArg(id = 4)], someString = "some-string")
                internal
                val org.gradle.api.Project.`someExtension`: com.example.SomeExtension get() =
                    (this as org.gradle.api.plugins.ExtensionAware).extensions.getByName("someExtension") as com.example.SomeExtension

                /**
                 * Configures the [someExtension][com.example.SomeExtension] extension.
                 */
                @com.example.SomeExperimentalApi(arg = com.example.Arg(argValue = "arg-value", elementArgs = [com.example.ElementArg(id = 1), com.example.ElementArg(id = 2)], otherArg = com.example.OtherArg(id = 42)), elementArg = [com.example.ElementArg(id = 3), com.example.ElementArg(id = 4)], someString = "some-string")
                internal
                fun org.gradle.api.Project.`someExtension`(configure: Action<com.example.SomeExtension>): Unit =
                    (this as org.gradle.api.plugins.ExtensionAware).extensions.configure("someExtension", configure)
            """.trimIndent()
        )
    }


    @Test
    fun `if an annotation value requires a different opt-in, adds that to the accessor`() {
        withBuildSrcDependingOnAnotherPlugin(
            pluginCode = pluginAddingAnExtensionWithOptInAnnotations(
                """
                    @RequiresOptIn("More Experimental API", RequiresOptIn.Level.ERROR)
                    annotation class MoreExperimentalApi

                    @MoreExperimentalApi
                    annotation class Arg(val argValue: String)

                    @RequiresOptIn("Some Experimental API", RequiresOptIn.Level.ERROR)
                    @MoreExperimentalApi
                    annotation class SomeExperimentalApi(val someString: String, val arg: Arg)
                """.trimIndent(), """
                    @MoreExperimentalApi
                    @SomeExperimentalApi("some-string", Arg("arg-value"))
                """.trimIndent(), """
                    @OptIn(SomeExperimentalApi::class, MoreExperimentalApi::class)
                """.trimIndent()
            )
        )

        hasValidAccessorSource(
            """
                /**
                 * Retrieves the [someExtension][com.example.SomeExtension] extension.
                 */
                @com.example.MoreExperimentalApi
                @com.example.SomeExperimentalApi(arg = com.example.Arg(argValue = "arg-value"), someString = "some-string")
                internal
                val org.gradle.api.Project.`someExtension`: com.example.SomeExtension get() =
                    (this as org.gradle.api.plugins.ExtensionAware).extensions.getByName("someExtension") as com.example.SomeExtension

                /**
                 * Configures the [someExtension][com.example.SomeExtension] extension.
                 */
                @com.example.MoreExperimentalApi
                @com.example.SomeExperimentalApi(arg = com.example.Arg(argValue = "arg-value"), someString = "some-string")
                internal
                fun org.gradle.api.Project.`someExtension`(configure: Action<com.example.SomeExtension>): Unit =
                    (this as org.gradle.api.plugins.ExtensionAware).extensions.configure("someExtension", configure)
            """.trimIndent()
        )
    }

    @Test
    fun `for an inaccessible opt-in annotation, generates accessors to inaccessible type`() {
        withBuildSrcDependingOnAnotherPlugin(
            pluginAddingAnExtensionWithOptInAnnotations(
                """
                    annotation class Arg(val argValue: String) // this one is private, can't use it in the accessor source

                    @RequiresOptIn("Some Experimental API", RequiresOptIn.Level.ERROR)
                    internal annotation class SomeExperimentalApi(val arg: Arg)
                """.trimIndent(), """
                    @SomeExperimentalApi(Arg("arg-value"))
                """.trimIndent(), """
                    @OptIn(SomeExperimentalApi::class)
                """.trimIndent()
            )
        )

        hasValidAccessorSource(
            """
                /**
                 * Retrieves the `someExtension` extension.
                 *
                 * `someExtension` is not accessible in a type safe way because:
                 * - `com.example.SomeExperimentalApi` required for the opt-in is inaccessible
                 */
                internal
                val org.gradle.api.Project.`someExtension`: Any get() =
                    (this as org.gradle.api.plugins.ExtensionAware).extensions.getByName("someExtension")

                /**
                 * Configures the `someExtension` extension.
                 *
                 * `someExtension` is not accessible in a type safe way because:
                 * - `com.example.SomeExperimentalApi` required for the opt-in is inaccessible
                 */
                internal
                fun org.gradle.api.Project.`someExtension`(configure: Action<Any>): Unit =
                    (this as org.gradle.api.plugins.ExtensionAware).extensions.configure("someExtension", configure)
            """.trimIndent()
        )
    }

    @Test
    fun `when an opt-in annotation requires an argument of type that needs a private opt-in, reports that opt-in`() {
        withBuildSrcDependingOnAnotherPlugin(
            pluginAddingAnExtensionWithOptInAnnotations(
                """
                    @RequiresOptIn("Some Private API", RequiresOptIn.Level.ERROR)
                    private annotation class SomePrivateApi

                    @SomePrivateApi
                    annotation class Arg(val argValue: String)

                    @OptIn(SomePrivateApi::class)
                    @RequiresOptIn("Some Experimental API", RequiresOptIn.Level.ERROR)
                    annotation class SomeExperimentalApi(val arg: Arg)
                """.trimIndent(), """
                    @OptIn(SomePrivateApi::class)
                    @SomeExperimentalApi(Arg("arg-value"))
                """.trimIndent(), """
                    @OptIn(SomeExperimentalApi::class, SomePrivateApi::class)
                """.trimIndent()
            )
        )

        hasValidAccessorSource(
            """
                /**
                 * Retrieves the `someExtension` extension.
                 *
                 * `someExtension` is not accessible in a type safe way because:
                 * - `com.example.SomePrivateApi` required for the opt-in is inaccessible
                 */
                internal
                val org.gradle.api.Project.`someExtension`: Any get() =
                    (this as org.gradle.api.plugins.ExtensionAware).extensions.getByName("someExtension")

                /**
                 * Configures the `someExtension` extension.
                 *
                 * `someExtension` is not accessible in a type safe way because:
                 * - `com.example.SomePrivateApi` required for the opt-in is inaccessible
                 */
                internal
                fun org.gradle.api.Project.`someExtension`(configure: Action<Any>): Unit =
                    (this as org.gradle.api.plugins.ExtensionAware).extensions.configure("someExtension", configure)
            """.trimIndent()
        )
    }


    @Test
    fun `when the model type is nested, copies opt-in annotations from the outer types`() {
        withBuildSrcDependingOnAnotherPlugin(
            """
                package com.example

                import org.gradle.api.Plugin
                import org.gradle.api.Project

                @RequiresOptIn("Some Experimental API", RequiresOptIn.Level.ERROR)
                annotation class SomeExperimentalApi

                @SomeExperimentalApi
                class SomePlugin : Plugin<Project> {
                    abstract class SomeExtension

                    override fun apply(project: Project) {
                        @OptIn(SomeExperimentalApi::class)
                        project.extensions.create("someExtension", SomeExtension::class.java)
                    }
                }
            """.trimIndent()
        )

        hasValidAccessorSource(
            """
                /**
                 * Retrieves the [ext][org.gradle.api.plugins.ExtraPropertiesExtension] extension.
                 */
                @com.example.SomeExperimentalApi
                internal
                val com.example.SomePlugin.SomeExtension.`ext`: org.gradle.api.plugins.ExtraPropertiesExtension get() =
                    (this as org.gradle.api.plugins.ExtensionAware).extensions.getByName("ext") as org.gradle.api.plugins.ExtraPropertiesExtension

                /**
                 * Configures the [ext][org.gradle.api.plugins.ExtraPropertiesExtension] extension.
                 */
                @com.example.SomeExperimentalApi
                internal
                fun com.example.SomePlugin.SomeExtension.`ext`(configure: Action<org.gradle.api.plugins.ExtraPropertiesExtension>): Unit =
                    (this as org.gradle.api.plugins.ExtensionAware).extensions.configure("ext", configure)
            """.trimIndent()
        )
    }

    @Test
    fun `adds opt-in annotations to task accessors`() {
        withBuildSrcDependingOnAnotherPlugin(
            """
                package com.example

                import org.gradle.api.Plugin
                import org.gradle.api.Project
                import org.gradle.api.DefaultTask
                import org.gradle.api.tasks.TaskAction

                @RequiresOptIn("Some Experimental API", RequiresOptIn.Level.ERROR)
                annotation class SomeExperimentalApi

                @SomeExperimentalApi
                abstract class SomeTask : DefaultTask() {
                    @TaskAction
                    fun someAction() = Unit
                }

                class SomePlugin : Plugin<Project> {
                    override fun apply(project: Project) {
                        @OptIn(SomeExperimentalApi::class)
                        project.tasks.register("someTask", SomeTask::class.java)
                    }
                }
            """.trimIndent()
        )

        hasValidAccessorSource(
            """
                /**
                 * Provides the existing [someTask][com.example.SomeTask] task.
                 */
                @com.example.SomeExperimentalApi
                internal
                val TaskContainer.`someTask`: TaskProvider<com.example.SomeTask>
                    get() = named<com.example.SomeTask>("someTask")
            """.trimIndent()
        )

    }

    private fun hasValidAccessorSource(source: String) {
        build()
        assertThat(allAccessorSourcesForBuildSrc(), containsString(source))
    }

    private fun allAccessorSourcesForBuildSrc() =
        projectRoot.resolve("buildSrc/build/generated-sources/kotlin-dsl-accessors").walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }

    private fun pluginAddingAnExtensionWithOptInAnnotations(annotationSource: String, annotationUsage: String, optIn: String) =
        """
            package com.example

            import org.gradle.api.Plugin
            import org.gradle.api.Project

            $annotationSource

            $annotationUsage
            abstract class SomeExtension

            class SomePlugin : Plugin<Project> {
                override fun apply(project: Project) {
                    $optIn
                    project.extensions.create("someExtension", SomeExtension::class.java)
                }
            }
        """.trimIndent()


    private fun withBuildSrcDependingOnAnotherPlugin(
        pluginCode: String
    ) {
        withFile(
            "buildSrc/settings.gradle.kts",
            """
            include(":plugin")
            """.trimIndent()
        )

        withFile(
            "buildSrc/plugin/build.gradle.kts",
            """
            import org.jetbrains.kotlin.gradle.dsl.JvmTarget

            plugins {
                `java-gradle-plugin`
                kotlin("jvm")
            }

            kotlin {
                compilerOptions {
                    jvmTarget = JvmTarget.JVM_1_8
                }
            }

            tasks.compileJava {
                targetCompatibility = "1.8"
            }

            repositories {
                mavenCentral()
            }

            gradlePlugin {
                plugins {
                    create("simplePlugin") {
                        id = "some-plugin"
                        implementationClass = "com.example.SomePlugin"
                    }
                }
            }

            """.trimIndent()
        )

        withFile("buildSrc/plugin/src/main/kotlin/SomePlugin.kt", pluginCode)

        withFile(
            "buildSrc/src/main/kotlin/buildSrc-plugin.gradle.kts",
            """
            plugins {
                `some-plugin`
            }
            """.trimIndent()
        )

        withFile(
            "buildSrc/build.gradle.kts",
            """
            plugins {
                `kotlin-dsl`
            }

            repositories {
                gradlePluginPortal()
            }

            dependencies {
                implementation(project(":plugin"))
            }
            """.trimIndent()
        )

        withBuildScript(
            """
            plugins {
                id("java")
            }
        """.trimIndent()
        )
    }

    private val noopPlugin = """
        package com.example

        import org.gradle.api.Plugin
        import org.gradle.api.Project

        class SomePlugin : Plugin<Project> {
            override fun apply(project: Project) = Unit
        }
    """.trimIndent()
}
