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

package org.gradle.internal.enterprise.core

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.enterprise.DevelocityPluginCheckInFixture
import org.gradle.internal.enterprise.GradleEnterprisePluginCheckInFixture
import org.gradle.internal.enterprise.impl.DefaultGradleEnterprisePluginCheckInService
import org.gradle.plugin.management.internal.autoapply.AutoAppliedDevelocityPlugin
import org.gradle.util.internal.VersionNumber
import spock.lang.Issue

import static org.gradle.initialization.StartParameterBuildOptions.BuildScanOption
import static org.gradle.internal.enterprise.impl.legacy.DevelocityPluginCompatibility.MINIMUM_SUPPORTED_PLUGIN_VERSION

class BuildScanAutoApplyIntegrationTest extends AbstractIntegrationSpec {

    private static final String PLUGIN_AUTO_APPLY_VERSION = AutoAppliedDevelocityPlugin.VERSION
    private static final String PLUGIN_NEWER_VERSION = newerThanAutoApplyPluginVersion()

    private final DevelocityPluginCheckInFixture fixture = new DevelocityPluginCheckInFixture(testDirectory, mavenRepo, createExecuter())
    private final GradleEnterprisePluginCheckInFixture gradleEnterpriseFixture = new GradleEnterprisePluginCheckInFixture(testDirectory, mavenRepo, createExecuter())

    def setup() {
        buildFile << """
            task dummy {}
        """
        settingsFile << fixture.pluginManagement()
        fixture.publishDummyPlugin(executer)
    }

    void applyPlugin() {
        settingsFile << fixture.plugins()
    }

    def "automatically applies plugin when --scan is provided on command-line"() {
        when:
        runBuildWithScanRequest()

        then:
        pluginAppliedOnce()
    }

    def "does not automatically apply plugin when --scan is not provided on command-line"() {
        when:
        runBuildWithoutScanRequest()

        then:
        pluginNotApplied()
    }

    def "does not automatically apply plugin to subprojects"() {
        when:
        createDirs("a", "b")
        settingsFile << """
            include 'a', 'b'
            assert pluginManager.hasPlugin('$fixture.id')
        """

        and:
        runBuildWithScanRequest()

        then:
        pluginAppliedOnce()
    }

    def "does not apply plugin to nested builds in a composite"() {
        when:
        settingsFile << """
            includeBuild 'a'
            assert pluginManager.hasPlugin('$fixture.id')
        """
        file('a/settings.gradle') << """
            rootProject.name = 'a'
            assert !pluginManager.hasPlugin('$fixture.id')
        """
        file('a/build.gradle') << """
            println 'in nested build'
        """

        and:
        runBuildWithScanRequest()

        then:
        outputContains 'in nested build'
        pluginAppliedOnce()
    }

    def "uses #sequence version of plugin when explicit in plugins block"() {
        when:
        fixture.runtimeVersion = version
        fixture.artifactVersion = version
        settingsFile << fixture.plugins()

        and:
        runBuildWithScanRequest()

        then:
        pluginAppliedOnce()

        where:
        sequence | version
        "older"  | MINIMUM_SUPPORTED_PLUGIN_VERSION
        "same"   | PLUGIN_AUTO_APPLY_VERSION
        "newer"  | PLUGIN_NEWER_VERSION
    }

    def "uses #sequence version of plugin when added to buildscript classpath"() {
        when:
        fixture.runtimeVersion = version
        fixture.artifactVersion = version
        settingsFile.text = """
            buildscript {
                repositories {
                    maven { url = '${mavenRepo.uri}' }
                }
                dependencies {
                    classpath '${"com.gradle:develocity-gradle-plugin:$version"}'
                }
            }
            apply plugin: '$fixture.id'
        """

        and:
        runBuildWithScanRequest()

        then:
        pluginAppliedOnce()

        where:
        sequence | version
        "older"  | MINIMUM_SUPPORTED_PLUGIN_VERSION
        "same"   | PLUGIN_AUTO_APPLY_VERSION
        "newer"  | PLUGIN_NEWER_VERSION
    }

    def "uses #sequence version of plugin when added to initscript classpath"() {
        when:
        fixture.runtimeVersion = version
        fixture.artifactVersion = version
        file('init.gradle') << """
            initscript {
                repositories {
                    maven { url = '${mavenRepo.uri}' }
                }

                dependencies {
                    classpath '${"com.gradle:develocity-gradle-plugin:$version"}'
                }
            }

            beforeSettings {
                it.apply plugin: $fixture.className
            }
        """

        and:
        runBuildWithScanRequest('-I', 'init.gradle')

        then:
        pluginAppliedOnce()

        where:
        sequence | version
        "older"  | MINIMUM_SUPPORTED_PLUGIN_VERSION
        "same"   | PLUGIN_AUTO_APPLY_VERSION
        "newer"  | PLUGIN_NEWER_VERSION
    }

    def "does not auto-apply plugin when explicitly requested and not applied"() {
        when:
        settingsFile << """
            plugins {
                id '$fixture.id' version '${fixture.artifactVersion}' apply false
            }
        """

        and:
        runBuildWithScanRequest()

        then:
        pluginNotApplied()
    }

    @Issue("gradle/gradle#3250")
    def "automatically applies plugin when --scan is provided on command-line and a script is applied in the buildscript block"() {
        given:
        buildFile << """
            buildscript {
              rootProject.apply { from(rootProject.file("gradle/dependencies.gradle")) }
            }
        """.stripIndent()
        file("gradle/dependencies.gradle") << ""

        when:
        runBuildWithScanRequest()

        then:
        pluginAppliedOnce()
    }

