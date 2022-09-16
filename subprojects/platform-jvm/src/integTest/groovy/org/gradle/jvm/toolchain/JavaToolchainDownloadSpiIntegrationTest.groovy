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


import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.internal.os.OperatingSystem

class JavaToolchainDownloadSpiIntegrationTest extends AbstractJavaToolchainDownloadSpiIntegrationTest {

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def "can inject custom toolchain registry via settings plugin"() {
        settingsFile << """
            ${applyToolchainRegistryPlugin("CustomToolchainRegistry", customToolchainRegistryCode())}               
            toolchainManagement {
                jdks {
                    resolvers {
                        resolver('custom') {
                            implementationClass = CustomToolchainRegistry
                        }
                    }
                }
            }
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
                .requireOwnGradleUserHomeDir()
                .withToolchainDownloadEnabled()
                .runWithFailure()

        then:
        failure.assertHasDescription("Execution failed for task ':compileJava'.")
                .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'.")
                .assertHasCause("Unable to download toolchain matching the requirements ({languageVersion=99, vendor=matching('exotic'), implementation=vendor-specific}) from 'https://exoticJavaToolchain.com/java-99'.")
                .assertHasCause("Could not HEAD 'https://exoticJavaToolchain.com/java-99'.")
    }

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def "downloaded JDK is checked against the spec"() {
        settingsFile << """
            ${applyToolchainRegistryPlugin("BrokenToolchainRegistry", brokenToolchainRegistryCode())}               
            toolchainManagement {
                jdks {
                    resolvers {
                        resolver('broken') {
                            implementationClass = BrokenToolchainRegistry
                        }
                    }
                }
            }
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
                .requireOwnGradleUserHomeDir()
                .withToolchainDownloadEnabled()
                .runWithFailure()

        then:
        failure.assertHasDescription("Execution failed for task ':compileJava'.")
                .assertHasCause("Error while evaluating property 'javaCompiler' of task ':compileJava'.")
                .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'.")
                .assertHasCause("Unable to download toolchain matching the requirements ({languageVersion=11, vendor=any, implementation=vendor-specific}) from 'https://api.adoptium.net/v3/binary/latest/8/ga/${os()}/x64/jdk/hotspot/normal/eclipse'.")
                .assertHasCause("Toolchain provisioned from 'https://api.adoptium.net/v3/binary/latest/8/ga/${os()}/x64/jdk/hotspot/normal/eclipse' doesn't satisfy the specification: {languageVersion=11, vendor=any, implementation=vendor-specific}.")
    }

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def "custom toolchain registries are consulted in order"() {
        settingsFile << """
            ${applyToolchainRegistryPlugin("CustomToolchainRegistry", customToolchainRegistryCode())}
            ${applyToolchainRegistryPlugin("UselessToolchainRegistry", uselessToolchainRegistryCode("UselessToolchainRegistry"))}            
            toolchainManagement {
                jdks {
                    resolvers {
                        resolver('useless') {
                            implementationClass = UselessToolchainRegistry
                        }
                        resolver('custom') {
                            implementationClass = CustomToolchainRegistry
                        }
                    }
                }
            }
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
                .requireOwnGradleUserHomeDir()
                .withToolchainDownloadEnabled()
                .runWithFailure()

        then:
        failure.assertHasDescription("Execution failed for task ':compileJava'.")
                .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'.")
                .assertHasCause("Unable to download toolchain matching the requirements ({languageVersion=99, vendor=matching('exotic'), implementation=vendor-specific}) from 'https://exoticJavaToolchain.com/java-99'.")
                .assertHasCause("Could not HEAD 'https://exoticJavaToolchain.com/java-99'.")
    }

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def "will use default if no custom toolchain registry requested"() {
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
                .withToolchainDownloadEnabled()
                .expectDocumentedDeprecationWarning("Java toolchain auto-provisioning needed, but no java toolchain repositories declared by the build. Will rely on the built-in repository. " +
                        "This behaviour has been deprecated and is scheduled to be removed in Gradle 8.0. " +
                        "In order to declare a repository for java toolchains, you must edit your settings script and add one via the toolchainManagement block. " +
                        "See https://docs.gradle.org/current/userguide/toolchains.html#sec:provisioning for more details.")
                .runWithFailure()

        then:
        failure.assertHasDescription("Execution failed for task ':compileJava'.")
                .assertHasCause("Error while evaluating property 'javaCompiler' of task ':compileJava'.")
                .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'.")
                .assertHasCause("Unable to download toolchain matching the requirements ({languageVersion=99, vendor=any, implementation=vendor-specific}) from 'https://api.adoptium.net/v3/binary/latest/99/ga/${os()}/x64/jdk/hotspot/normal/eclipse'.")
                .assertHasCause("Could not read 'https://api.adoptium.net/v3/binary/latest/99/ga/${os()}/x64/jdk/hotspot/normal/eclipse' as it does not exist.")
    }

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def "if toolchain registries are explicitly requested, then the default is NOT automatically added to the request"() {
        settingsFile << """
            ${applyToolchainRegistryPlugin("UselessToolchainRegistry", uselessToolchainRegistryCode("UselessToolchainRegistry"))}            
            toolchainManagement {
                jdks {
                    resolvers {
                        resolver('useless') {
                            implementationClass = UselessToolchainRegistry
                        }
                    }
                }
            }
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
                .requireOwnGradleUserHomeDir()
                .withToolchainDownloadEnabled()
                .runWithFailure()

        then:
        failure.assertHasDescription("Execution failed for task ':compileJava'.")
                .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'.")
                .assertHasCause("No compatible toolchains found for request specification: {languageVersion=99, vendor=any, implementation=vendor-specific} (auto-detect false, auto-download true).")
    }

