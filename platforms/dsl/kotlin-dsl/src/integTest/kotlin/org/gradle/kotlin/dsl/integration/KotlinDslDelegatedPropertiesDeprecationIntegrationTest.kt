/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.util.GradleVersion
import org.junit.Test


/**
 * Verifies that all Kotlin DSL property delegate syntaxes emit deprecation warnings
 * with precise, variant-specific messages guiding users to their explicit replacements.
 */
class KotlinDslDelegatedPropertiesDeprecationIntegrationTest : AbstractKotlinIntegrationTest() {

    private
    fun expectDeprecation(feature: String, advice: String? = null) {
        executer.expectDocumentedDeprecationWarning(
            "$feature has been deprecated. " +
                "This is scheduled to be removed in Gradle 10. " +
                (if (advice != null) "$advice " else "") +
                "Consult the upgrading guide for further information: " +
                "https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_9.html#kotlin_dsl_delegated_properties"
        )
    }

    @Test
    fun `NamedDomainObjectContainer delegates emit deprecation warnings`() {
        withBuildScript(
            """
            @file:Suppress("DEPRECATION")

            configurations.register("e")
            configurations.register("f")
            configurations.register("g")
            configurations.register("h")

            val a by configurations.registering
            val b by configurations.registering { }
            val c by configurations.creating
            val d by configurations.creating { }
            val e by configurations.existing
            val f by configurations.existing { }
            val g by configurations.getting
            val h by configurations.getting { }
        """
        )

        expectDeprecation("The 'val name by registering' property delegate syntax", "Use 'val element = register(name)' instead.")
        expectDeprecation("The 'val name by registering { }' property delegate syntax", "Use 'val element = register(name) { }' instead.")
        expectDeprecation("The 'val name by creating' property delegate syntax", "Use 'val element = create(name)' instead.")
        expectDeprecation("The 'val name by creating { }' property delegate syntax", "Use 'val element = create(name) { }' instead.")
        expectDeprecation("The 'val name by existing' property delegate syntax", "Use 'val element = named(name)' instead.")
        expectDeprecation("The 'val name by existing { }' property delegate syntax", "Use 'val element = named(name) { }' instead.")
        expectDeprecation("The 'val name by getting' property delegate syntax", "Use 'val element = getByName(name)' instead.")
        expectDeprecation("The 'val name by getting { }' property delegate syntax", "Use 'val element = getByName(name) { }' instead.")

        build("help")

        withBuildScript(
            """
            @file:Suppress("DEPRECATION")

            configurations.register("e")
            configurations.register("f")
            configurations.register("g")
            configurations.register("h")

            configurations {
                val a by registering
                val b by registering { }
                val c by creating
                val d by creating { }
                val e by existing
                val f by existing { }
                val g by getting
                val h by getting { }
            }
        """
        )

        expectDeprecation("The 'val name by registering' property delegate syntax", "Use 'val element = register(name)' instead.")
        expectDeprecation("The 'val name by registering { }' property delegate syntax", "Use 'val element = register(name) { }' instead.")
        expectDeprecation("The 'val name by creating' property delegate syntax", "Use 'val element = create(name)' instead.")
        expectDeprecation("The 'val name by creating { }' property delegate syntax", "Use 'val element = create(name) { }' instead.")
        expectDeprecation("The 'val name by existing' property delegate syntax", "Use 'val element = named(name)' instead.")
        expectDeprecation("The 'val name by existing { }' property delegate syntax", "Use 'val element = named(name) { }' instead.")
        expectDeprecation("The 'val name by getting' property delegate syntax", "Use 'val element = getByName(name)' instead.")
        expectDeprecation("The 'val name by getting { }' property delegate syntax", "Use 'val element = getByName(name) { }' instead.")

        build("help")
    }

