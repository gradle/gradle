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

package org.gradle.integtests.resolve.platforms

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

/**
 * Reproduces the defect from https://github.com/3ll3d00d/gradle-bom-fork-bug against the
 * current Gradle build, using a "publish then consume" pattern: a producer sub-build applies
 * the real {@code java-library}, {@code java-test-fixtures}, {@code java-platform}, and
 * {@code maven-publish} plugins to publish faithful Gradle Module Metadata for five
 * interdependent modules; a separate consumer build then resolves against that repository.
 *
 * Synthetic maven-fixture metadata (via {@code mavenRepo.module(...)}) does not reproduce the
 * defect — the resolver appears to require the exact attribute set and variant shape that
 * real Gradle publishing produces.
 */
@Issue("https://github.com/gradle/gradle/issues/36234")
class PlatformImportedToTestFixturesForkResolveIntegrationTest extends AbstractIntegrationSpec {

    def "importing a platform to testFixturesApi does not drop the testFixtures variant of a transitively required module when the dependency tree forks"() {
        given:
        // Producer sub-build — publishes the 5 modules with real Gradle plugins.
        def producer = file("producer")
        producer.file("settings.gradle") << """
            rootProject.name = 'inner'
            include('base', 'fork', 'middle', 'upper', 'bom')
        """
        producer.file("build.gradle") << """
            subprojects {
                group = 'org.example'
                version = '1.0.0'
                apply plugin: 'maven-publish'
                afterEvaluate {
                    publishing {
                        repositories {
                            maven {
                                name = 'testRepo'
                                url = rootProject.layout.buildDirectory.dir('repo')
                            }
                        }
                    }
                }
            }
        """
        producer.file("base/build.gradle") << """
            plugins { id 'java-library'; id 'java-test-fixtures' }
            publishing.publications { mavenJava(MavenPublication) { from components.java } }
        """
        producer.file("fork/build.gradle") << """
            plugins { id 'java-library' }
            dependencies { api project(':base') }
            publishing.publications { mavenJava(MavenPublication) { from components.java } }
        """
        producer.file("middle/build.gradle") << """
            plugins { id 'java-library'; id 'java-test-fixtures' }
            dependencies {
                api project(':base')
                testFixturesApi testFixtures(project(':base'))
            }
            publishing.publications { mavenJava(MavenPublication) { from components.java } }
        """
        producer.file("upper/build.gradle") << """
            plugins { id 'java-library'; id 'java-test-fixtures' }
            dependencies {
                api project(':middle')
                testFixturesApi testFixtures(project(':middle'))
                // This is the second edge that creates the fork on `base`:
                //   upper.testFixtures -> fork -> base   (main library capability)
                // combined with
                //   upper.testFixtures -> testFixtures(middle) -> testFixtures(base)   (test-fixtures capability)
                testFixturesApi project(':fork')
            }
            publishing.publications { mavenJava(MavenPublication) { from components.java } }
        """
        producer.file("bom/build.gradle") << """
            plugins { id 'java-platform' }
            dependencies {
                constraints {
                    api project(':base')
                    api project(':fork')
                    api project(':middle')
                    api project(':upper')
                }
            }
            publishing.publications { mavenJava(MavenPublication) { from components.javaPlatform } }
        """

        // Publish the producer's five modules into producer/build/repo .
        executer.inDirectory(producer).withTasks(
                ":base:publishAllPublicationsToTestRepoRepository",
                ":fork:publishAllPublicationsToTestRepoRepository",
                ":middle:publishAllPublicationsToTestRepoRepository",
                ":upper:publishAllPublicationsToTestRepoRepository",
                ":bom:publishAllPublicationsToTestRepoRepository",
        ).run()
        def repoUri = producer.file("build/repo").toURI()

        // Reset the executer's working directory to the test root for the consumer build.
        executer.inDirectory(testDirectory)

        // Consumer at the test root — mirrors outer/core/build.gradle from the external reproducer.
        settingsFile << "rootProject.name = 'consumer'"
        buildFile << """
            plugins {
                id 'java-library'
                id 'java-test-fixtures'
            }

            repositories {
                maven { url = "${repoUri}" }
            }

            dependencies {
                api 'org.example:upper'
                testImplementation testFixtures('org.example:upper')
                testFixturesApi testFixtures('org.example:upper')

                api platform('org.example:bom:1.+')
                // The line below is what triggers the defect: importing the platform to
                // testFixturesApi (in combination with the fork above) drops base's
                // testFixturesApiElements variant from the resolved graph.
                testFixturesApi platform('org.example:bom:1.+')
            }
        """

        when:
        succeeds "dependencyInsight", "--dependency", "base", "--configuration", "testCompileClasspath"

        then:
        // Before the fix, only the `apiElements` variant of `base` is selected. Both variants
        // must be present for the test-fixtures classpath to be complete.
        outputContains("Variant apiElements")
        outputContains("Variant testFixturesApiElements")
    }
}
