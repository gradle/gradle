/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.resolve.catalog

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.test.fixtures.file.LeaksFileHandles
import spock.lang.Issue

/**
 * This test isn't meant to check the behavior of the extension generation like the other
 * integration tests in this package, but only what is very specific to the Kotlin DSL.
 * Because it requires the generated Gradle API it runs significantly slower than the other
 * tests so avoid adding tests here if they cannot be expressed with the Groovy DSL.
 */
@LeaksFileHandles("Kotlin Compiler Daemon working directory")
class KotlinDslVersionCatalogExtensionIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def setup() {
        settingsKotlinFile << """
            rootProject.name = "test"
        """
        settingsKotlinFile << """
            dependencyResolutionManagement {
                repositories {
                    maven {
                        setUrl("${mavenHttpRepo.uri}")
                    }
                }
            }
        """
    }

    @UnsupportedWithConfigurationCache(because = "test uses project state directly")
    def "can override version of a library via an extension method"() {
        def lib = mavenHttpRepo.module('org.gradle.test', 'lib', '1.1').publish()
        settingsKotlinFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    create("libs") {
                        library("my-lib", "org.gradle.test:lib:1.0")
                    }
                }
            }
        """
        buildKotlinFile << """
            plugins {
                `java-library`
            }

            dependencies {
                implementation(libs.my.lib) {
                    version {
                        strictly("1.1")
                    }
                }
            }

            tasks.register("checkDeps") {
                inputs.files(configurations.compileClasspath)
                doLast {
                    val fileNames = configurations.compileClasspath.files.map(File::getName)
                    assert(fileNames == listOf("lib-1.1.jar"))
                }
            }
        """

        when:
        lib.pom.expectGet()
        lib.artifact.expectGet()

        then:
        succeeds ':checkDeps'
    }

    @Issue("https://github.com/gradle/gradle/issues/15382")
    @LeaksFileHandles("Kotlin Compiler Daemon working directory")
    @ToBeFixedForConfigurationCache
    def "can add a dependency in a project via a precompiled script plugin"() {
        settingsKotlinFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    create("libs") {
                        library("lib", "org:test:1.0")
                    }
                }
            }
        """
        file("buildSrc/build.gradle.kts") << """
            plugins {
                `kotlin-dsl`
            }

            repositories {
                gradlePluginPortal()
            }
        """
        file("buildSrc/src/main/kotlin/my.plugin.gradle.kts") << """
            pluginManager.withPlugin("java") {
                val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
                dependencies.addProvider("implementation", libs.findLibrary("lib").get())
            }
        """

        buildKotlinFile << """
            plugins {
                `java-library`
                id("my.plugin")
            }

            tasks.register("checkDeps") {
                inputs.files(configurations.compileClasspath)
                doLast {
                    val fileNames = configurations.compileClasspath.files.map(File::getName)
                    assert(fileNames == listOf("test-1.0.jar"))
                }
            }
        """

        def lib = mavenHttpRepo.module('org', 'test', '1.0').publish()

        when:
        lib.pom.expectGet()
        lib.artifact.expectGet()

        then:
        succeeds ':checkDeps'
    }

    @Issue("https://github.com/gradle/gradle/issues/15350")
    def "provides Configuration.invoke method supporting provider"() {
        def lib = mavenHttpRepo.module('org.gradle.test', 'lib', '1.1').publish()
        def lib2 = mavenHttpRepo.module('org.gradle.test', 'lib2', '1.1').publish()
        settingsKotlinFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    create("libs") {
                        library("my-lib", "org.gradle.test:lib:1.1")
                        library("my-lib2", "org.gradle.test:lib2:1.1")
                    }
                }
            }
        """
        withCheckDeps()
        buildKotlinFile << """
            plugins {
                `java-library`
            }

            val custom by configurations.creating {
                configurations.implementation.get().extendsFrom(this)
            }
            dependencies {
                custom(libs.my.lib)
                custom(libs.my.lib2) {
                    because("Some comment why we need this dependency")
                }
            }

            tasks.register<CheckDeps>("checkDeps") {
                files.from(configurations.compileClasspath)
                expected.set(listOf("lib-1.1.jar", "lib2-1.1.jar"))
            }
        """

        when:
        lib.pom.expectGet()
        lib.artifact.expectGet()
        lib2.pom.expectGet()
        lib2.artifact.expectGet()

        then:
        succeeds ':checkDeps'
    }

    @Issue("https://github.com/gradle/gradle/issues/15350")
    def "provides String.invoke method supporting provider"() {
        def lib = mavenHttpRepo.module('org.gradle.test', 'lib', '1.1').publish()
        def lib2 = mavenHttpRepo.module('org.gradle.test', 'lib2', '1.1').publish()
        settingsKotlinFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    create("libs") {
                        library("my-lib", "org.gradle.test:lib:1.1")
                        library("my-lib2", "org.gradle.test:lib2:1.1")
                    }
                }
            }
        """

        withCheckDeps()

        buildKotlinFile << """
            plugins {
                `java-library`
            }

            val custom by configurations.creating {
                configurations.implementation.get().extendsFrom(this)
            }
            dependencies {
                "custom"(libs.my.lib)
                "custom"(libs.my.lib2) {
                    because("Some comment why we need this dependency")
                }
            }

            tasks.register<CheckDeps>("checkDeps") {
                files.from(configurations.compileClasspath)
                expected.set(listOf("lib-1.1.jar", "lib2-1.1.jar"))
            }
        """

        when:
        lib.pom.expectGet()
        lib.artifact.expectGet()
        lib2.pom.expectGet()
        lib2.artifact.expectGet()

        then:
        succeeds ':checkDeps'
    }

    @Issue("https://github.com/gradle/gradle/issues/18617")
    def "provides Configuration.invoke method supporting ProviderConvertible"() {
        def lib = mavenHttpRepo.module('org.gradle.test', 'lib', '1.1').publish()
        settingsKotlinFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    create("libs") {
                        library("my-lib", "org.gradle.test:lib:1.1")
                        // Forces `my.lib` to be a ProviderConvertible, otherwise unused
                        library("my-lib-two", "org.gradle.test:lib2:1.1")
                    }
                }
            }
        """
        withCheckDeps()
        buildKotlinFile << """
            plugins {
                `java-library`
            }

            val custom by configurations.creating {
                configurations.implementation.get().extendsFrom(this)
            }
            dependencies {
                custom(libs.my.lib)
                custom(libs.my.lib) {
                    because("Some comment why we need this dependency")
                }
            }

            tasks.register<CheckDeps>("checkDeps") {
                files.from(configurations.compileClasspath)
                expected.set(listOf("lib-1.1.jar"))
            }
        """

        when:
        lib.pom.expectGet()
        lib.artifact.expectGet()

        then:
        succeeds ':checkDeps'
    }

    @Issue("https://github.com/gradle/gradle/issues/18617")
    def "provides String.invoke method supporting ProviderConvertible"() {
        def lib = mavenHttpRepo.module('org.gradle.test', 'lib', '1.1').publish()
        settingsKotlinFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    create("libs") {
                        library("my-lib", "org.gradle.test:lib:1.1")
                        // Forces `my.lib` to be a ProviderConvertible, otherwise unused
                        library("my-lib-two", "org.gradle.test:lib2:1.1")
                    }
                }
            }
        """

        withCheckDeps()

        buildKotlinFile << """
            plugins {
                `java-library`
            }

            val custom by configurations.creating {
                configurations.implementation.get().extendsFrom(this)
            }
            dependencies {
                "custom"(libs.my.lib)
                "custom"(libs.my.lib) {
                    because("Some comment why we need this dependency")
                }
            }

            tasks.register<CheckDeps>("checkDeps") {
                files.from(configurations.compileClasspath)
                expected.set(listOf("lib-1.1.jar"))
            }
        """

        when:
        lib.pom.expectGet()
        lib.artifact.expectGet()

        then:
        succeeds ':checkDeps'
    }

    @Issue("https://github.com/gradle/gradle/issues/17874")
    def "supports version catalogs in force method of resolutionStrategy"() {
        def lib = mavenHttpRepo.module('org.gradle.test', 'lib', '1.1').publish()
        def lib2 = mavenHttpRepo.module('org.gradle.test', 'lib2', '1.1').publish()
        settingsKotlinFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    create("libs") {
                        library("myLib", "org.gradle.test:lib:1.1")
                        library("myLib-subgroup", "org.gradle.test:lib2:1.1")
                    }
                }
            }
        """

        withCheckDeps()

        buildKotlinFile << """
            plugins {
                `java-library`
            }


            dependencies {
                implementation("org.gradle.test:lib:1.0")
                implementation("org.gradle.test:lib2:1.0")
                configurations.all {
                    resolutionStrategy {
                        force(libs.myLib)
                        force(libs.myLib.subgroup)
                    }
                }
            }

            tasks.register<CheckDeps>("checkDeps") {
                files.from(configurations.compileClasspath)
                expected.set(listOf("lib-1.1.jar", "lib2-1.1.jar"))
            }
        """

        when:
        lib.pom.expectGet()
        lib.artifact.expectGet()
        lib2.pom.expectGet()
        lib2.artifact.expectGet()

        then:
        succeeds ':checkDeps'
    }

    private void withCheckDeps() {
        buildKotlinFile << """
            abstract class CheckDeps: DefaultTask() {
                @get:InputFiles
                abstract val files: ConfigurableFileCollection

                @get:Input
                abstract val expected: ListProperty<String>

                @TaskAction
                fun verify() {
                    val fileNames = files.files.map(File::getName)
                    assert(fileNames == expected.get()) { "Expected \${expected.get()} but got \$fileNames" }
                }
            }
        """
    }
}
