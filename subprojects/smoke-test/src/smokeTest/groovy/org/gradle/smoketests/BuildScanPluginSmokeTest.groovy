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

class BuildScanPluginSmokeTest extends AbstractSmokeTest {

    private static final List<String> GRACEFULLY_UNSUPPORTED = [
        "1.6",
        "1.7",
        "1.7.4",
    ]

    private static final List<String> SUPPORTED = [
        "1.8",
        "1.9",
        "1.9.1",
        "1.10",
        "1.10.1",
        "1.10.2",
        "1.10.3",
        "1.11",
        "1.12",
        "1.12.1",
        "1.13",
        "1.13.1",
        "1.13.2",
        "1.13.3",
        "1.13.4",
        "1.14",
        "1.15.1"
    ]

    @Unroll
    "can run build with build scan plugin #version"() {
        when:
        usePluginVersion version
        useRepoMirror = (version != '1.9.1') // https://github.com/gradle/dotcom/issues/1213


        then:
        build().output.contains("Build scan written to")

        where:
        version << SUPPORTED
    }

    @Unroll
    "gracefully fails with unsupported version #version"() {
        when:
        usePluginVersion version

        then:
        buildAndFail("--scan").output.contains("""
> Failed to apply plugin [id 'com.gradle.build-scan']
   > This version of Gradle requires version 1.8.0 of the build scan plugin or later.
     Please see https://gradle.com/scans/help/gradle-incompatible-plugin-version for more information.
""")

        where:
        version << GRACEFULLY_UNSUPPORTED
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
                licenseAgreementUrl = 'https://gradle.com/terms-of-service'
                licenseAgree = 'yes'
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
