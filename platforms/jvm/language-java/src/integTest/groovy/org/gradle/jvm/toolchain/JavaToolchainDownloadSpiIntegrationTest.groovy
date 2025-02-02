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

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.DocumentationUtils
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.junit.Assume
import spock.lang.Issue

import static JavaToolchainDownloadUtil.applyToolchainResolverPlugin
import static JavaToolchainDownloadUtil.noUrlResolverCode
import static org.gradle.integtests.fixtures.SuggestionsMessages.GET_HELP
import static org.gradle.integtests.fixtures.SuggestionsMessages.INFO_DEBUG
import static org.gradle.integtests.fixtures.SuggestionsMessages.SCAN
import static org.gradle.integtests.fixtures.SuggestionsMessages.STACKTRACE_MESSAGE
import static org.gradle.jvm.toolchain.JavaToolchainDownloadUtil.DEFAULT_PLUGIN
import static org.gradle.jvm.toolchain.JavaToolchainDownloadUtil.NO_RESOLVER
import static org.gradle.jvm.toolchain.JavaToolchainDownloadUtil.NO_TOOLCHAIN_MANAGEMENT
import static org.gradle.jvm.toolchain.JavaToolchainDownloadUtil.singleUrlResolverCode

class JavaToolchainDownloadSpiIntegrationTest extends AbstractIntegrationSpec {

    def "can inject custom toolchain registry via settings plugin"() {
        settingsFile << """
            ${applyToolchainResolverPlugin("CustomToolchainResolver", customToolchainResolverCode())}
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

        when:
        failure = executer
                .withTasks("compileJava", "-s")
                .requireOwnGradleUserHomeDir("needs to not have cached toolchains")
                .withToolchainDownloadEnabled()
                .runWithFailure()

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':compileJava'.")
               .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'.")
               .assertHasCause("Cannot find a Java installation on your machine (${OperatingSystem.current()}) matching: {languageVersion=99, vendor=vendor matching('exotic'), implementation=vendor-specific}. " +
                   "Some toolchain resolvers had provisioning failures: custom (Unable to download toolchain matching the requirements " +
                   "({languageVersion=99, vendor=vendor matching('exotic'), implementation=vendor-specific}) from 'https://exoticJavaToolchain.com/java-99', " +
                   "due to: Could not HEAD 'https://exoticJavaToolchain.com/java-99'.).")
    }

    def "downloaded JDK is checked against the spec"() {
        given:
        def jdkRepository = new JdkRepository(Jvm.current().javaVersion)
        def uri = jdkRepository.start()
        jdkRepository.reset()

        settingsFile << """
            ${applyToolchainResolverPlugin("CustomToolchainResolver", singleUrlResolverCode(uri))}
        """

        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(11)
                }
            }
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        failure = executer
                .withTasks("compileJava")
                .requireOwnGradleUserHomeDir("needs to not have cached toolchains")
                .withToolchainDownloadEnabled()
                .runWithFailure()

        and:
        jdkRepository.stop()

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':compileJava'.")
               .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'.")
               .assertHasCause("Cannot find a Java installation on your machine (${OperatingSystem.current()}) matching: {languageVersion=11, vendor=any vendor, implementation=vendor-specific}. " +
                   "Some toolchain resolvers had provisioning failures: custom (Unable to download toolchain matching the requirements ({languageVersion=11, vendor=any vendor, implementation=vendor-specific}) " +
                   "from '$uri', due to: Toolchain provisioned from '$uri' doesn't satisfy the specification: {languageVersion=11, vendor=any vendor, implementation=vendor-specific} and must have the executable 'javac' and the executable 'javadoc'.)")
    }

    @Issue("https://github.com/gradle/gradle/issues/30409")
    def "no temporary JDK path is logged when provisioning a JDK"() {
        given:
        def otherJavaVersion = JavaVersion.current() == JavaVersion.VERSION_17 ? JavaVersion.VERSION_21 : JavaVersion.VERSION_17
        def jdkRepository = new JdkRepository(otherJavaVersion)
        def uri = jdkRepository.start()
        jdkRepository.reset()

        settingsFile << """
            ${applyToolchainResolverPlugin("CustomToolchainResolver", singleUrlResolverCode(uri))}
        """

        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${otherJavaVersion.majorVersion})
                }
            }
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        result = executer
            .withTasks("compileJava", "-s")
            .requireOwnGradleUserHomeDir("needs to not have cached toolchains")
            .withToolchainDownloadEnabled()
            .run()

        and:
        jdkRepository.stop()


        then:
        // There is no reason we should log the temporary JDK path, unless we're reporting a warning to the user that we shouldn't be
        def testPath = ".tmp/jdks/"
        if (OperatingSystem.current().isWindows()) {
            testPath = testPath.replace('/', '\\')
        }
        result.assertNotOutput(testPath)
    }

    def "custom toolchain registries are consulted in order"() {
        settingsFile << """
            ${applyToolchainResolverPlugin("CustomToolchainResolver", customToolchainResolverCode(), DEFAULT_PLUGIN, NO_TOOLCHAIN_MANAGEMENT)}
            ${applyToolchainResolverPlugin("UselessToolchainResolver", noUrlResolverCode(), DEFAULT_PLUGIN,
                """
                    toolchainManagement {
                        jvm {
                            javaRepositories {
                                repository('useless') {
                                    resolverClass = UselessToolchainResolver
                                }
                                repository('custom') {
                                    resolverClass = CustomToolchainResolver
                                }
                            }
                        }
                    }
                """
            )}
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

        when:
        failure = executer
                .withTasks("compileJava")
                .requireOwnGradleUserHomeDir("needs to not have cached toolchains")
                .withToolchainDownloadEnabled()
                .runWithFailure()

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':compileJava'.")
               .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'.")
               .assertHasCause("Cannot find a Java installation on your machine (${OperatingSystem.current()}) matching: {languageVersion=99, vendor=vendor matching('exotic'), implementation=vendor-specific}. " +
                   "Some toolchain resolvers had provisioning failures: custom (Unable to download toolchain matching the requirements " +
                   "({languageVersion=99, vendor=vendor matching('exotic'), implementation=vendor-specific}) from 'https://exoticJavaToolchain.com/java-99', " +
                   "due to: Could not HEAD 'https://exoticJavaToolchain.com/java-99'.).")
    }