    @Test
    fun `PolymorphicDomainObjectContainer typed delegates emit deprecation warnings`() {
        withBuildScript(
            """
            @file:Suppress("DEPRECATION")

            plugins {
                java
                `jvm-test-suite`
            }

            testing.suites.register<JvmTestSuite>("e")
            testing.suites.register<JvmTestSuite>("f")
            testing.suites.register<JvmTestSuite>("g")
            testing.suites.register<JvmTestSuite>("h")
            testing.suites.register<JvmTestSuite>("i")
            testing.suites.register<JvmTestSuite>("j")
            testing.suites.register<JvmTestSuite>("k")

            val a by testing.suites.registering(JvmTestSuite::class)
            val b by testing.suites.registering(JvmTestSuite::class) { }
            val c by testing.suites.creating(JvmTestSuite::class)
            val d by testing.suites.creating(JvmTestSuite::class) { }
            val test by testing.suites.existing
            val e by testing.suites.existing { }
            val f by testing.suites.existing(JvmTestSuite::class)
            val g by testing.suites.existing(JvmTestSuite::class) { }
            val h by testing.suites.getting
            val i by testing.suites.getting { }
            val j by testing.suites.getting(JvmTestSuite::class)
            val k by testing.suites.getting(JvmTestSuite::class) { }
        """
        )

        expectDeprecation("The 'val name by registering(Type::class)' property delegate syntax", "Use 'val element = register<Type>(name)' instead.")
        expectDeprecation("The 'val name by registering(Type::class) { }' property delegate syntax", "Use 'val element = register<Type>(name) { }' instead.")
        expectDeprecation("The 'val name by creating(Type::class)' property delegate syntax", "Use 'val element = create<Type>(name)' instead.")
        expectDeprecation("The 'val name by creating(Type::class) { }' property delegate syntax", "Use 'val element = create<Type>(name) { }' instead.")
        expectDeprecation("The 'val name by existing' property delegate syntax", "Use 'val element = named(name)' instead.")
        expectDeprecation("The 'val name by existing { }' property delegate syntax", "Use 'val element = named(name) { }' instead.")
        expectDeprecation("The 'val name by existing(Type::class)' property delegate syntax", "Use 'val element = named<Type>(name)' instead.")
        expectDeprecation("The 'val name by existing(Type::class) { }' property delegate syntax", "Use 'val element = named<Type>(name) { }' instead.")
        expectDeprecation("The 'val name by getting' property delegate syntax", "Use 'val element = getByName(name)' instead.")
        expectDeprecation("The 'val name by getting { }' property delegate syntax", "Use 'val element = getByName(name) { }' instead.")
        expectDeprecation("The 'val name by getting(Type::class)' property delegate syntax", "Use 'val element = getByName<Type>(name)' instead.")
        expectDeprecation("The 'val name by getting(Type::class) { }' property delegate syntax", "Use 'val element = getByName<Type>(name) { }' instead.")

        build("help")

        withBuildScript(
            """
            @file:Suppress("DEPRECATION")

            plugins {
                java
                `jvm-test-suite`
            }

            testing {
                suites {
                    register<JvmTestSuite>("e")
                    register<JvmTestSuite>("f")
                    register<JvmTestSuite>("g")
                    register<JvmTestSuite>("h")
                    register<JvmTestSuite>("i")
                    register<JvmTestSuite>("j")
                    register<JvmTestSuite>("k")

                    val a by registering(JvmTestSuite::class)
                    val b by registering(JvmTestSuite::class) { }
                    val c by creating(JvmTestSuite::class)
                    val d by creating(JvmTestSuite::class) { }
                    val test by existing
                    val e by existing { }
                    val f by existing(JvmTestSuite::class)
                    val g by existing(JvmTestSuite::class) { }
                    val h by getting
                    val i by getting { }
                    val j by getting(JvmTestSuite::class)
                    val k by getting(JvmTestSuite::class) { }
                }
            }
        """
        )

        expectDeprecation("The 'val name by registering(Type::class)' property delegate syntax", "Use 'val element = register<Type>(name)' instead.")
        expectDeprecation("The 'val name by registering(Type::class) { }' property delegate syntax", "Use 'val element = register<Type>(name) { }' instead.")
        expectDeprecation("The 'val name by creating(Type::class)' property delegate syntax", "Use 'val element = create<Type>(name)' instead.")
        expectDeprecation("The 'val name by creating(Type::class) { }' property delegate syntax", "Use 'val element = create<Type>(name) { }' instead.")
        expectDeprecation("The 'val name by existing' property delegate syntax", "Use 'val element = named(name)' instead.")
        expectDeprecation("The 'val name by existing { }' property delegate syntax", "Use 'val element = named(name) { }' instead.")
        expectDeprecation("The 'val name by existing(Type::class)' property delegate syntax", "Use 'val element = named<Type>(name)' instead.")
        expectDeprecation("The 'val name by existing(Type::class) { }' property delegate syntax", "Use 'val element = named<Type>(name) { }' instead.")
        expectDeprecation("The 'val name by getting' property delegate syntax", "Use 'val element = getByName(name)' instead.")
        expectDeprecation("The 'val name by getting { }' property delegate syntax", "Use 'val element = getByName(name) { }' instead.")
        expectDeprecation("The 'val name by getting(Type::class)' property delegate syntax", "Use 'val element = getByName<Type>(name)' instead.")
        expectDeprecation("The 'val name by getting(Type::class) { }' property delegate syntax", "Use 'val element = getByName<Type>(name) { }' instead.")

        build("help")
    }

