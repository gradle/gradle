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

import groovy.io.FileType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestFile

class JavaToolchainDownloadIntegrationTest extends AbstractIntegrationSpec {

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def "download and provisioning works end-to-end"() { //TODO (#21082): this test actually downloads a JDK from Adoptium... can we leave it like that?
        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(8)
                }
            }
        """

        file("src/main/java/Foo.java") << "public class Foo {}"


        when:
        ExecutionResult result = executer
                .withTasks("compileJava")
                .requireOwnGradleUserHomeDir()
                .withToolchainDownloadEnabled()
                .run()


        then:

        TestFile installLocation = temporaryFolder.file("user-home", "jdks", "temurin-8-x86_64-${osInFilename()}")
        if (!installLocation.isDirectory()) { //TODO (#21082): hack to debug on other OSs, remove
            def list = []

            def dir = temporaryFolder.file("user-home", "jdks").canonicalFile
            dir.eachFileRecurse (FileType.FILES) { file ->
                list << file
            }

            throw new RuntimeException("Not found: " + list.join("\n\t"))
        }

        File[] subFolders = installLocation.getCanonicalFile().listFiles()
        subFolders.length == 1
        TestFile firstSubFolder = installLocation.file(subFolders[0].name)
        firstSubFolder.isDirectory()

        TestFile marker = firstSubFolder.file("provisioned.ok")
        marker.isFile()
    }

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def "can properly fails for missing combination"() {
        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(99)
                }
            }
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        failure = executer
            .withTasks("compileJava")
            .requireOwnGradleUserHomeDir()
            .withToolchainDetectionEnabled()
            .withToolchainDownloadEnabled()
            .runWithFailure()

        then:
        failure.assertHasDescription("Execution failed for task ':compileJava'.")
            .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'")
            .assertHasCause("Unable to download toolchain matching the requirements ({languageVersion=99, vendor=any, implementation=vendor-specific}) from: https://api.adoptium.net/v3/binary/latest/99/ga/${osInUri()}/x64/jdk/hotspot/normal/eclipse")
            .assertHasCause("Could not read 'https://api.adoptium.net/v3/binary/latest/99/ga/${osInUri()}/x64/jdk/hotspot/normal/eclipse' as it does not exist.")
    }

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def 'toolchain selection that requires downloading fails when it is disabled'() {
        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(14)
                }
            }
        """

        propertiesFile << """
            org.gradle.java.installations.auto-download=false
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        failure = executer
            .withTasks("compileJava")
            .requireOwnGradleUserHomeDir()
            .runWithFailure()

        then:
        failure.assertHasDescription("Execution failed for task ':compileJava'.")
            .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'")
            .assertHasCause("No compatible toolchains found for request filter: {languageVersion=14, vendor=any, implementation=vendor-specific} (auto-detect false, auto-download false)")
    }

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def 'toolchain download on http fails'() {
        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(99)
                }
            }
        """

        propertiesFile << """
            org.gradle.jvm.toolchain.install.adoptopenjdk.baseUri=http://example.com
            org.gradle.jvm.toolchain.install.adoptium.baseUri=http://example.com
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        failure = executer
            .withTasks("compileJava")
            .requireOwnGradleUserHomeDir()
            .withToolchainDetectionEnabled()
            .withToolchainDownloadEnabled()
            .runWithFailure()

        then:
        failure.assertHasDescription("Execution failed for task ':compileJava'.")
            .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'")
            .assertHasCause("Unable to download toolchain matching the requirements ({languageVersion=99, vendor=any, implementation=vendor-specific}) from: http://example.com/v3/binary/latest/99/ga/${osInUri()}/x64/jdk/hotspot/normal/eclipse")
            .assertHasCause("Attempting to download a JDK from an insecure URI http://example.com/v3/binary/latest/99/ga/${osInUri()}/x64/jdk/hotspot/normal/eclipse. This is not supported, use a secure URI instead.")
    }

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def 'toolchain download of AdoptOpenJDK emits deprecation warning'() {
        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(99)
                    vendor = JvmVendorSpec.ADOPTOPENJDK
                }
            }
        """

        propertiesFile << """
            org.gradle.jvm.toolchain.install.adoptopenjdk.baseUri=https://example.com
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        failure = executer
            .withTasks("compileJava")
            .requireOwnGradleUserHomeDir()
            .withToolchainDetectionEnabled()
            .withToolchainDownloadEnabled()
            .expectDeprecationWarning('Due to changes in AdoptOpenJDK download endpoint, downloading a JDK with an explicit vendor of AdoptOpenJDK should be replaced with a spec without a vendor or using Eclipse Temurin / IBM Semeru.')
            .runWithFailure()

        then:
        failure.assertHasDescription("Execution failed for task ':compileJava'.")
            .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'")
            .assertHasCause("Unable to download toolchain matching the requirements ({languageVersion=99, vendor=ADOPTOPENJDK, implementation=vendor-specific}) from: https://api.adoptium.net/v3/binary/latest/99/ga/${osInUri()}/x64/jdk/hotspot/normal/eclipse")
    }

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def 'toolchain download of Adoptium does not emit deprecation warning'() {
        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(99)
                    vendor = JvmVendorSpec.ADOPTIUM
                }
            }
        """

        propertiesFile << """
            org.gradle.jvm.toolchain.install.adoptopenjdk.baseUri=https://example.com
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        failure = executer
            .withTasks("compileJava")
            .requireOwnGradleUserHomeDir()
            .withToolchainDetectionEnabled()
            .withToolchainDownloadEnabled()
            .runWithFailure()

        then:
        failure.assertHasDescription("Execution failed for task ':compileJava'.")
            .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'")
            .assertHasCause("Unable to download toolchain matching the requirements ({languageVersion=99, vendor=ADOPTIUM, implementation=vendor-specific}) from: https://api.adoptium.net/v3/binary/latest/99/ga/${osInUri()}/x64/jdk/hotspot/normal/eclipse")
    }

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def 'toolchain download of Semeru forces openj9'() {
        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(99)
                    vendor = JvmVendorSpec.IBM_SEMERU
                }
            }
        """

        propertiesFile << """
            org.gradle.jvm.toolchain.install.adoptopenjdk.baseUri=https://example.com
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        failure = executer
            .withTasks("compileJava")
            .requireOwnGradleUserHomeDir()
            .withToolchainDetectionEnabled()
            .withToolchainDownloadEnabled()
            .runWithFailure()

        then:
        failure.assertHasDescription("Execution failed for task ':compileJava'.")
            .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'")
            .assertHasCause("Unable to download toolchain matching the requirements ({languageVersion=99, vendor=IBM_SEMERU, implementation=vendor-specific}) from: https://example.com/v3/binary/latest/99/ga/${osInUri()}/x64/jdk/openj9/normal/adoptopenjdk")
            .assertHasCause("Could not read 'https://example.com/v3/binary/latest/99/ga/${osInUri()}/x64/jdk/openj9/normal/adoptopenjdk' as it does not exist.")
    }

    private static String osInUri() {
        OperatingSystem os = OperatingSystem.current()
        if (os.isWindows()) {
            return "windows";
        } else if (os.isMacOsX()) {
            return "mac";
        } else if (os.isLinux()) {
            return "linux";
        }
        return os.getFamilyName();
    }

    private static String osInFilename() {
        OperatingSystem os = OperatingSystem.current()
        return os.getFamilyName().replaceAll("[^a-zA-Z0-9\\-]", "_")
    }
}
