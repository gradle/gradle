/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.scan.config

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.scan.config.fixtures.GradleEnterprisePluginFixture
import org.gradle.plugin.management.internal.autoapply.AutoAppliedGradleEnterprisePlugin
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.util.VersionNumber
import spock.lang.Issue
import spock.lang.Unroll

import static org.gradle.initialization.StartParameterBuildOptions.BuildScanOption
import static org.gradle.internal.scan.config.fixtures.GradleEnterprisePluginFixture.GRADLE_ENTERPRISE_PLUGIN_CLASS_NAME
import static org.gradle.internal.scan.config.fixtures.GradleEnterprisePluginFixture.GRADLE_ENTERPRISE_PLUGIN_ID
import static org.gradle.internal.scan.config.fixtures.GradleEnterprisePluginFixture.PUBLISHING_BUILD_SCAN_MESSAGE_PREFIX

class BuildScanAutoApplyIntegrationTest extends AbstractIntegrationSpec {
    private static final String PLUGIN_AUTO_APPLY_VERSION = AutoAppliedGradleEnterprisePlugin.VERSION
    private static final String PLUGIN_MINIMUM_VERSION = BuildScanPluginCompatibility.FIRST_GRADLE_ENTERPRISE_PLUGIN_VERSION_DISPLAY
    private static final String PLUGIN_NEWER_VERSION = newerThanAutoApplyPluginVersion()
    private final GradleEnterprisePluginFixture fixture = new GradleEnterprisePluginFixture(testDirectory, mavenRepo, createExecuter())

    def setup() {
        buildFile << """
            task dummy {}
        """
        settingsFile << fixture.pluginManagement()
        fixture.publishDummyPlugin(executer)
    }

    @ToBeFixedForInstantExecution
    def "automatically applies plugin when --scan is provided on command-line"() {
        when:
        runBuildWithScanRequest()

        then:
        pluginAppliedOnce()
    }

    @ToBeFixedForInstantExecution
    def "only applies once when -b used"() {
        when:
        file("other-build.gradle") << "task dummy {}"
        runBuildWithScanRequest("-b", "other-build.gradle")

        then:
        pluginAppliedOnce()
    }

    @ToBeFixedForInstantExecution
    def "does not automatically apply plugin when --scan is not provided on command-line"() {
        when:
        runBuildWithoutScanRequest()

        then:
        pluginNotApplied()
    }

    @ToBeFixedForInstantExecution
    def "does not automatically apply plugin to subprojects"() {
        when:
        settingsFile << """
            include 'a', 'b'
            assert pluginManager.hasPlugin('$GRADLE_ENTERPRISE_PLUGIN_ID')
        """

        and:
        runBuildWithScanRequest()

        then:
        pluginAppliedOnce()
    }

    @ToBeFixedForInstantExecution
    def "does not apply plugin to nested builds in a composite"() {
        when:
        settingsFile << """
            includeBuild 'a'
            assert pluginManager.hasPlugin('$GRADLE_ENTERPRISE_PLUGIN_ID')
        """
        file('a/settings.gradle') << """
            rootProject.name = 'a'
            assert !pluginManager.hasPlugin('$GRADLE_ENTERPRISE_PLUGIN_ID')
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

    @Unroll
    @ToBeFixedForInstantExecution
    def "uses #sequence version of plugin when explicit in plugins block"() {
        when:
        fixture.runtimeVersion = version
        fixture.artifactVersion = version
        settingsFile << """
            plugins {
                id '$GRADLE_ENTERPRISE_PLUGIN_ID' version '$version'
            }
        """

        and:
        runBuildWithScanRequest()

        then:
        pluginAppliedOnce(version)

        where:
        sequence | version
        "older"  | PLUGIN_MINIMUM_VERSION
        "same"   | PLUGIN_AUTO_APPLY_VERSION
        "newer"  | PLUGIN_NEWER_VERSION
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def "uses #sequence version of plugin when added to buildscript classpath"() {
        when:
        fixture.runtimeVersion = version
        fixture.artifactVersion = version
        settingsFile.text = """
            buildscript {
                repositories {
                    maven { url '${mavenRepo.uri}' }
                }
                dependencies {
                    classpath '${"com.gradle:gradle-enterprise-gradle-plugin:$version"}'
                }
            }
            apply plugin: '$GRADLE_ENTERPRISE_PLUGIN_ID'
        """

        and:
        runBuildWithScanRequest()

        then:
        pluginAppliedOnce(version)

        where:
        sequence | version
        "older"  | PLUGIN_MINIMUM_VERSION
        "same"   | PLUGIN_AUTO_APPLY_VERSION
        "newer"  | PLUGIN_NEWER_VERSION
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def "uses #sequence version of plugin when added to initscript classpath"() {
        when:
        fixture.runtimeVersion = version
        fixture.artifactVersion = version
        file('init.gradle') << """
            initscript {
                repositories {
                    maven { url '${mavenRepo.uri}' }
                }
            
                dependencies {
                    classpath '${"com.gradle:gradle-enterprise-gradle-plugin:$version"}'
                }
            }
            
            beforeSettings {
                it.apply plugin: $GRADLE_ENTERPRISE_PLUGIN_CLASS_NAME
            }
        """

        and:
        runBuildWithScanRequest('-I', 'init.gradle')

        then:
        pluginAppliedOnce(version)

        where:
        sequence | version
        "older"  | PLUGIN_MINIMUM_VERSION
        "same"   | PLUGIN_AUTO_APPLY_VERSION
        "newer"  | PLUGIN_NEWER_VERSION
    }

    @ToBeFixedForInstantExecution
    def "does not auto-apply plugin when explicitly requested and not applied"() {
        when:
        settingsFile << """
            plugins {
                id '$GRADLE_ENTERPRISE_PLUGIN_ID' version '${PLUGIN_AUTO_APPLY_VERSION}' apply false
            }
        """

        and:
        runBuildWithScanRequest()

        then:
        pluginNotApplied()
    }

    @Issue("gradle/gradle#3250")
    @ToBeFixedForInstantExecution
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

    @ToBeFixedForInstantExecution
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
            "The build scan plugin is not compatible with this version of Gradle.\n" +
            "Please see https://gradle.com/help/gradle-6-build-scan-plugin for more information."
        )
    }

    private void runBuildWithScanRequest(String... additionalArgs) {
        List<String> allArgs = ["--${BuildScanOption.LONG_OPTION}", "-s"]

        if (additionalArgs) {
            allArgs.addAll(additionalArgs)
        }

        args(allArgs as String[])
        runBuildWithoutScanRequest()
    }

    private void runBuildWithoutScanRequest() {
        succeeds("dummy")
    }

    private void pluginAppliedOnce(String version = PLUGIN_AUTO_APPLY_VERSION) {
        assert output.count("${PUBLISHING_BUILD_SCAN_MESSAGE_PREFIX}${version}") == 1
    }

    private void pluginNotApplied() {
        assert !output.contains(PUBLISHING_BUILD_SCAN_MESSAGE_PREFIX)
    }


    static String newerThanAutoApplyPluginVersion() {
        def autoApplyVersion = VersionNumber.parse(PLUGIN_AUTO_APPLY_VERSION)
        VersionNumber.version(autoApplyVersion.major + 1).toString()
    }
}
