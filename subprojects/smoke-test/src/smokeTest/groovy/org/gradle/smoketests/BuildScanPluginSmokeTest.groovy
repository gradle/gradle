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


import org.gradle.internal.scan.config.BuildScanPluginCompatibility
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.VersionNumber
import spock.lang.Unroll

import static org.gradle.internal.scan.config.BuildScanPluginCompatibility.FIRST_GRADLE_ENTERPRISE_PLUGIN_VERSION

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
        "3.2"
    ]

    @Unroll
    "can use plugin #version"() {
        when:
        usePluginVersion version

        then:
        build().output.contains("Build scan written to")

        where:
        version << SUPPORTED
    }

    @Unroll
    "cannot use plugin #version"() {
        when:
        usePluginVersion version

        and:
        def output = buildAndFail().output

        then:
        output.contains(BuildScanPluginCompatibility.OLD_SCAN_PLUGIN_VERSION_MESSAGE)

        where:
        version << UNSUPPORTED
    }

    BuildResult build(String... args) {
        scanRunner(args).build()
    }

    BuildResult buildAndFail(String... args) {
        scanRunner(args).buildAndFail()
    }

    GradleRunner scanRunner(String... args) {
        runner("build", "-Dscan.dump", *args).forwardOutput()
    }

    void usePluginVersion(String version) {
        def gradleEnterprisePlugin = VersionNumber.parse(version) >= FIRST_GRADLE_ENTERPRISE_PLUGIN_VERSION
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
            ${jcenterRepository()}

            dependencies {
                testImplementation 'junit:junit:4.12'
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
