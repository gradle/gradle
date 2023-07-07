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
import org.gradle.internal.enterprise.GradleEnterprisePluginCheckInFixture
import org.gradle.internal.enterprise.impl.DefaultGradleEnterprisePluginCheckInService
import org.gradle.plugin.management.internal.autoapply.AutoAppliedGradleEnterprisePlugin
import org.gradle.util.internal.VersionNumber

import static org.gradle.initialization.StartParameterBuildOptions.BuildScanOption

class BuildScanAutoApplyClasspathIntegrationTest extends AbstractIntegrationSpec {

    private static final VersionNumber PLUGIN_MINIMUM_NON_DEPRECATED_VERSION = DefaultGradleEnterprisePluginCheckInService.MINIMUM_SUPPORTED_PLUGIN_VERSION_SINCE_GRADLE_9
    private final GradleEnterprisePluginCheckInFixture fixture = new GradleEnterprisePluginCheckInFixture(testDirectory, mavenRepo, createExecuter())

    def setup() {
        fixture.publishDummyPlugin(executer)
        fixture.runtimeVersion = PLUGIN_MINIMUM_NON_DEPRECATED_VERSION
        fixture.artifactVersion = PLUGIN_MINIMUM_NON_DEPRECATED_VERSION

        file("build-src/my-plugin/build.gradle") << """
            plugins {
                id 'java-gradle-plugin'
            }

            repositories {
                 maven { url '${mavenRepo.uri}' }
            }

            gradlePlugin {
                plugins {
                    enterprise {
                        id = 'my.plugin'
                        implementationClass = 'org.gradle.reproducer.MyPlugin'
                    }
                }

                dependencies {
                   implementation '${AutoAppliedGradleEnterprisePlugin.GROUP}:${AutoAppliedGradleEnterprisePlugin.NAME}:${fixture.artifactVersion}'
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
                    settings.getPluginManager().apply("com.gradle.enterprise");
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
                    maven { url '${mavenRepo.uri}' }
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
        fixture.appliedOnce(output)
        fixture.assertAutoApplied(output, false)
    }

    def "task is up-to-date when using --scan"() {
        when:
        succeeds "cacheableTask"

        then:
        fixture.appliedOnce(output)
        fixture.assertAutoApplied(output, false)
        executedAndNotSkipped(":cacheableTask")

        when:
        succeeds "cacheableTask", "--scan"

        then:
        fixture.appliedOnce(output)
        fixture.assertAutoApplied(output, false)
        skipped(":cacheableTask")
    }
}
