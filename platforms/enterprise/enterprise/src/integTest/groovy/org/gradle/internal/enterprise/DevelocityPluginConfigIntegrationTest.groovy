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

package org.gradle.internal.enterprise

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.plugin.management.internal.autoapply.AutoAppliedDevelocityPlugin
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.util.internal.ToBeImplemented
import spock.lang.Issue

import javax.annotation.Nullable

import static org.gradle.internal.enterprise.GradleEnterprisePluginConfig.BuildScanRequest.NONE
import static org.gradle.internal.enterprise.GradleEnterprisePluginConfig.BuildScanRequest.REQUESTED
import static org.gradle.internal.enterprise.GradleEnterprisePluginConfig.BuildScanRequest.SUPPRESSED

class DevelocityPluginConfigIntegrationTest extends AbstractIntegrationSpec {

    def plugin = new DevelocityPluginCheckInFixture(testDirectory, mavenRepo, createExecuter())

    def setup() {
        settingsFile << plugin.pluginManagement()
        plugin.publishDummyPlugin(executer)
        buildFile << """
            task t
        """
    }

    def "has none requestedness if no switch present"() {
        given:
        settingsFile << plugin.plugins()

        when:
        succeeds "t"

        then:
        plugin.assertBuildScanRequest(output, NONE)
        plugin.assertAutoApplied(output, false)
    }

    def "is requested with --scan"() {
        given:
        if (!autoApplied) {
            settingsFile << plugin.plugins()
        }

        when:
        succeeds "t", "--scan"

        then:
        plugin.assertBuildScanRequest(output, REQUESTED)
        plugin.assertAutoApplied(output, autoApplied)

        where:
        autoApplied << [true, false]
    }

    def "is suppressed with --no-scan"() {
        given:
        settingsFile << plugin.plugins()

        when:
        succeeds "t", "--no-scan"

        then:
        plugin.assertBuildScanRequest(output, SUPPRESSED)
        plugin.assertAutoApplied(output, false)
    }

    def "is not auto-applied when added to classpath via buildscript block"() {
        given:
        def coordinates = "${groupId}:${artifactId}:${plugin.runtimeVersion}"
        settingsFile << """
            buildscript {
                repositories {
                    maven { url = '${mavenRepo.uri}' }
                }
                dependencies {
                    classpath("${coordinates}")
                }
            }

            apply plugin: 'com.gradle.develocity'
        """

        when:
        succeeds "t", "--scan"

        then:
        plugin.assertAutoApplied(output, false)

        where:
        groupId                                 | artifactId
        'com.gradle'                            | 'develocity-gradle-plugin'
        AutoAppliedDevelocityPlugin.ID.id | "${AutoAppliedDevelocityPlugin.ID.id}.gradle.plugin"
    }

    def "is auto-applied when --scan is used despite init script"() {
        given:
        def pluginArtifactId = "com.gradle:develocity-gradle-plugin:${plugin.runtimeVersion}"
        def initScript = file("build-scan-init.gradle") << """
            initscript {
                repositories {
                    maven { url = '${mavenRepo.uri}' }
                }
                dependencies {
                    classpath("${pluginArtifactId}")
                }
            }
            gradle.settingsEvaluated { settings ->
                if (settings.pluginManager.hasPlugin('${plugin.id}')) {
                    logger.lifecycle("${plugin.id} is already applied")
                } else {
                    logger.lifecycle("Applying ${plugin.className} via init script")
                    settings.pluginManager.apply(initscript.classLoader.loadClass('${plugin.className}'))
                }
            }
        """

        when:
        succeeds "t", "--scan", "--init-script", initScript.absolutePath

        then:
        plugin.assertAutoApplied(output, true)
        outputContains("${plugin.id} is already applied")
    }

    @ToBeImplemented("https://github.com/gradle/gradle/issues/24884")
    def "is not auto-applied when --scan is used and applied via init script in beforeSettings"() {
        given:
        def pluginArtifactId = "com.gradle:develocity-gradle-plugin:${plugin.runtimeVersion}"
        def initScript = file("build-scan-init.gradle") << """
            initscript {
                repositories {
                    maven { url = '${mavenRepo.uri}' }
                }
                dependencies {
                    classpath("${pluginArtifactId}")
                }
            }
            gradle.beforeSettings { settings ->
                settings.buildscript.repositories { maven { url = '${mavenRepo.uri}' } }
                settings.buildscript.dependencies.classpath("com.gradle:develocity-gradle-plugin:${plugin.runtimeVersion}")
            }
            gradle.settingsEvaluated { settings ->
                if (settings.pluginManager.hasPlugin('${plugin.id}')) {
                    logger.lifecycle("${plugin.id} is already applied")
                } else {
                    logger.lifecycle("Applying ${plugin.id} via init script")
                    settings.pluginManager.apply("com.gradle.develocity")
                }
            }
        """

        when:
        succeeds "t", "--scan", "--init-script", initScript.absolutePath

        then:
        plugin.assertAutoApplied(output, false)
        outputContains("Applying ${plugin.id} via init script")
        // TODO: Should not issue the warning
        plugin.issuedNoPluginWarning(output)
    }

    @Issue('https://github.com/gradle/gradle/issues/24023')
    @Requires(IntegTestPreconditions.IsConfigCached)
    def 'is correctly requested by the configuration cache'() {
        when:
        succeeds('t', *firstBuildArgs)

        then:
        assertRequestedOrNotApplied firstRequest

        when:
        succeeds('t', *secondBuildArgs)

        then:
        assertRequestedOrNotApplied secondRequest

        where:
        firstBuildArgs | firstRequest | secondBuildArgs | secondRequest
        ['--scan']     | REQUESTED    | []              | null
        []             | null         | ['--scan']      | REQUESTED
        ['--scan']     | REQUESTED    | ['--scan']      | REQUESTED
    }

    void assertRequestedOrNotApplied(@Nullable GradleEnterprisePluginConfig.BuildScanRequest buildScanRequest) {
        if (buildScanRequest) {
            plugin.assertBuildScanRequest(output, buildScanRequest)
        } else {
            plugin.notApplied(output)
        }
    }
}
