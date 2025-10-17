/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.kotlin.dsl.plugins

import org.gradle.integtests.fixtures.CrossVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetVersions
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.internal.os.OperatingSystem
import org.gradle.util.GradleVersion

import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.mavenCentralRepository
import static org.gradle.test.fixtures.dsl.GradleDsl.KOTLIN
import static org.junit.Assume.assumeFalse
import static org.junit.Assume.assumeTrue

@TargetVersions("5.0+")
class ProjectTheExtensionCrossVersionSpec extends CrossVersionIntegrationSpec {

    def "can access extensions with current Gradle version from plugin built with Gradle 5.0+"() {

        def isFlaky = OperatingSystem.current().isWindows() &&
            previous.version >= GradleVersion.version("6.5") &&
            previous.version < GradleVersion.version("7.0")
        assumeFalse("Test is flaky on Windows with Gradle >= 6.5 and < 7.0 ", isFlaky)

        when:
        pluginBuiltWith(previous)

        then:
        pluginAppliedWith(current)
    }

    def "can access extensions with Gradle 9.0.0+ from plugin built with current Gradle version"() {

        // 9.0.0 is the first version that embeds Kotlin 2.2 and can execute code compiled for Kotlin 2.2
        assumeTrue(previous.version >= GradleVersion.version('9.0.0'))

        when:
        pluginBuiltWith(current)

        then:
        pluginAppliedWith(previous)
    }

    def "can access extensions with Gradle #minGradle+ from plugin built with current Gradle version targeting Kotlin #kotlinLanguageVersion"() {

        assumeTrue(previous.version >= GradleVersion.version(minGradle))

        when:
        pluginBuiltWith(current, "KOTLIN_${kotlinLanguageVersion.replace(".", "_")}")

        then:
        pluginAppliedWith(previous)

        where:
        minGradle | kotlinLanguageVersion
        "7.3"     | "1.8" // 6.8, but 7.3 is the first to support running on Java 17
        "7.3"     | "1.9" // 6.8, but 7.3 is the first to support running on Java 17
        "7.3"     | "2.0" // 6.8, but 7.3 is the first to support running on Java 17
        "8.11"    | "2.1"
    }

    private void pluginBuiltWith(GradleDistribution distribution, String kotlinVersion = null) {
        file("plugin/settings.gradle.kts").text = """
            println("Publishing plugin with ${'$'}{org.gradle.util.GradleVersion.current()}")
        """
        def pluginBuildScript = file("plugin/build.gradle.kts")
        pluginBuildScript.text = """
            plugins {
                `kotlin-dsl`
                `maven-publish`
            }
            group = "com.example"
            version = "1.0"
            ${mavenCentralRepository(KOTLIN)}
            publishing {
                repositories { maven { url = uri("${mavenRepo.uri}") } }
            }
        """
        if (kotlinVersion != null) {
            pluginBuildScript.text = """
                import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
                import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

                ${pluginBuildScript.text}

                tasks.withType<KotlinCompile>().configureEach {
                    compilerOptions {
                        languageVersion = KotlinVersion.$kotlinVersion
                        apiVersion = KotlinVersion.$kotlinVersion
                    }
                }
            """
        }
        file("plugin/src/main/kotlin/my-types.kt").text = """
            import org.gradle.api.provider.Property
            interface MyExtension { val some: Property<String> }
            interface Unregistered
        """
        file("plugin/src/main/kotlin/my-plugin.gradle.kts").text = """
            extensions.create<MyExtension>("myExtension")
            $usageCode
        """

        version(distribution)
            .inDirectory(file("plugin"))
            .withTasks("publish")
            .withArgument("-s")
            // The expected deprecations change too much between versions for checking deprecations to be worthwhile.
            .noDeprecationChecks()
            .run()
    }

    private void pluginAppliedWith(GradleDistribution distribution) {
        file("consumer/settings.gradle.kts").text = """
            pluginManagement {
                repositories { maven(url = "${mavenRepo.uri}") }
            }
            println("Applying plugin with ${'$'}{org.gradle.util.GradleVersion.current()}")
        """
        file("consumer/build.gradle.kts").text = """
            plugins {
                id("my-plugin") version "1.0"
            }
            tasks.register("myTask")
            $usageCode
        """


        version(distribution)
            .inDirectory(file("consumer"))
            .withTasks("myTask")
            .withArgument("-s")
            // The expected deprecations change too much between versions for checking deprecations to be worthwhile.
            .noDeprecationChecks()
            .run()
    }

    private static String getUsageCode() {
        return """

            // Accessing extensions

            the<MyExtension>().some.set("thing")
            the(MyExtension::class).some.set("thing")
            configure<MyExtension> {
                some.set("thing")
            }

            // Error cases

            try {
                the<Unregistered>()
                throw Exception("UnknownDomainObjectException not thrown")
            } catch(ex: UnknownDomainObjectException) {}

            try {
                the(Unregistered::class)
                throw Exception("UnknownDomainObjectException not thrown")
            } catch(ex: UnknownDomainObjectException) {}

            try {
                configure<Unregistered> {}
                throw Exception("UnknownDomainObjectException not thrown")
            } catch(ex: UnknownDomainObjectException) {}
        """
    }
}
