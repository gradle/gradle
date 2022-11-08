/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.jvm.toolchain

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class JavaToolchainDownloadSoakTest extends AbstractIntegrationSpec {

    def setup() {
        settingsFile << """
            pluginManagement {
                repositories {
                    maven {
                        url 'https://plugins.grdev.net/m2/'
                    }
                }
            }

            plugins {
                id 'org.gradle.disco-toolchains' version '0.1'
            }
            
            toolchainManagement {
                jvm {
                    javaRepositories {
                        repository('disco') {
                            resolverClass = org.gradle.disco.DiscoToolchainResolver
                        }
                    }
                }
            }
        """ //TODO (#22138): use PROD portal for plugin, when published

        buildFile << """
            plugins {
                id "java"
            }

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(16)
                }
            }
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        executer.requireOwnGradleUserHomeDir()
        executer
            .withToolchainDetectionEnabled()
            .withToolchainDownloadEnabled()
    }

    def "can download missing jdk automatically"() {
        when:
        result = executer
                .withTasks("compileJava", "-Porg.gradle.java.installations.auto-detect=false")
                .run()

        then:
        javaClassFile("Foo.class").assertExists()
        assertJdkWasDownloaded("eclipse_foundation")
    }

    def "can download missing j9 jdk automatically"() {
        buildFile << """
            java {
                toolchain {
                    implementation = JvmImplementation.J9
                }
            }
        """

        when:
        result = executer
               .withTasks("compileJava", "-Porg.gradle.java.installations.auto-detect=false")
               .run()

        then:
        javaClassFile("Foo.class").assertExists()
        assertJdkWasDownloaded("openj9")
    }

    private void assertJdkWasDownloaded(String implementation) {
        assert executer.gradleUserHomeDir.file("jdks").listFiles({ file ->
            file.name.contains("-16-") && file.name.contains(implementation)
        } as FileFilter)
    }

    def cleanup() {
        executer.gradleUserHomeDir.file("jdks").deleteDir()
    }
}
