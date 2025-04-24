/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.plugins

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes

class JavaBaseJavaExecIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        settingsFile << """
            rootProject.name = 'java-base-javaexec'
            include 'lib'
        """

        file('lib/src/main/java/VersionCheck.java') << """
            public class VersionCheck {
                public static void main(String[] args) {
                    System.out.println("Java " + System.getProperty("java.version"));
                }
            }
        """

        executer.withArgument("-Porg.gradle.java.installations.auto-detect=true")
    }

    def "javaexec defaults to java toolchain when java-base plugin is applied"() {
        def differentVersionMetadata = AvailableJavaHomes.getJvmInstallationMetadata(AvailableJavaHomes.differentVersion)
        assert differentVersionMetadata.languageVersion != JavaVersion.current()

        given:
        expectedVersion(differentVersionMetadata.languageVersion)
        buildFile << """
            plugins {
                id 'java-base'
            }

            configurations {
                libs
            }

            dependencies {
                libs project(':lib')
            }

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${differentVersionMetadata.languageVersion.majorVersion})
                }
            }

            tasks.register("javaVersion", JavaExec) {
                mainClass = "VersionCheck"
                classpath = configurations.libs
            }
        """

        when:
        def result = run("javaVersion")

        then:
        def javaVersion = javaVersionFrom(result.groupedOutput.task(":javaVersion").output)
        javaVersion == differentVersionMetadata.languageVersion
        javaVersion != JavaVersion.current()
    }

    def "can configure javaexec to use explicit java toolchain when java-base plugin is applied"() {
        def differentVersionMetadata = AvailableJavaHomes.getJvmInstallationMetadata(AvailableJavaHomes.differentVersion)
        assert differentVersionMetadata.languageVersion != JavaVersion.current()

        given:
        expectedVersion(JavaVersion.current())
        buildFile << """
            plugins {
                id 'java-base'
            }

            configurations {
                libs
            }

            dependencies {
                libs project(':lib')
            }

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${differentVersionMetadata.languageVersion.majorVersion})
                }
            }

            tasks.register("javaVersion", JavaExec) {
                mainClass = "VersionCheck"
                classpath = configurations.libs
                javaLauncher = javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of(${JavaVersion.current().majorVersion})
                }
            }
        """

        when:
        def result = run("javaVersion")

        then:
        def javaVersion = javaVersionFrom(result.groupedOutput.task(":javaVersion").output)
        javaVersion != differentVersionMetadata.languageVersion
        javaVersion == JavaVersion.current()
    }

    def "javaexec defaults to current java when java-base plugin is applied but toolchain is not set"() {
        given:
        expectedVersion(JavaVersion.current())
        buildFile << """
            plugins {
                id 'java-base'
            }

            configurations {
                libs
            }

            dependencies {
                libs project(':lib')
            }

            tasks.register("javaVersion", JavaExec) {
                mainClass = "VersionCheck"
                classpath = configurations.libs
            }
        """

        when:
        def result = run("javaVersion")

        then:
        def javaVersion = javaVersionFrom(result.groupedOutput.task(":javaVersion").output)
        javaVersion == JavaVersion.current()
    }

    def "javaexec defaults to current java when java-base plugin is not applied"() {
        given:
        expectedVersion(JavaVersion.current())
        buildFile << """
            configurations {
                libs
            }

            dependencies {
                libs project(':lib')
            }

            tasks.register("javaVersion", JavaExec) {
                mainClass = "VersionCheck"
                classpath = configurations.libs
            }
        """

        when:
        def result = run("javaVersion")

        then:
        def javaVersion = javaVersionFrom(result.groupedOutput.task(":javaVersion").output)
        javaVersion == JavaVersion.current()
    }

    private void expectedVersion(JavaVersion expectedVersion) {
        file('lib/build.gradle') << """
            plugins {
                id 'java'
            }

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${expectedVersion.majorVersion})
                }
            }
        """
    }

    private JavaVersion javaVersionFrom(String output) {
        def javaVersionString = output.split("Java ")[1].trim()
        return JavaVersion.toVersion(javaVersionString)
    }
}
