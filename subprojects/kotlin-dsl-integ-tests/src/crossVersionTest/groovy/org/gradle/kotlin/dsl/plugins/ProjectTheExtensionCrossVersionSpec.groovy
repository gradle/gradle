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
import org.gradle.util.GradleVersion

import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.mavenCentralRepository
import static org.gradle.test.fixtures.dsl.GradleDsl.KOTLIN
import static org.junit.Assume.assumeTrue

@TargetVersions("5.0+")
class ProjectTheExtensionCrossVersionSpec extends CrossVersionIntegrationSpec {

    def "can access extensions and conventions with current Gradle version from plugin built with Gradle 5.0+"() {

        when:
        pluginBuiltWith(previous)

        then:
        pluginAppliedWith(current)
    }

    def "can access extensions and conventions with Gradle 6.8+ from plugin built with current Gradle version"() {

        assumeTrue(previous.version >= GradleVersion.version('6.8'))

        when:
        pluginBuiltWith(current)

        then:
        pluginAppliedWith(previous)
    }

    private void pluginBuiltWith(GradleDistribution distribution) {
        file("plugin/settings.gradle.kts").text = ""
        file("plugin/build.gradle.kts").text = """
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
        file("plugin/src/main/kotlin/my-types.kt").text = """
            import org.gradle.api.provider.Property
            interface MyExtension { val some: Property<String> }
            interface MyConvention { val more: Property<String> }
            interface Unregistered
        """
        file("plugin/src/main/kotlin/my-plugin.gradle.kts").text = """
            extensions.create<MyExtension>("myExtension")
            convention.plugins["myConvention"] = objects.newInstance<MyConvention>()
            $usageCode
        """
        version(distribution)
            .inDirectory(file("plugin"))
            .withTasks("publish")
            .run()
    }

    private void pluginAppliedWith(GradleDistribution distribution) {
        file("consumer/settings.gradle.kts").text = """
            pluginManagement {
                repositories { maven(url = "${mavenRepo.uri}") }
            }
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
            .run()
    }

    private String getUsageCode() {
        return """

            // Accessing extensions

            the<MyExtension>().some.set("thing")
            the(MyExtension::class).some.set("thing")
            configure<MyExtension> {
                some.set("thing")
            }

            // Accessing conventions

            the<MyConvention>().more.set("less")
            the(MyConvention::class).more.set("less")
            configure<MyConvention> {
                more.set("less")
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
