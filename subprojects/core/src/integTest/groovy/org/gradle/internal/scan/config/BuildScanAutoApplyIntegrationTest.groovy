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
import org.gradle.test.fixtures.plugin.PluginBuilder
import spock.lang.Unroll

class BuildScanAutoApplyIntegrationTest extends AbstractIntegrationSpec {
    public static final String BUILD_SCAN_DEFAULT_VERSION = "1.9"

    def setup() {
        buildFile << """
            task dummy {}
"""
        settingsFile << """
            pluginManagement {
                repositories {
                    maven { url '${mavenRepo.uri}' }
                }
            }
"""

        publishDummyBuildScanPlugin(BUILD_SCAN_DEFAULT_VERSION)
    }

    def "automatically applies buildscan plugin when --scan is provided on command-line"() {
        when:
        runBuildWithScanRequest()

        then:
        buildScanPluginApplied(BUILD_SCAN_DEFAULT_VERSION)
    }

    def "does not automatically apply buildscan plugin when --scan is not provided on command-line"() {
        when:
        runBuildWithoutScanRequest()

        then:
        buildScanPluginNotApplied()
    }

    @Unroll
    def "uses #sequence version of plugin when explicit in plugins block"() {
        when:
        if (version != BUILD_SCAN_DEFAULT_VERSION) {
            publishDummyBuildScanPlugin(version)
        }
        pluginsRequest "id 'com.gradle.build-scan' version '$version'"

        and:
        runBuildWithScanRequest()

        then:
        buildScanPluginApplied(version)

        where:
        sequence | version
        "older"  | "1.8"
        "same"   | BUILD_SCAN_DEFAULT_VERSION
        "newer"  | "1.10"
    }

    @Unroll
    def "uses #sequence version of plugin when added to buildscript classpath"() {
        when:
        if (version != BUILD_SCAN_DEFAULT_VERSION) {
            publishDummyBuildScanPlugin(version)
        }
        buildscriptApply "com.gradle:build-scan-plugin:$version"

        and:
        runBuildWithScanRequest()

        then:
        buildScanPluginApplied(version)

        where:
        sequence | version
        "older"  | "1.8"
        "same"   | BUILD_SCAN_DEFAULT_VERSION
        "newer"  | "1.10"
    }

    def "does not auto-apply buildscan plugin when explicitly requested and not applied"() {
        when:
        pluginsRequest "id 'com.gradle.build-scan' version '${BUILD_SCAN_DEFAULT_VERSION}' apply false"

        and:
        runBuildWithScanRequest()

        then:
        buildScanPluginNotApplied()
    }

    private void publishDummyBuildScanPlugin(String version) {
        def builder = new PluginBuilder(testDirectory.file('plugin-' + version))
        builder.addPlugin("""
            def gradle = project.gradle
            
            org.gradle.internal.scan.config.BuildScanPluginMetadata buildScanPluginMetadata = { "${version}" } as org.gradle.internal.scan.config.BuildScanPluginMetadata
            gradle.services.get(org.gradle.internal.scan.config.BuildScanConfigProvider).collect(buildScanPluginMetadata)
            
            gradle.buildFinished {
                println 'PUBLISHING BUILD SCAN v${version}'
            }
""", "com.gradle.build-scan", "DummyBuildScanPlugin")
        builder.publishAs("com.gradle:build-scan-plugin:${version}", mavenRepo, executer)
    }

    private void runBuildWithScanRequest() {
        args("--scan")
        succeeds("dummy")
    }

    private void runBuildWithoutScanRequest() {
        succeeds("dummy")
    }

    private void buildScanPluginApplied(String version) {
        assert output.contains("PUBLISHING BUILD SCAN v${version}")
    }

    private void buildScanPluginNotApplied() {
        assert !output.contains("PUBLISHING BUILD SCAN")
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
            apply plugin: 'com.gradle.build-scan'
""" + buildFile.text
    }
}