    @Test
    fun `NamedDomainObjectCollection delegates emit deprecation warnings`() {
        withBuildScript(
            """
            @file:Suppress("DEPRECATION")

            configurations.register("a")
            configurations.register("b")
            configurations.register("c")
            configurations.register("d")

            val collection: NamedDomainObjectCollection<Configuration> = configurations

            val a by collection.existing
            val b by collection.existing { }
            val c by collection.getting
            val d by collection.getting { }
        """
        )

        expectDeprecation("The 'val name by existing' property delegate syntax", "Use 'val element = named(name)' instead.")
        expectDeprecation("The 'val name by existing { }' property delegate syntax", "Use 'val element = named(name) { }' instead.")
        expectDeprecation("The 'val name by getting' property delegate syntax", "Use 'val element = getByName(name)' instead.")
        expectDeprecation("The 'val name by getting { }' property delegate syntax", "Use 'val element = getByName(name) { }' instead.")

        build("help")
    }

    @Test
    fun `TaskContainer delegates emit deprecation warnings`() {
        withBuildScript(
            """
            @file:Suppress("DEPRECATION")

            tasks.register("i")
            tasks.register("j")
            tasks.register("k")
            tasks.register("l")
            tasks.register("m")
            tasks.register("n")
            tasks.register("o")
            tasks.register("p")

            val a by tasks.registering
            val b by tasks.registering { }
            val c by tasks.registering(Copy::class)
            val d by tasks.registering(Copy::class) { }

            val e by tasks.creating
            val f by tasks.creating { }
            val g by tasks.creating(Copy::class)
            val h by tasks.creating(Copy::class) { }

            val i by tasks.existing
            val j by tasks.existing { }
            val k by tasks.existing(DefaultTask::class)
            val l by tasks.existing(DefaultTask::class) { }

            val m by tasks.getting
            val n by tasks.getting { }
            val o by tasks.getting(DefaultTask::class)
            val p by tasks.getting(DefaultTask::class) { }
        """
        )

        expectDeprecation("The 'val name by registering' property delegate syntax", "Use 'val element = register(name)' instead.")
        expectDeprecation("The 'val name by registering { }' property delegate syntax", "Use 'val element = register(name) { }' instead.")
        expectDeprecation("The 'val name by registering(Type::class)' property delegate syntax", "Use 'val element = register<Type>(name)' instead.")
        expectDeprecation("The 'val name by registering(Type::class) { }' property delegate syntax", "Use 'val element = register<Type>(name) { }' instead.")
        expectDeprecation("The 'val name by creating' property delegate syntax", "Use 'val element = create(name)' instead.")
        expectDeprecation("The 'val name by creating { }' property delegate syntax", "Use 'val element = create(name) { }' instead.")
        expectDeprecation("The 'val name by creating(Type::class)' property delegate syntax", "Use 'val element = create<Type>(name)' instead.")
        expectDeprecation("The 'val name by creating(Type::class) { }' property delegate syntax", "Use 'val element = create<Type>(name) { }' instead.")
        expectDeprecation("The 'val name by existing' property delegate syntax", "Use 'val element = named(name)' instead.")
        expectDeprecation("The 'val name by existing { }' property delegate syntax", "Use 'val element = named(name) { }' instead.")
        expectDeprecation("The 'val name by existing(Type::class)' property delegate syntax", "Use 'val element = named<Type>(name)' instead.")
        expectDeprecation("The 'val name by existing(Type::class) { }' property delegate syntax", "Use 'val element = named<Type>(name) { }' instead.")
        expectDeprecation("The 'val name by getting' property delegate syntax", "Use 'val element = getByName(name)' instead.")
        expectDeprecation("The 'val name by getting { }' property delegate syntax", "Use 'val element = getByName(name) { }' instead.")
        expectDeprecation("The 'val name by getting(Type::class)' property delegate syntax", "Use 'val element = getByName<Type>(name)' instead.")
        expectDeprecation("The 'val name by getting(Type::class) { }' property delegate syntax", "Use 'val element = getByName<Type>(name) { }' instead.")

        build("help")

        withBuildScript(
            """
            @file:Suppress("DEPRECATION")

            tasks.register("i")
            tasks.register("j")
            tasks.register("k")
            tasks.register("l")
            tasks.register("m")
            tasks.register("n")
            tasks.register("o")
            tasks.register("p")

            tasks {
                val a by registering
                val b by registering { }
                val c by registering(Copy::class)
                val d by registering(Copy::class) { }
                val e by creating
                val f by creating { }
                val g by creating(Copy::class)
                val h by creating(Copy::class) { }
                val i by existing
                val j by existing { }
                val k by existing(DefaultTask::class)
                val l by existing(DefaultTask::class) { }
                val m by getting
                val n by getting { }
                val o by getting(DefaultTask::class)
                val p by getting(DefaultTask::class) { }
            }
        """
        )

        expectDeprecation("The 'val name by registering' property delegate syntax", "Use 'val element = register(name)' instead.")
        expectDeprecation("The 'val name by registering { }' property delegate syntax", "Use 'val element = register(name) { }' instead.")
        expectDeprecation("The 'val name by registering(Type::class)' property delegate syntax", "Use 'val element = register<Type>(name)' instead.")
        expectDeprecation("The 'val name by registering(Type::class) { }' property delegate syntax", "Use 'val element = register<Type>(name) { }' instead.")
        expectDeprecation("The 'val name by creating' property delegate syntax", "Use 'val element = create(name)' instead.")
        expectDeprecation("The 'val name by creating { }' property delegate syntax", "Use 'val element = create(name) { }' instead.")
        expectDeprecation("The 'val name by creating(Type::class)' property delegate syntax", "Use 'val element = create<Type>(name)' instead.")
        expectDeprecation("The 'val name by creating(Type::class) { }' property delegate syntax", "Use 'val element = create<Type>(name) { }' instead.")
        expectDeprecation("The 'val name by existing' property delegate syntax", "Use 'val element = named(name)' instead.")
        expectDeprecation("The 'val name by existing { }' property delegate syntax", "Use 'val element = named(name) { }' instead.")
        expectDeprecation("The 'val name by existing(Type::class)' property delegate syntax", "Use 'val element = named<Type>(name)' instead.")
        expectDeprecation("The 'val name by existing(Type::class) { }' property delegate syntax", "Use 'val element = named<Type>(name) { }' instead.")
        expectDeprecation("The 'val name by getting' property delegate syntax", "Use 'val element = getByName(name)' instead.")
        expectDeprecation("The 'val name by getting { }' property delegate syntax", "Use 'val element = getByName(name) { }' instead.")
        expectDeprecation("The 'val name by getting(Type::class)' property delegate syntax", "Use 'val element = getByName<Type>(name)' instead.")
        expectDeprecation("The 'val name by getting(Type::class) { }' property delegate syntax", "Use 'val element = getByName<Type>(name) { }' instead.")

        build("help")
    }

