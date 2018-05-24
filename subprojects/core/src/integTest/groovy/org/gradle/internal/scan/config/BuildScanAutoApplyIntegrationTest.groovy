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
import org.gradle.internal.scan.config.fixtures.BuildScanPluginFixture
import org.gradle.plugin.management.internal.autoapply.AutoAppliedBuildScanPlugin
import org.gradle.util.VersionNumber
import spock.lang.Issue
import spock.lang.Unroll

import static org.gradle.initialization.StartParameterBuildOptions.BuildScanOption
import static org.gradle.internal.scan.config.fixtures.BuildScanPluginFixture.BUILD_SCAN_PLUGIN_ID
import static org.gradle.internal.scan.config.fixtures.BuildScanPluginFixture.FULLY_QUALIFIED_DUMMY_BUILD_SCAN_PLUGIN_IMPL_CLASS
import static org.gradle.internal.scan.config.fixtures.BuildScanPluginFixture.PUBLISHING_BUILD_SCAN_MESSAGE_PREFIX

class BuildScanAutoApplyIntegrationTest extends AbstractIntegrationSpec {
    private static final String BUILD_SCAN_PLUGIN_AUTO_APPLY_VERSION = AutoAppliedBuildScanPlugin.VERSION
    private static final String BUILD_SCAN_PLUGIN_MINIMUM_VERSION = BuildScanPluginCompatibility.MIN_SUPPORTED_VERSION.toString()
    private static final String BUILD_SCAN_PLUGIN_NEWER_VERSION = newerThanAutoApplyPluginVersion()
    private final BuildScanPluginFixture fixture = new BuildScanPluginFixture(testDirectory, mavenRepo, createExecuter())

    def setup() {
        buildFile << """
            task dummy {}
        """
        settingsFile << fixture.pluginManagement()
        fixture.publishDummyBuildScanPlugin(executer)
    }

    def "automatically applies build scan plugin when --scan is provided on command-line"() {
        when:
        runBuildWithScanRequest()

        then:
        buildScanPluginApplied(BUILD_SCAN_PLUGIN_AUTO_APPLY_VERSION)
    }

    def "does not automatically apply build scan plugin when --scan is not provided on command-line"() {
        when:
        runBuildWithoutScanRequest()

        then:
        buildScanPluginNotApplied()
    }

    def "does not automatically apply build scan plugin to subprojects"() {
        when:
        settingsFile << """
            include 'a', 'b'
        """
        buildFile << """
            assert pluginManager.hasPlugin('$BUILD_SCAN_PLUGIN_ID')
            subprojects {
                assert !pluginManager.hasPlugin('$BUILD_SCAN_PLUGIN_ID')
            }
        """

        and:
        runBuildWithScanRequest()

        then:
        buildScanPluginApplied(BUILD_SCAN_PLUGIN_AUTO_APPLY_VERSION)
    }

    def "does not apply build scan plugin to buildSrc build"() {
        when:
        file('buildSrc/build.gradle') << """
            println 'in buildSrc'
            assert !pluginManager.hasPlugin('$BUILD_SCAN_PLUGIN_ID')
        """

        and:
        runBuildWithScanRequest()

        then:
        outputContains 'in buildSrc'
        buildScanPluginApplied(BUILD_SCAN_PLUGIN_AUTO_APPLY_VERSION)
    }

    def "does not apply build scan plugin to nested builds in a composite"() {
        when:
        settingsFile << """
            includeBuild 'a'
        """
        file('a/settings.gradle') << """
            rootProject.name = 'a'
        """
        file('a/build.gradle') << """
            println 'in nested build'
            assert !pluginManager.hasPlugin('$BUILD_SCAN_PLUGIN_ID')
        """

        and:
        runBuildWithScanRequest()

        then:
        outputContains 'in nested build'
        buildScanPluginApplied(BUILD_SCAN_PLUGIN_AUTO_APPLY_VERSION)
    }

