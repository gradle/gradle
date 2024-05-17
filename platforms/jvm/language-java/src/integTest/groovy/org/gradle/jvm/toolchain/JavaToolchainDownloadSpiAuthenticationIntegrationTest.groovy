/*
 * Copyright 2022 the original author or authors.
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
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.HttpServer
import org.junit.Rule

import static JavaToolchainDownloadUtil.applyToolchainResolverPlugin
import static JavaToolchainDownloadUtil.singleUrlResolverCode
import static org.gradle.jvm.toolchain.JavaToolchainDownloadUtil.DEFAULT_PLUGIN

class JavaToolchainDownloadSpiAuthenticationIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    HttpServer server

    TestFile toolchainArchive
    URI archiveUri

    def setup() {
        toolchainArchive = createZip('toolchain.zip') {
            file 'content.txt'
        }

        server.start()

        archiveUri = server.uri.resolve("/path/toolchain.zip")
    }

    def "can download without authentication"() {
        settingsFile << """
            ${applyToolchainResolverPlugin("CustomToolchainResolver", singleUrlResolverCode(archiveUri))}
        """

        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(99)
                    vendor = JvmVendorSpec.matching("exotic")
                }
            }
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        server.expectHead("/path/toolchain.zip", toolchainArchive)
        server.expectGet("/path/toolchain.zip", toolchainArchive)

        when:
        failure = executer
                .withTasks("compileJava")
                .requireOwnGradleUserHomeDir("needs to be able to provision fresh toolchains")
                .withToolchainDownloadEnabled()
                .runWithFailure()

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':compileJava'.")
               .assertHasCause("Could not resolve all dependencies for configuration ':compileClasspath'.")
               .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'.")
               .assertHasCause("Cannot find a Java installation on your machine matching this tasks requirements: {languageVersion=99, vendor=matching('exotic'), implementation=vendor-specific} for")
                .assertHasCause("No matching toolchain could be found in the locally installed toolchains or the configured toolchain download repositories. " +
                    "Some toolchain resolvers had provisioning failures: custom (Unable to download toolchain matching the requirements " +
                    "({languageVersion=99, vendor=matching('exotic'), implementation=vendor-specific}) from '$archiveUri', " +
                    "due to: Unpacked JDK archive does not contain a Java home: " + temporaryFolder.testDirectory.file("user-home", ".tmp", "jdks", "toolchain"))
    }

    def "can download with basic authentication"() {
        settingsFile <<
            applyToolchainResolverPlugin("CustomToolchainResolver", singleUrlResolverCode(archiveUri), DEFAULT_PLUGIN,
                """
                    toolchainManagement {
                        jvm {
                            javaRepositories {
                                repository('custom') {
                                    resolverClass = CustomToolchainResolver
                                    credentials {
                                        username "user"
                                        password "password"
                                    }
                                    authentication {
                                        digest(BasicAuthentication)
                                    }
                                }
                            }
                        }
                    }
                """
        )

        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(99)
                    vendor = JvmVendorSpec.matching("exotic")
                }
            }
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        server.expectHead("/path/toolchain.zip", "user", "password", toolchainArchive)
        server.expectGet("/path/toolchain.zip", "user", "password", toolchainArchive)

        when:
        failure = executer
                .withTasks("compileJava")
                .requireOwnGradleUserHomeDir("needs to be able to provision fresh toolchains")
                .withToolchainDownloadEnabled()
                .runWithFailure()

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':compileJava'.")
            .assertHasCause("Could not resolve all dependencies for configuration ':compileClasspath'.")
            .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'.")
            .assertHasCause("Cannot find a Java installation on your machine matching this tasks requirements: {languageVersion=99, vendor=matching('exotic'), implementation=vendor-specific} for")
            .assertHasCause("No matching toolchain could be found in the locally installed toolchains or the configured toolchain download repositories. " +
                "Some toolchain resolvers had provisioning failures: custom (Unable to download toolchain matching the requirements " +
                "({languageVersion=99, vendor=matching('exotic'), implementation=vendor-specific}) from '$archiveUri', " +
                "due to: Unpacked JDK archive does not contain a Java home: " + temporaryFolder.testDirectory.file("user-home", ".tmp", "jdks", "toolchain"))
    }
}
