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

package org.gradle.smoketests

import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.internal.VersionNumber
import org.junit.Assume
import spock.lang.IgnoreIf

// https://plugins.gradle.org/plugin/com.gradle.enterprise
class BuildScanPluginSmokeTest extends AbstractSmokeTest {

    private static final List<String> UNSUPPORTED = [
        "2.4.2",
        "2.4.1",
        "2.4",
        "2.3",
        "2.2.1",
        "2.2",
        "2.1",
        "2.0.2",
        "2.0.1",
        "2.0",
        "1.16",
        "1.15",
        "1.14"
    ]

    private static final List<String> SUPPORTED = [
        "3.0",
        "3.1",
        "3.1.1",
        "3.2",
        "3.2.1",
        "3.3",
        "3.3.1",
        "3.3.2",
        "3.3.3",
        "3.3.4",
        "3.4",
        "3.4.1",
        "3.5",
        "3.5.1",
        "3.5.2",
        "3.6",
        "3.6.1",
        "3.6.2",
        "3.6.3",
        "3.6.4",
        "3.7",
        "3.7.1",
        "3.7.2",
        "3.8",
        "3.8.1",
        "3.9",
        "3.10",
        "3.10.1",
        "3.10.2",
        "3.10.3",
        // "3.11", This doesn't work on Java 8, so let's not test it.
        "3.11.1",
        "3.11.2",
        "3.11.3",
        "3.11.4",
        "3.12",
        "3.12.1",
        "3.12.2",
        "3.12.3"
    ]

    private static final VersionNumber FIRST_VERSION_SUPPORTING_CONFIGURATION_CACHE = VersionNumber.parse("3.4")
    private static final VersionNumber FIRST_VERSION_SUPPORTING_GRADLE_8_CONFIGURATION_CACHE = VersionNumber.parse("3.12")

    @IgnoreIf({ !GradleContextualExecuter.configCache })
    def "can use plugin #version with Gradle 8 configuration cache"() {
        given:
        def versionNumber = VersionNumber.parse(version)
        Assume.assumeFalse(versionNumber < FIRST_VERSION_SUPPORTING_GRADLE_8_CONFIGURATION_CACHE)

        when:
        usePluginVersion version

        then:
        build().output.contains("Build scan written to")

        where:
        version << SUPPORTED
    }

    @IgnoreIf({ GradleContextualExecuter.configCache })
    def "can use plugin #version"() {
        given:
        def versionNumber = VersionNumber.parse(version)
        Assume.assumeFalse(versionNumber < FIRST_VERSION_SUPPORTING_CONFIGURATION_CACHE)

        when:
        usePluginVersion version

        then:
        build().output.contains("Build scan written to")

        where:
        version << SUPPORTED
    }

    def "cannot use plugin #version"() {
        when:
        usePluginVersion version

        and:
        def output = runner("--stacktrace")
            .buildAndFail().output

        then:
        output.contains(GradleEnterprisePluginManager.OLD_SCAN_PLUGIN_VERSION_MESSAGE)

        where:
        version << UNSUPPORTED
    }

    BuildResult build(String... args) {
        scanRunner(args).build()
    }

    GradleRunner scanRunner(String... args) {
        runner("build", "-Dscan.dump", *args).forwardOutput()
    }

    void usePluginVersion(String version) {
        def gradleEnterprisePlugin = VersionNumber.parse(version) >= VersionNumber.parse("3.0")
        if (gradleEnterprisePlugin) {
            settingsFile << """
                plugins {
                    id "com.gradle.enterprise" version "$version"
                }

                gradleEnterprise {
                    buildScan {
                        termsOfServiceUrl = 'https://gradle.com/terms-of-service'
                        termsOfServiceAgree = 'yes'
                    }
                }
            """
        } else {
            buildFile << """
                plugins {
                    id "com.gradle.build-scan" version "$version"
                }

                buildScan {
                    termsOfServiceUrl = 'https://gradle.com/terms-of-service'
                    termsOfServiceAgree = 'yes'
                }
            """
        }

        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}

            dependencies {
                testImplementation 'junit:junit:4.13'
            }
        """

        file("src/main/java/MySource.java") << """
            public class MySource {
                public static boolean isTrue() { return true; }
            }
        """

        file("src/test/java/MyTest.java") << """
            import org.junit.*;

            public class MyTest {
               @Test
               public void test() {
                  Assert.assertTrue(MySource.isTrue());
               }
            }
        """
    }
}
