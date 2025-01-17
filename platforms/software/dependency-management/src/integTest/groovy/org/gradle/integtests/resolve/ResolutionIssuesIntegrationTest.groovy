/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.integtests.resolve

import groovy.test.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.Ignore
import spock.lang.Issue

/**
 * Common location for unresolved resolution tests that are related to user-reported issues.
 *
 * Once these issues are resolved, we should make sure to move these tests to a
 * file more appropriate for the feature they are testing.
 */
class ResolutionIssuesIntegrationTest extends AbstractIntegrationSpec {

    @NotYetImplemented
    @Issue("https://github.com/gradle/gradle/issues/14220#issuecomment-1283947029")
    def "resolution result represents failure to resolve dynamic selected module version when platform has constraint on that module"() {
        mavenRepo.module("test", "module1", "11.1.0.1").publish()

        settingsFile << "include 'plat'"
        file("plat/build.gradle") << """
            plugins {
                id("java-platform")
            }

            dependencies {
                constraints {
                    api "test:module1:11.1.0.1"
                }
            }
        """

        buildFile << """
            plugins {
                id("jvm-ecosystem")
            }

            configurations {
                dependencyScope("implementation")
                resolvable("runtimeClasspath") {
                    extendsFrom implementation
                }
            }

            tasks.register('resolve') {
                def root = configurations.runtimeClasspath.incoming.resolutionResult.rootComponent
                doLast {
                    println root.get()
                }
            }

            ${mavenTestRepository()}

            dependencies {
                implementation 'test:module1:11.2.0.+'
                implementation platform(project(":plat"))
            }
        """

        expect:
        succeeds("resolve")
    }

    @NotYetImplemented
    @Issue("https://github.com/gradle/gradle/issues/22326")
    def "capability conflict skip"() {
        settingsFile << """
            include("app", "extension")

            dependencyResolutionManagement {
                ${mavenCentralRepository()}
                components {
                    withModule("org.jboss.spec.javax.transaction:jboss-transaction-api_1.2_spec") {
                        allVariants {
                            withCapabilities {
                                addCapability("javax.transaction", "javax.transaction-api", id.version)
                            }
                        }
                    }
                }
            }
        """

        file("app/build.gradle") << """
            plugins {
                id("java-library")
            }

            dependencies {
                // Change the order of these two dependencies to have the capability conflict detected
                runtimeOnly("org.liquibase.ext:liquibase-hibernate5:4.4.3")
                runtimeOnly("org.eclipse.jetty.aggregate:jetty-all:9.4.35.v20201120")
            }
        """

        file("extension/build.gradle") << """
            plugins {
                id("java-library")
            }

            configurations.all {
                resolutionStrategy.capabilitiesResolution.withCapability("javax.transaction:javax.transaction-api") {
                    select("javax.transaction:javax.transaction-api:0")
                }
            }

            dependencies {
                implementation("org.hibernate:hibernate-core:5.5.7.Final")
                implementation(project(":app"))
            }
        """

        when:
        succeeds(":extension:dependencies", "--configuration=runtimeClasspath")

        then:
        outputContains("org.jboss.spec.javax.transaction:jboss-transaction-api_1.2_spec:1.1.1.Final -> javax.transaction:javax.transaction-api:1.3")
    }

    @Ignore("Original reproducer. Minified version below")
    @Requires(UnitTestPreconditions.Jdk17OrLater)
    @Issue("https://github.com/gradle/gradle/issues/22326#issuecomment-1617422240")
    def "guava issue"() {
        settingsFile << """
            pluginManagement {
                ${mavenCentralRepository()}
                repositories {
                    google()
                    maven { url = 'https://jitpack.io' }
                }
            }

            dependencyResolutionManagement {
                ${mavenCentralRepository()}
                repositories {
                    google()
                    maven { url = 'https://jitpack.io' }
                }
            }
        """

        propertiesFile << "android.useAndroidX=true"

        file("src/main/AndroidManifest.xml") << """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"></manifest>
"""

        buildFile << '''
            plugins {
                id("com.android.application") version "8.2.2"
            }

            android {
                namespace 'org.zephyrsoft.trackworktime'
                compileSdkVersion 33
            }

            dependencies {
                implementation 'androidx.appcompat:appcompat:1.6.1'
                implementation "ch.acra:acra-http:5.10.1"
                implementation 'com.google.guava:guava:32.1.1-android'
            }
        '''

        expect:
        succeeds("checkDebugDuplicateClasses")
    }

    @NotYetImplemented
    @Issue("https://github.com/gradle/gradle/issues/22326#issuecomment-1617422240")
    def "guava issue -- minimized"() {
        // We shadow the real android libraries with java libraries so that we don't need to
        // apply the android plugin.
        def androidxCore = mavenRepo.module("androidx.core", "core", "1.8.0")
            .dependsOn("androidx.concurrent", "concurrent-futures", "1.1.0")
            .publish()
        mavenRepo.module("androidx.activity", "activity", "1.6.0")
            .dependsOn(androidxCore)
            .publish()

        mavenRepo.module("ch.acra", "acra-http", "5.10.1")
            .dependsOn("com.google.auto", "auto-common", "1.2.1")
            .publish()

        settingsFile << """
            pluginManagement {
                ${mavenCentralRepository()}
            }

            dependencyResolutionManagement {
                ${mavenTestRepository()}
                ${mavenCentralRepository()}
                repositories {
                    google()
                }
            }
        """

        buildFile << '''
            plugins {
                id("java-library")
            }

            dependencies {
                implementation 'androidx.activity:activity:1.6.0'
                implementation "ch.acra:acra-http:5.10.1"
                implementation 'com.google.guava:guava:32.1.1-android'
            }

            task checkDebugDuplicateClasses {
                def files = configurations.runtimeClasspath
                doLast {
                    assert files*.name.contains("guava-32.1.1-jre.jar")
                    assert !files*.name.contains("listenablefuture-1.0.jar")
                }
            }
        '''

        expect:
        succeeds("checkDebugDuplicateClasses")
    }

