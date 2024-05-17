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
                    maven(url = "${mavenHttpRepo.uri}")
                }
            }
        """
    }

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
                val classpath: FileCollection = configurations.compileClasspath.get()
                inputs.files(classpath)
                doLast {
                    val fileNames = classpath.files.map(File::getName)
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
                val libs = the<VersionCatalogsExtension>().named("libs")
                dependencies.addProvider("implementation", libs.findLibrary("lib").get())
            }
        """

        buildKotlinFile << """
            plugins {
                `java-library`
                id("my.plugin")
            }

            tasks.register("checkDeps") {
                val classpath: FileCollection = configurations.compileClasspath.get()
                inputs.files(classpath)
                doLast {
                    val fileNames = classpath.files.map(File::getName)
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


    @Issue("https://github.com/gradle/gradle/issues/22650")
    def "can use the generated extension to declare a dependency constraint with and without sub-group using bundles"() {
        settingsKotlinFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    register("libs") {
                        version("myLib") {
                            strictly("[1.0,1.1)")
                        }
                        library("myLib", "org.gradle.test", "lib-core").versionRef("myLib")
                        library("myLib-ext", "org.gradle.test", "lib-ext").versionRef("myLib")
                        bundle("myBundle", listOf("myLib"))
                        bundle("myBundle-ext", listOf("myLib-ext"))
                    }
                }
            }
        """
        def publishLib = { String artifactId, String version ->
            def lib = mavenHttpRepo.module("org.gradle.test", artifactId, version)
                .withModuleMetadata()
                .publish()
            lib.moduleMetadata.expectGet()
            lib.pom.expectGet()
            return lib
        }
        publishLib("lib-core", "1.0").with {
            it.rootMetaData.expectGet()
            it.artifact.expectGet()
        }
        publishLib("lib-core", "1.1")
        publishLib("lib-ext", "1.0").with {
            it.rootMetaData.expectGet()
            it.artifact.expectGet()
        }

        withCheckDeps()
        buildKotlinFile << """
            plugins {
                `java-library`
            }

            dependencies {
                implementation("org.gradle.test:lib-core:1.+") // intentional!
                implementation("org.gradle.test:lib-ext") // intentional!
                constraints {
                    implementation(libs.bundles.myBundle)
                    implementation(libs.bundles.myBundle.ext)
                }
            }

            tasks.register<CheckDeps>("checkDeps") {
                files.from(configurations.compileClasspath)
                expected.set(listOf("lib-core-1.0.jar", "lib-ext-1.0.jar"))
            }
            // Might be worth checking constraints too? Not sure if necessary because the Groovy DSL version covers that
            // and the selected versions above would be wrong.
        """

        expect:
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

    def "no name conflicting accessors of different catalogs"() {
        def libA = mavenHttpRepo.module("com.company","libs-a").publish()
        def libB = mavenHttpRepo.module("com.companylibs","libs-b").publish()
        settingsKotlinFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    create("libs") {
                        library("com-company-libs-a", "com.company:libs-a:1.0")
                    }

                    create("moreLibs") {
                        library("com-companylibs-b", "com.companylibs:libs-b:1.0")
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
                implementation(libs.com.company.libs.a)
                implementation(moreLibs.com.companylibs.b)
            }

            tasks.register<CheckDeps>("checkDeps") {
                files.from(configurations.compileClasspath)
                expected.set(listOf("libs-a-1.0.jar", "libs-b-1.0.jar"))
            }
        """

        when:
        libA.pom.expectGet()
        libA.artifact.expectGet()
        libB.pom.expectGet()
        libB.artifact.expectGet()

        then:
        succeeds ':checkDeps'
    }

    @Issue("https://github.com/gradle/gradle/issues/24426")
    def "can use version catalogs in buildscript block of applied script"() {

        given:
        def lib = mavenHttpRepo.module('org.gradle.test', 'lib', '1.1').publish()
        settingsKotlinFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    create("libs") {
                        library("my-lib", "org.gradle.test:lib:1.1")
                    }
                }
            }
        """
        buildKotlinFile << """
            apply(from = "applied.gradle.kts")
        """
        file("applied.gradle.kts") << """
            buildscript {
                dependencies {
                    classpath(libs.my.lib)
                }
                repositories {
                    maven(url = "${mavenHttpRepo.uri}")
                }
            }
        """

        when:
        lib.pom.expectGet()
        lib.artifact.expectGet()

        then:
        succeeds ':help'
    }
}