    @Unroll
    def "uses #sequence version of plugin when explicit in plugins block"() {
        when:
        fixture.runtimeVersion = version
        fixture.artifactVersion = version
        pluginsRequest "id '$BUILD_SCAN_PLUGIN_ID' version '$version'"

        and:
        runBuildWithScanRequest()

        then:
        buildScanPluginApplied(version)

        where:
        sequence | version
        "older"  | BUILD_SCAN_PLUGIN_MINIMUM_VERSION
        "same"   | BUILD_SCAN_PLUGIN_AUTO_APPLY_VERSION
        "newer"  | BUILD_SCAN_PLUGIN_NEWER_VERSION
    }

    @Unroll
    def "uses #sequence version of plugin when added to buildscript classpath"() {
        when:
        fixture.runtimeVersion = version
        fixture.artifactVersion = version
        buildscriptApply "com.gradle:build-scan-plugin:$version"

        and:
        runBuildWithScanRequest()

        then:
        buildScanPluginApplied(version)

        where:
        sequence | version
        "older"  | BUILD_SCAN_PLUGIN_MINIMUM_VERSION
        "same"   | BUILD_SCAN_PLUGIN_AUTO_APPLY_VERSION
        "newer"  | BUILD_SCAN_PLUGIN_NEWER_VERSION
    }

    @Unroll
    def "uses #sequence version of plugin when added to initscript classpath"() {
        when:
        fixture.runtimeVersion = version
        fixture.artifactVersion = version
        initScriptApply "com.gradle:build-scan-plugin:$version"

        and:
        runBuildWithScanRequest('-I', 'init.gradle')

        then:
        buildScanPluginApplied(version)

        where:
        sequence | version
        "older"  | BUILD_SCAN_PLUGIN_MINIMUM_VERSION
        "same"   | BUILD_SCAN_PLUGIN_AUTO_APPLY_VERSION
        "newer"  | BUILD_SCAN_PLUGIN_NEWER_VERSION
    }

    def "does not auto-apply build scan plugin when explicitly requested and not applied"() {
        when:
        pluginsRequest "id '$BUILD_SCAN_PLUGIN_ID' version '${BUILD_SCAN_PLUGIN_AUTO_APPLY_VERSION}' apply false"

        and:
        runBuildWithScanRequest()

        then:
        buildScanPluginNotApplied()
    }

    @Issue("gradle/gradle#3250")
    def "automatically applies build scan plugin when --scan is provided on command-line and a script is applied in the buildscript block"() {
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
        buildScanPluginApplied(BUILD_SCAN_PLUGIN_AUTO_APPLY_VERSION)
    }

    private void runBuildWithScanRequest(String... additionalArgs) {
        List<String> allArgs = ["--${BuildScanOption.LONG_OPTION}"]

        if (additionalArgs) {
            allArgs.addAll(additionalArgs)
        }

        args(allArgs as String[])
        runBuildWithoutScanRequest()
    }

    private void runBuildWithoutScanRequest() {
        succeeds("dummy")
    }

    private void buildScanPluginApplied(String version) {
        assert output.contains("${PUBLISHING_BUILD_SCAN_MESSAGE_PREFIX}${version}")
    }

    private void buildScanPluginNotApplied() {
        assert !output.contains(PUBLISHING_BUILD_SCAN_MESSAGE_PREFIX)
    }

    private void pluginsRequest(String request) {
        buildFile.text = """
            plugins {
                ${request}
            }
        """ + buildFile.text
    }

    private void buildscriptApply(String coordinates) {
        buildFile.text = """
            buildscript {
                repositories {
                    maven { url '${mavenRepo.uri}' }
                }
                dependencies {
                    classpath '${coordinates}'
                }
            }
            apply plugin: '$BUILD_SCAN_PLUGIN_ID'
        """ + buildFile.text
    }

    private void initScriptApply(String coordinates) {
        file('init.gradle') << """
            initscript {
                repositories {
                    maven { url '${mavenRepo.uri}' }
                }
            
                dependencies {
                    classpath '${coordinates}'
                }
            }
            
            rootProject {
                apply plugin: $FULLY_QUALIFIED_DUMMY_BUILD_SCAN_PLUGIN_IMPL_CLASS
            }
        """
    }

    static String newerThanAutoApplyPluginVersion() {
        def autoApplyVersion = VersionNumber.parse(BUILD_SCAN_PLUGIN_AUTO_APPLY_VERSION)
        VersionNumber.version(autoApplyVersion.major + 1).toString()
    }
}