    def "fails on registration collision"() {
        settingsFile << """
            ${applyToolchainResolverPlugin("UselessToolchainResolver", NO_RESOLVER, "UselessPlugin1", NO_TOOLCHAIN_MANAGEMENT)}
            ${applyToolchainResolverPlugin("UselessToolchainResolver", noUrlResolverCode(), "UselessPlugin2",
                """
                    toolchainManagement {
                        jvm {
                            javaRepositories {
                                repository('useless') {
                                    resolverClass = UselessToolchainResolver1
                                }
                                repository('useless') {
                                    resolverClass = UselessToolchainResolver2
                                }
                            }
                        }
                    }
                """)}
        """

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
                .requireOwnGradleUserHomeDir("needs to not have cached toolchains")
                .withToolchainDownloadEnabled()
                .runWithFailure()

        then:
        failure.assertHasCause("Failed to apply plugin class 'UselessPlugin2'.")
                .assertHasCause("Duplicate registration for 'UselessToolchainResolver'.")
    }

    def "fails on implementation class collision"() {
        settingsFile << """
            ${applyToolchainResolverPlugin("UselessToolchainResolver", noUrlResolverCode(), DEFAULT_PLUGIN,
                """
                    toolchainManagement {
                        jvm {
                            javaRepositories {
                                repository('useless1') {
                                    resolverClass = UselessToolchainResolver
                                }
                                repository('useless2') {
                                    resolverClass = UselessToolchainResolver
                                }
                            }
                        }
                    }
                """
            )}
        """

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
                .requireOwnGradleUserHomeDir("needs to not have cached toolchains")
                .withToolchainDownloadEnabled()
                .runWithFailure()

        then:
        failure.assertHasDescription("Duplicate configuration for repository implementation 'UselessToolchainResolver'.")
    }

    def "fails on repository name collision"() {
        settingsFile << """
            ${applyToolchainResolverPlugin("UselessToolchainResolver1", noUrlResolverCode(), DEFAULT_PLUGIN, NO_TOOLCHAIN_MANAGEMENT)}
            ${applyToolchainResolverPlugin("UselessToolchainResolver2", noUrlResolverCode(), DEFAULT_PLUGIN,
                """
                    toolchainManagement {
                        jvm {
                            javaRepositories {
                                repository('useless') {
                                    resolverClass = UselessToolchainResolver1
                                }
                                repository('useless') {
                                    resolverClass = UselessToolchainResolver2
                                }
                            }
                        }
                    }
                """
            )}
        """

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
                .requireOwnGradleUserHomeDir("needs to not have cached toolchains")
                .withToolchainDownloadEnabled()
                .runWithFailure()

        then:
        failure.assertHasCause("Duplicate configuration for repository 'useless'.")
    }