    @Test
    fun `collection-level delegates emit deprecation warnings`() {
        withBuildScript(
            """
            @file:Suppress("DEPRECATION")

            configurations.register("myConf")

            val myConf: Configuration by configurations
            println(myConf.name)

            val provider = configurations.named("myConf")
            val myConfFromProvider: Configuration by provider
            println(myConfFromProvider.name)
        """
        )

        expectDeprecation("The 'val name by container' property delegate syntax", "Use 'val element = getByName(name)' instead.")
        repeat(2) { expectDeprecation("The 'val name by provider' property delegate syntax", "Use 'val value = provider.get()' instead.") }

        build("help")
    }

    @Test
    fun `project property delegates emit deprecation warnings`() {
        withBuildScript(
            """
            @file:Suppress("DEPRECATION")

            val myProp: String by project
            val myNullableProp: String? by project
        """
        )

        file("gradle.properties").writeText("myProp=value")

        expectDeprecation("The 'val name: Type by project' property delegate syntax", "Use 'val property = project.property(name)' instead.")
        expectDeprecation("The 'val name: Type? by project' property delegate syntax", "Use 'val property = project.findProperty(name)' instead.")

        build("help")
    }

    @Test
    fun `settings property delegate emits deprecation warning`() {
        withSettings(
            """
            @file:Suppress("DEPRECATION")

            val myProp: String by settings
            val myNullableProp: String? by settings
        """
        )

        file("gradle.properties").writeText("myProp=value")

        expectDeprecation(
            "The 'val name: Type by settings' property delegate syntax",
            "Use 'val property = providers.gradleProperty(name).get()' for Gradle properties or 'val property = extra[name] as Type' for extra properties instead."
        )
        expectDeprecation(
            "The 'val name: Type? by settings' property delegate syntax",
            "Use 'val property = providers.gradleProperty(name).orNull' for Gradle properties or 'extra[name] as Type?' for extra properties instead."
        )

        build("help")
    }

