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

package org.gradle.internal.enterprise.core

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.enterprise.BaseBuildScanPluginCheckInFixture
import org.gradle.internal.enterprise.DevelocityPluginCheckInFixture
import org.gradle.internal.enterprise.GradleEnterprisePluginCheckInFixture
import org.gradle.internal.enterprise.impl.DefaultGradleEnterprisePluginCheckInService
import org.gradle.util.internal.VersionNumber
import org.junit.Assume

abstract class BuildScanAutoApplyClasspathIntegrationTest extends AbstractIntegrationSpec {

    private static final VersionNumber PLUGIN_MINIMUM_NON_DEPRECATED_VERSION = DefaultGradleEnterprisePluginCheckInService.MINIMUM_SUPPORTED_PLUGIN_VERSION_SINCE_GRADLE_9

    protected final DevelocityPluginCheckInFixture autoAppliedPluginFixture = new DevelocityPluginCheckInFixture(testDirectory, mavenRepo, createExecuter())

    abstract boolean isIsolatedProjectsCompatible()

    abstract BaseBuildScanPluginCheckInFixture getTransitivePluginFixture()

    abstract void assertNotAutoApplied(String output)

    static class DevelocityAutoApplyClasspathIntegrationTest extends BuildScanAutoApplyClasspathIntegrationTest {

        @Override
        boolean isIsolatedProjectsCompatible() {
            return false
        }

        @Override
        BaseBuildScanPluginCheckInFixture getTransitivePluginFixture() {
            autoAppliedPluginFixture
        }

        @Override
        void assertNotAutoApplied(String output) {
            // Develocity plugin is transitively applied, but not via auto-application mechanism
            autoAppliedPluginFixture.assertAutoApplied(output, false)
        }
    }

    static class GradleEnterpriseAutoApplyClasspathIntegrationTest extends BuildScanAutoApplyClasspathIntegrationTest {

        private final GradleEnterprisePluginCheckInFixture gradleEnterpriseFixture = new GradleEnterprisePluginCheckInFixture(testDirectory, mavenRepo, createExecuter())

        @Override
        boolean isIsolatedProjectsCompatible() {
            return true
        }

        @Override
        BaseBuildScanPluginCheckInFixture getTransitivePluginFixture() {
            // Develocity plugin is auto-applied but the Gradle Enterprise plugin is a transitive dependency
            gradleEnterpriseFixture
        }

        @Override
        void assertNotAutoApplied(String output) {
            // Develocity plugin is applied neither as a transitive dependency, nor via auto-application mechanism as Gradle Enterprise plugin is present
            autoAppliedPluginFixture.notApplied(output)
        }
    }

    def setup() {
        Assume.assumeTrue(GradleContextualExecuter.notIsolatedProjects || isIsolatedProjectsCompatible())

        autoAppliedPluginFixture.publishDummyPlugin(executer)

        transitivePluginFixture.publishDummyPlugin(executer)
        transitivePluginFixture.runtimeVersion = PLUGIN_MINIMUM_NON_DEPRECATED_VERSION
        transitivePluginFixture.artifactVersion = PLUGIN_MINIMUM_NON_DEPRECATED_VERSION

        file("build-src/my-plugin/build.gradle") << """
            plugins {
                id 'java-gradle-plugin'
            }

            repositories {
                 maven { url = '${mavenRepo.uri}' }
            }

            gradlePlugin {
                plugins {
                    develocity {
                        id = 'my.plugin'
                        implementationClass = 'org.gradle.reproducer.MyPlugin'
                    }
                }

                dependencies {
                   implementation '${transitivePluginFixture.pluginArtifactGroup}:${transitivePluginFixture.pluginArtifactName}:${transitivePluginFixture.artifactVersion}'
                }
            }
        """
        file("build-src/my-plugin/src/main/java/org/gradle/reproducer/MyPlugin.java") << """
            package org.gradle.reproducer;

            import org.gradle.api.Plugin;
            import org.gradle.api.initialization.Settings;

            class MyPlugin implements Plugin<Settings> {
                @Override
                public void apply(Settings settings) {
                    settings.getPluginManager().apply("${transitivePluginFixture.id}");
                }
            }
        """

        file("build-src/settings.gradle") << """
            rootProject.name = 'build-src'

            include 'my-plugin'
        """

        settingsFile << """
            pluginManagement {
                includeBuild 'build-src'
                repositories {
                    maven { url = '${mavenRepo.uri}' }
                }
            }

            plugins {
                id 'my.plugin'
            }
        """

        file('src/dummy-input.txt') << "dummy text"

        buildFile << """
            task cacheableTask {
                def inputFile = file("src/dummy-input.txt")
                def outputDir = file(layout.buildDirectory.dir("dummy-output"))
                def outputFile = file(layout.buildDirectory.file("dummy-output/result.txt"))

                outputs.cacheIf { true }

                inputs.files(inputFile)
                    .withPropertyName("dummy-input")
                    .withPathSensitivity(PathSensitivity.RELATIVE)

                outputs.dir(outputDir)
                    .withPropertyName('dummy-output')

                doLast {
                    outputFile.text = inputFile.text
                }
            }
        """
    }

    def "transitively applied build scan plugin disables auto-application"() {
        when:
        succeeds "cacheableTask", "--scan"

        then:
        transitivePluginFixture.appliedOnce(output)
        assertNotAutoApplied(output)
    }

    def "task is up-to-date when using --scan"() {
        when:
        succeeds "cacheableTask"

        then:
        transitivePluginFixture.appliedOnce(output)
        assertNotAutoApplied(output)
        executedAndNotSkipped(":cacheableTask")

        when:
        succeeds "cacheableTask", "--scan"

        then:
        transitivePluginFixture.appliedOnce(output)
        assertNotAutoApplied(output)
        skipped(":cacheableTask")
    }
}