    def "fails well when trying to use old plugin"() {
        given:
        buildFile.text = """
            plugins {
                id "com.gradle.build-scan" version "$PLUGIN_AUTO_APPLY_VERSION"
            }
        """ + buildFile.text

        when:
        fails("--scan", "dummy")

        then:
        failure.assertHasDescription("Error resolving plugin [id: 'com.gradle.build-scan', version: '$PLUGIN_AUTO_APPLY_VERSION']")
        failure.assertHasCause(
            "The Develocity plugin is not compatible with this version of Gradle.\n" +
                "Please see https://gradle.com/help/gradle-6-build-scan-plugin for more information."
        )
    }

    def "warns if scan requested but no scan plugin applied"() {
        given:
        applyPlugin()
        fixture.doCheckIn = false

        when:
        succeeds "dummy", "--scan"

        then:
        fixture.issuedNoPluginWarning(output)
    }

    def "does not warns if scan requested but no scan plugin unsupported"() {
        given:
        applyPlugin()

        when:
        succeeds "dummy", "--scan", "-D${DefaultGradleEnterprisePluginCheckInService.UNSUPPORTED_TOGGLE}=true"

        then:
        fixture.assertUnsupportedMessage(output, DefaultGradleEnterprisePluginCheckInService.UNSUPPORTED_TOGGLE_MESSAGE)
        fixture.didNotIssuedNoPluginWarning(output)
    }

    def "does not warn if no scan requested but no scan plugin applied"() {
        given:
        applyPlugin()
        fixture.doCheckIn = false

        when:
        succeeds "dummy", "--no-scan"

        then:
        fixture.didNotIssuedNoPluginWarning(output)
    }

    def "does not warn for each nested build if --scan used"() {
        given:
        applyPlugin()
        fixture.doCheckIn = false

        file("buildSrc/build.gradle") << ""
        file("a/buildSrc/build.gradle") << ""
        file("a/build.gradle") << ""
        file("a/settings.gradle") << ""
        file("b/buildSrc/build.gradle") << ""
        file("b/build.gradle") << ""
        file("b/settings.gradle") << ""
        settingsFile << """
            includeBuild "a"
            includeBuild "b"
        """
        buildFile.text = """
            task t
        """

        when:
        succeeds "t", "--scan"

        then:
        fixture.issuedNoPluginWarningCount(output, 1)
    }

    def "does not auto-apply plugin when Gradle Enterprise plugin is applied using plugin ID"() {
        when:
        gradleEnterpriseFixture.publishDummyPlugin(executer)
        settingsFile << gradleEnterpriseFixture.plugins()

        and:
        runBuildWithScanRequest()

        then:
        pluginNotApplied()
    }

    def "does not auto-apply plugin when Gradle Enterprise plugin is applied using plugin class name"() {
        when:
        gradleEnterpriseFixture.publishDummyPlugin(executer)
        settingsFile.text = """
            buildscript {
                repositories {
                    maven { url = '${mavenRepo.uri}' }
                }
                dependencies {
                    classpath '${"com.gradle:gradle-enterprise-gradle-plugin:${gradleEnterpriseFixture.runtimeVersion}"}'
                }
            }
            apply plugin: $gradleEnterpriseFixture.className
        """

        and:
        runBuildWithScanRequest()

        then:
        pluginNotApplied()
    }

    def "does not auto-apply plugin when Gradle Enterprise plugin explicitly requested and not applied"() {
        when:
        gradleEnterpriseFixture.publishDummyPlugin(executer)
        settingsFile << """
            plugins {
                id '$gradleEnterpriseFixture.id' version '${gradleEnterpriseFixture.artifactVersion}' apply false
            }
        """

        and:
        runBuildWithScanRequest()

        then:
        pluginNotApplied()
    }

    def "does not auto-apply plugin when Gradle Enterprise plugin is added to initscript classpath"() {
        when:
        gradleEnterpriseFixture.publishDummyPlugin(executer)
        file('init.gradle') << """
            initscript {
                repositories {
                    maven { url = '${mavenRepo.uri}' }
                }

                dependencies {
                    classpath '${"com.gradle:gradle-enterprise-gradle-plugin:${gradleEnterpriseFixture.runtimeVersion}"}'
                }
            }

            beforeSettings {
                it.apply plugin: $gradleEnterpriseFixture.className
            }
        """

        and:
        runBuildWithScanRequest('-I', 'init.gradle')

        then:
        pluginNotApplied()
    }

    private void runBuildWithScanRequest(String... additionalArgs) {
        List<String> allArgs = ["--${BuildScanOption.LONG_OPTION}", "-s"]

        if (additionalArgs) {
            allArgs.addAll(additionalArgs)
        }

        args(allArgs as String[])
        runBuildWithoutScanRequest()
    }

    private void runBuildWithoutScanRequest(String... additionalArgs) {
        if (additionalArgs) {
            args(additionalArgs)
        }

        succeeds("dummy")
    }

    private void pluginAppliedOnce() {
        fixture.appliedOnce(output)
    }

    private void pluginNotApplied() {
        fixture.notApplied(output)
    }

    static String newerThanAutoApplyPluginVersion() {
        def autoApplyVersion = VersionNumber.parse(PLUGIN_AUTO_APPLY_VERSION)
        VersionNumber.version(autoApplyVersion.major + 1).toString()
    }
}