    @Test
    fun `extra property delegates emit deprecation warnings`() {
        withBuildScript(
            """
            @file:Suppress("DEPRECATION")

            var a by extra("value")
            var b by extra { "computed" }
            val c: String by extra
            val d: String? by extra

            a = "new value"
            b = "new computed"
        """
        )

        repeat(2) { expectDeprecation("The 'val name by extra(...)' or 'val name by extra { ... }' property delegate syntax", "Use 'extra.set(name, value)' instead.") }
        expectDeprecation("The 'val name: Type by extra' property delegate syntax", "Use 'val property = extra[name] as Type' instead.")
        expectDeprecation("The 'val name: Type? by extra' property delegate syntax", "Use 'val property = extra[name] as Type?' instead.")

        build("help")
    }

    @Test
    fun `Property value delegates emit deprecation warnings`() {
        withBuildScript(
            """
            @file:Suppress("DEPRECATION")

            val myProp = objects.property<String>()
            myProp.set("hello")

            val readValue: String by myProp
            println(readValue)

            val writableProp = objects.property<String>()
            writableProp.set("initial")
            var writableValue: String by writableProp
            writableValue = "updated"
        """
        )

        expectDeprecation("The 'val value by property' property delegate syntax", "Use 'val value = property.get()' instead.")
        expectDeprecation("The 'var name by property; name = value' property delegate syntax", "Use 'property.set(value)' instead.")

        build("help")
    }

    @Test
    fun `ConfigurableFileCollection value delegates emit deprecation warnings`() {
        withBuildScript(
            """
            @file:Suppress("DEPRECATION")

            val fc = files()
            fc.from("a.txt")

            var myFiles by fc
            println(myFiles)
            myFiles = files("b.txt")
        """
        )

        expectDeprecation("The 'val files by configurableFileCollection' property delegate syntax", "Use 'val files = configurableFileCollection.getFiles()' instead.")
        expectDeprecation("The 'val files by configurableFileCollection; files = ...' property delegate syntax", "Use 'configurableFileCollection.setFrom(...)' instead.")

        build("help")
    }