    def "fails on registration collision"() {
        settingsFile << """
            ${applyToolchainRegistryPlugin("UselessPlugin1", "UselessToolchainRegistry", uselessToolchainRegistryCode("UselessToolchainRegistry"))}
            ${applyToolchainRegistryPlugin("UselessPlugin2", "UselessToolchainRegistry", "")}
            toolchainManagement {
                jdks {
                    resolvers {
                        resolver('useless') {
                            implementationClass = UselessToolchainRegistry1
                        }
                        resolver('useless') {
                            implementationClass = UselessToolchainRegistry2
                        }
                    }
                }
            }
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
                .requireOwnGradleUserHomeDir()
                .withToolchainDownloadEnabled()
                .runWithFailure()

        then:
        failure.assertHasCause("Failed to apply plugin class 'UselessPlugin2'.")
                .assertHasCause("Duplicate registration for 'UselessToolchainRegistry'.")
    }

    def "fails on resolver name collision"() {
        settingsFile << """
            ${applyToolchainRegistryPlugin("UselessToolchainRegistry1", uselessToolchainRegistryCode("UselessToolchainRegistry1"))}            
            ${applyToolchainRegistryPlugin("UselessToolchainRegistry2", uselessToolchainRegistryCode("UselessToolchainRegistry2"))}            
            toolchainManagement {
                jdks {
                    resolvers {
                        resolver('useless') {
                            implementationClass = UselessToolchainRegistry1
                        }
                        resolver('useless') {
                            implementationClass = UselessToolchainRegistry2
                        }
                    }
                }
            }
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
                .requireOwnGradleUserHomeDir()
                .withToolchainDownloadEnabled()
                .runWithFailure()

        then:
        failure.assertHasCause("Duplicate configuration for resolver 'useless'.")
    }

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def "list of requested repositories can be queried"() {
        settingsFile << """
            ${applyToolchainRegistryPlugin("UselessToolchainRegistry1", uselessToolchainRegistryCode("UselessToolchainRegistry1"))}            
            ${applyToolchainRegistryPlugin("UselessToolchainRegistry2", uselessToolchainRegistryCode("UselessToolchainRegistry2"))}            
            ${applyToolchainRegistryPlugin("UselessToolchainRegistry3", uselessToolchainRegistryCode("UselessToolchainRegistry3"))}            
            toolchainManagement {
                jdks {
                    resolvers {
                        resolver('useless3') {
                            implementationClass = UselessToolchainRegistry3
                        }
                        resolver('useless1') {
                            implementationClass = UselessToolchainRegistry1
                        }
                    }
                }
            }
            
            println(\"\"\"Explicitly requested toolchains: \${toolchainManagement.jdks.getResolvers().collect { it.getName() }}.\"\"\")
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
                .requireOwnGradleUserHomeDir()
                .withToolchainDownloadEnabled()
                .runWithFailure()

        then:
        failure.getOutput().contains("Explicitly requested toolchains: [useless3, useless1].")
    }

    private static String customToolchainRegistryCode() {
        """
            import java.util.Optional;
            import org.gradle.platform.BuildPlatform;

            public abstract class CustomToolchainRegistry implements JavaToolchainRepository {
                @Override
                public Optional<URI> toUri(JavaToolchainRequest request) {
                    return Optional.of(URI.create("https://exoticJavaToolchain.com/java-" + request.getJavaToolchainSpec().getLanguageVersion().get()));
                }
            }
            """
    }

    private static String uselessToolchainRegistryCode(String className) {
        """
            import java.util.Optional;
            import org.gradle.platform.BuildPlatform;

            public abstract class ${className} implements JavaToolchainRepository {
                @Override
                public Optional<URI> toUri(JavaToolchainRequest request) {
                    return Optional.empty();
                }
            }
            """
    }

    private static String brokenToolchainRegistryCode() {
        """
            import java.util.Optional;
            import org.gradle.platform.BuildPlatform;

            public abstract class BrokenToolchainRegistry implements JavaToolchainRepository {
                @Override
                public Optional<URI> toUri(JavaToolchainRequest request) {
                    return Optional.of(URI.create("https://api.adoptium.net/v3/binary/latest/8/ga/${os()}/x64/jdk/hotspot/normal/eclipse"));
                }
            }
            """
    }

    private static String os() {
        OperatingSystem os = OperatingSystem.current()
        if (os.isWindows()) {
            return "windows"
        } else if (os.isMacOsX()) {
            return "mac"
        } else if (os.isLinux()) {
            return "linux"
        }
        return os.getFamilyName()
    }

}