    @NotYetImplemented
    @UnsupportedWithConfigurationCache(because = "Uses allDependencies")
    @Issue("https://github.com/gradle/gradle/pull/26016#issuecomment-1795491970")
    def "conflict between two nodes in the same component causes edge without target node"() {
        settingsFile << "include 'producer'"
        file("producer/build.gradle") << """
            configurations {
                consumable("one") {
                    outgoing {
                        capability('o:n:e')
                    }
                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, "foo"))
                    }
                }
                consumable("one-preferred") {
                    outgoing {
                        capability('o:n:e')
                        capability('g:one-preferred:v')
                    }
                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, "foo"))
                    }
                }
            }
        """
        buildFile << """
            configurations {
                dependencyScope("implementation")
                resolvable("classpath") {
                    extendsFrom(implementation)
                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, "foo"))
                    }
                }
            }

            configurations.classpath {
                resolutionStrategy.capabilitiesResolution.all { details ->
                    def selection =
                        details.candidates.find { it.variantName.endsWith('preferred') }
                    println("Selecting \$selection from \${details.candidates}")
                    details.select(selection)
                }
            }

            dependencies {
                implementation(project(':producer')) {
                    capabilities {
                        requireCapability('o:n:e')
                    }
                }
                implementation(project(':producer')) {
                    capabilities {
                        requireCapability('o:n:e')
                        requireCapability('g:one-preferred:v')
                    }
                }
            }

            task resolve {
                def result = configurations.classpath.incoming.resolutionResult
                doLast {
                    result.allDependencies {
                        assert it.selectedVariant != null
                    }
                }
            }
        """

        expect:
        succeeds("resolve")
    }

    @NotYetImplemented
    def "can select unrelated variant from component with variant that loses capability conflict"() {
        settingsFile << """
            include("producer1")
            include("producer2")
        """
        buildFile << """
            plugins {
                id("java-library")
            }

            dependencies {
                implementation(project(":producer2")) {
                    capabilities {
                        requireCapability("org:bar:1.0")
                    }
                }
                implementation(project(":producer1")) {
                    capabilities {
                        requireCapability("org:foo:2.0")
                    }
                }
                implementation(project(":producer2")) {
                    capabilities {
                        requireCapability("org:foo:1.0")
                    }
                }
            }

            task resolve {
                def files = configurations.runtimeClasspath.incoming.files
                doLast {
                    println(files*.name)
                }
            }

            configurations.runtimeClasspath {
                resolutionStrategy {
                    capabilitiesResolution {
                        withCapability("org:foo") {
                            selectHighestVersion()
                        }
                    }
                }
            }
        """

        file("producer1/build.gradle") << """
            configurations {
                consumable("foo") {
                    outgoing.capability("org:foo:2.0")
                    outgoing.artifact(file("producer1-foo"))
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.LIBRARY))
                    }
                }
            }
        """
        file("producer2/build.gradle") << """
            configurations {
                consumable("foo") {
                    outgoing.capability("org:foo:1.0")
                    outgoing.artifact(file("producer2-foo"))
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.LIBRARY))
                    }
                }
                consumable("bar") {
                    outgoing.capability("org:bar:1.0")
                    outgoing.artifact(file("producer2-bar"))
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.LIBRARY))
                    }
                }
            }
        """

        expect:
        succeeds(":resolve")
        outputContains("[producer2-bar, producer1-foo]")
    }

    def "depending on a bom of one version and another dependency that upgrades that bom causes unstable graph"() {

        // This code triggers a situation where we reselect the root node of the graph.
        // This would lead to an unstable graph if it weren't for some suspicious code that
        // detects certain unstable scenarios.
        // See SelectorState#markForReuse()

        mavenRepo.module("org.junit", "junit-bom", "5.10.2")
            .withModuleMetadata()
            .adhocVariants()
            .variant("apiElements", [
                "org.gradle.category": "platform",
                "org.gradle.usage": "java-api"
            ])
            .publish()

        mavenRepo.module("org.junit", "junit-bom", "5.11.3")
            .withModuleMetadata()
            .adhocVariants()
            .variant("apiElements", [
                "org.gradle.category": "platform",
                "org.gradle.usage": "java-api"
            ])
            .publish()

        mavenRepo.module("org.junit.jupiter", "junit-jupiter-params", "5.11.3")
            .withModuleMetadata()
            .withVariant("api") {
                dependsOn("org.junit", "junit-bom", "5.11.3") {
                    attribute("org.gradle.category", "platform")
                    endorseStrictVersions = true
                }
            }
            .publish()

        buildFile << """
            plugins {
              id("java-library")
            }

            ${mavenTestRepository()}

            dependencies {
                implementation(platform("org.junit:junit-bom:5.10.2"))
                implementation("org.junit.jupiter:junit-jupiter-params:5.11.3")
            }
        """

        expect:
        succeeds(":dependencies", "--configuration", "compileClasspath")
    }
}