    @Test
    fun `ExtensionContainer delegate emits deprecation warning`() {
        withBuildScript(
            """
            @file:Suppress("DEPRECATION")

            plugins {
                java
            }

            val java: JavaPluginExtension by extensions

            println(java.sourceCompatibility)
        """
        )

        expectDeprecation("The 'val name: Type by extensions' property delegate syntax", "Use 'val extension = extensions.getByType<Type>()' instead.")

        build("help")
    }

    @Test
    fun `direct use of delegate provider factory methods emits deprecation warnings`() {
        withBuildScript(
            """
            // Direct factory calls, bypassing the entry-point functions
            val rdp = RegisteringDomainObjectDelegateProvider.of(configurations)
            val rdpa = RegisteringDomainObjectDelegateProviderWithAction.of<ConfigurationContainer, Configuration>(configurations) { }
            val rdpt = RegisteringDomainObjectDelegateProviderWithType.of(tasks, Copy::class)
            val rdpta = RegisteringDomainObjectDelegateProviderWithTypeAndAction.of(tasks, Copy::class) { }
            val edp = ExistingDomainObjectDelegateProvider.of(configurations)
            val edpa = ExistingDomainObjectDelegateProviderWithAction.of<ConfigurationContainer, Configuration>(configurations) { }
            val edpt = ExistingDomainObjectDelegateProviderWithType.of(tasks, Copy::class)
            val edpta = ExistingDomainObjectDelegateProviderWithTypeAndAction.of(tasks, Copy::class) { }
            val cdp = NamedDomainObjectContainerCreatingDelegateProvider.of(configurations)
            val pcdp = PolymorphicDomainObjectContainerCreatingDelegateProvider.of(tasks, Copy::class.java)
            val pgdp = PolymorphicDomainObjectContainerGettingDelegateProvider.of(configurations, Configuration::class)
            val ndocdp = NamedDomainObjectCollectionDelegateProvider.of(configurations)
            val edd = ExistingDomainObjectDelegate.of("value")
            val ivdp = InitialValueExtraPropertyDelegateProvider.of(extra, "value")
            val ivd = InitialValueExtraPropertyDelegate.of<String>(extra)
        """
        )

        expectDeprecation("The org.gradle.kotlin.dsl.RegisteringDomainObjectDelegateProvider type")
        expectDeprecation("The org.gradle.kotlin.dsl.RegisteringDomainObjectDelegateProviderWithAction type")
        expectDeprecation("The org.gradle.kotlin.dsl.RegisteringDomainObjectDelegateProviderWithType type")
        expectDeprecation("The org.gradle.kotlin.dsl.RegisteringDomainObjectDelegateProviderWithTypeAndAction type")
        expectDeprecation("The org.gradle.kotlin.dsl.ExistingDomainObjectDelegateProvider type")
        expectDeprecation("The org.gradle.kotlin.dsl.ExistingDomainObjectDelegateProviderWithAction type")
        expectDeprecation("The org.gradle.kotlin.dsl.ExistingDomainObjectDelegateProviderWithType type")
        expectDeprecation("The org.gradle.kotlin.dsl.ExistingDomainObjectDelegateProviderWithTypeAndAction type")
        expectDeprecation("The org.gradle.kotlin.dsl.NamedDomainObjectContainerCreatingDelegateProvider type")
        expectDeprecation("The org.gradle.kotlin.dsl.PolymorphicDomainObjectContainerCreatingDelegateProvider type")
        expectDeprecation("The org.gradle.kotlin.dsl.PolymorphicDomainObjectContainerGettingDelegateProvider type")
        expectDeprecation("The org.gradle.kotlin.dsl.NamedDomainObjectCollectionDelegateProvider type")
        expectDeprecation("The org.gradle.kotlin.dsl.ExistingDomainObjectDelegate type")
        expectDeprecation("The org.gradle.kotlin.dsl.InitialValueExtraPropertyDelegateProvider type")
        expectDeprecation("The org.gradle.kotlin.dsl.InitialValueExtraPropertyDelegate type")

        build("help")
    }

}