    def "list of requested repositories can be queried"() {
        settingsFile << """
            ${applyToolchainResolverPlugin("UselessToolchainResolver1", noUrlResolverCode(), DEFAULT_PLUGIN, NO_TOOLCHAIN_MANAGEMENT)}
            ${applyToolchainResolverPlugin("UselessToolchainResolver2", noUrlResolverCode(), DEFAULT_PLUGIN, NO_TOOLCHAIN_MANAGEMENT)}
            ${applyToolchainResolverPlugin("UselessToolchainResolver3", noUrlResolverCode(), DEFAULT_PLUGIN,
                """
                    toolchainManagement {
                        jvm {
                            javaRepositories {
                                repository('useless3') {
                                    resolverClass = UselessToolchainResolver3
                                }
                                repository('useless1') {
                                    resolverClass = UselessToolchainResolver1
                                }
                            }
                        }
                    }
                """
            )}

            println(\"\"\"Explicitly requested toolchains: \${toolchainManagement.jvm.getJavaRepositories().getAsList().collect { it.getName() }}.\"\"\")
        """

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
                .requireOwnGradleUserHomeDir("needs to not have cached toolchains")
                .withToolchainDownloadEnabled()
                .runWithFailure()

        then:
        failure.getOutput().contains("Explicitly requested toolchains: [useless3, useless1].")
    }

    def "created repository can be removed"() {
        settingsFile << """
            ${applyToolchainResolverPlugin("UselessToolchainResolver1", noUrlResolverCode(), DEFAULT_PLUGIN, NO_TOOLCHAIN_MANAGEMENT)}
            ${applyToolchainResolverPlugin("UselessToolchainResolver2", noUrlResolverCode(), DEFAULT_PLUGIN, NO_TOOLCHAIN_MANAGEMENT)}
            ${applyToolchainResolverPlugin("UselessToolchainResolver3", noUrlResolverCode(), DEFAULT_PLUGIN,
                """
                    toolchainManagement {
                        jvm {
                            javaRepositories {
                                repository('useless1') {
                                    resolverClass = UselessToolchainResolver1
                                }
                                repository('useless2') {
                                    resolverClass = UselessToolchainResolver2
                                }
                                repository('useless3') {
                                    resolverClass = UselessToolchainResolver3
                                }
                            }
                        }
                    }
                """
            )}

            toolchainManagement.jvm.javaRepositories.remove('useless2')

            println(\"\"\"Explicitly requested toolchains: \${toolchainManagement.jvm.getJavaRepositories().getAsList().collect { it.getName() }}.\"\"\")
        """

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
                .requireOwnGradleUserHomeDir("needs to not have cached toolchains")
                .withToolchainDownloadEnabled()
                .runWithFailure()

        then:
        failure.getOutput().contains("Explicitly requested toolchains: [useless1, useless3].")
    }

    def "cannot mutate repository rules after settings have been evaluated"() {
        settingsFile << """
            ${applyToolchainResolverPlugin("UselessToolchainResolver", noUrlResolverCode())}
        """

        buildFile << """
            gradle.settings.toolchainManagement.jvm.javaRepositories.remove('useless')
        """

        when:
        fails ":help"

        then:
        failure.assertHasCause("Mutation of toolchain repositories declared in settings is only allowed during settings evaluation")
    }

    def "throws informative error on repositories not being configured"() {
        settingsFile << """
            ${applyToolchainResolverPlugin("CustomToolchainResolver", customToolchainResolverCode(), DEFAULT_PLUGIN, NO_TOOLCHAIN_MANAGEMENT)}
        """

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
                .requireOwnGradleUserHomeDir("needs to not have cached toolchains")
                .withToolchainDownloadEnabled()
                .runWithFailure()

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':compileJava'.")
               .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'.")
               .assertHasCause("Cannot find a Java installation on your machine (${OperatingSystem.current()}) matching: {languageVersion=99, vendor=any vendor, implementation=vendor-specific}. " +
                   "Toolchain download repositories have not been configured.")
               .assertHasResolutions(
                   DocumentationUtils.normalizeDocumentationLink("Learn more about toolchain auto-detection and auto-provisioning at https://docs.gradle.org/current/userguide/toolchains.html#sec:auto_detection."),
                   DocumentationUtils.normalizeDocumentationLink("Learn more about toolchain repositories at https://docs.gradle.org/current/userguide/toolchains.html#sub:download_repositories."),
                   STACKTRACE_MESSAGE,
                   INFO_DEBUG,
                   SCAN,
                   GET_HELP)
    }

