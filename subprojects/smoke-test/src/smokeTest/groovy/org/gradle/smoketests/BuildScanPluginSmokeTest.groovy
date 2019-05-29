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

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Unroll

import static org.gradle.internal.scan.config.BuildScanPluginCompatibility.MIN_SUPPORTED_VERSION

class BuildScanPluginSmokeTest extends AbstractSmokeTest {

    private static final List<String> UNSUPPORTED = [
            "2.0.1",
            "2.0",
            "1.16",
            "1.15",
            "1.14"
    ]

    private static final List<String> SUPPORTED = [
            "2.3",
            "2.2.1",
            "2.2",
            "2.1",
            "2.0.2"
    ]

    @Unroll
    "can run build with build scan plugin #version"() {
        when:
        usePluginVersion version

        then:
        build().output.contains("Build scan written to")

        where:
        version << SUPPORTED
    }

    @Unroll
    "fail without capturing scan with unsupported version #version"() {
        when:
        usePluginVersion version

        and:
        def output = buildAndFail("--scan").output

        then:
        output.contains("This version of Gradle requires version $MIN_SUPPORTED_VERSION of the build scan plugin or later.")

        and:
        output.contains("Please see https://gradle.com/scans/help/gradle-incompatible-plugin-version for more information.")

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
        buildFile << """
            buildscript {
                repositories {
                    gradlePluginPortal()
                }
                dependencies {
                    classpath "com.gradle:build-scan-plugin:${version}"
                }
            }

            apply plugin: "com.gradle.build-scan"
            buildScan {
                termsOfServiceUrl = 'https://gradle.com/terms-of-service'
                termsOfServiceAgree = 'yes'
            }

            apply plugin: 'java'
            ${jcenterRepository()}

            dependencies {
                testCompile 'junit:junit:4.12'
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