    def "logs informative warning message if some repositories fail to resolve the toolchain spec #logLevel"(String logLevel, Closure<ExecutionResult> test) {
        given:
        Assume.assumeFalse(JavaVersion.current() == JavaVersion.VERSION_11)

        def jdkRepository = new JdkRepository(JavaVersion.VERSION_11)
        def uri = jdkRepository.start()
        jdkRepository.reset()

        settingsFile << """
            ${applyToolchainResolverPlugin("FailsToResolveResolver", failsToResolveResolverCode(), DEFAULT_PLUGIN, NO_TOOLCHAIN_MANAGEMENT)}
            ${applyToolchainResolverPlugin("FailsToProvisionResolver", failsToProvisionResolverCode(), DEFAULT_PLUGIN, NO_TOOLCHAIN_MANAGEMENT)}
            ${applyToolchainResolverPlugin("GoodResolver", singleUrlResolverCode(uri), DEFAULT_PLUGIN,
            """
                    toolchainManagement {
                        jvm {
                            javaRepositories {
                                repository('failsOnResolve') {
                                    resolverClass = FailsToResolveResolver
                                }
                                repository('failsOnProvision') {
                                    resolverClass = FailsToProvisionResolver
                                }
                                repository('good') {
                                    resolverClass = GoodResolver
                                }
                            }
                        }
                    }
                """
        )}
        """

        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(11)
                }
            }
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        def result = executer
            .withTasks("compileJava")
            .withArgument("--$logLevel")
            .requireOwnGradleUserHomeDir("needs to not have cached toolchains")
            .withToolchainDownloadEnabled()
            .withStackTraceChecksDisabled()
            .run()

        and:
        jdkRepository.stop()

        then:
        test(result)

        where:
        logLevel | test
        "warn"   | { ExecutionResult r -> r.assertOutputContains("Some toolchain resolvers had internal failures: failsOnResolve (Ooops!). " +
            "Some toolchain resolvers had provisioning failures: failsOnProvision (Unable to download toolchain matching the requirements ({languageVersion=11, vendor=any vendor, implementation=vendor-specific}) from 'http://exoticJavaToolchain.com/java-11', due to: Attempting to download java toolchain from an insecure URI http://exoticJavaToolchain.com/java-11. This is not supported, use a secure URI instead.). Switch logging level to DEBUG (--debug) for further information.")
        }
        "debug"  | { ExecutionResult r -> r.assertOutputContains("Some toolchain resolvers had internal failures: failsOnResolve (Ooops!). Some toolchain resolvers had provisioning failures: failsOnProvision (Unable to download toolchain matching the requirements ({languageVersion=11, vendor=any vendor, implementation=vendor-specific}) from 'http://exoticJavaToolchain.com/java-11', due to: Attempting to download java toolchain from an insecure URI http://exoticJavaToolchain.com/java-11. This is not supported, use a secure URI instead.)")
            .assertOutputContains("Suppressed: java.lang.Exception: Ooops!")
            .assertOutputContains("Suppressed: org.gradle.jvm.toolchain.internal.install.exceptions.ToolchainDownloadException: Unable to download toolchain matching the requirements ({languageVersion=11, vendor=any vendor, implementation=vendor-specific}) from 'http://exoticJavaToolchain.com/java-11', due to: Attempting to download java toolchain from an insecure URI http://exoticJavaToolchain.com/java-11. This is not supported, use a secure URI instead.")
        }
    }

    private static String failsToResolveResolverCode() {
        // fail by throwing an exception on resolution
        """
            @Override
            public Optional<JavaToolchainDownload> resolve(JavaToolchainRequest request) {
                throw new Exception("Ooops!");
            }
        """
    }

    private static String failsToProvisionResolverCode() {
        // can't be provisioned due to unsecure HTTP connection URL
        """
            @Override
            public Optional<JavaToolchainDownload> resolve(JavaToolchainRequest request) {
                URI uri = URI.create("http://exoticJavaToolchain.com/java-" + request.getJavaToolchainSpec().getLanguageVersion().get());
                return Optional.of(JavaToolchainDownload.fromUri(uri));
            }
        """
    }

    private static String customToolchainResolverCode() {
        """
            @Override
            public Optional<JavaToolchainDownload> resolve(JavaToolchainRequest request) {
                URI uri = URI.create("https://exoticJavaToolchain.com/java-" + request.getJavaToolchainSpec().getLanguageVersion().get());
                return Optional.of(JavaToolchainDownload.fromUri(uri));
            }
        """
    }

}
