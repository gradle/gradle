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
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.internal.os.OperatingSystem
import org.gradle.jvm.toolchain.internal.DefaultJavaToolchainRepositoryRegistry

class JavaToolchainDownloadSpiIntegrationTest extends AbstractIntegrationSpec {

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def "can inject custom toolchain registry via settings plugin"() {
        settingsFile << """
            ${applyToolchainManagementBasePlugin()}
            ${applyToolchainRegistryPlugin("customRegistry", "CustomToolchainRegistry", customToolchainRegistryCode())}               
            toolchainManagement {
                jdks.request("customRegistry")
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
                .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'")
                .assertHasCause("Unable to download toolchain matching the requirements ({languageVersion=99, vendor=matching('exotic'), implementation=vendor-specific}) from: https://exoticJavaToolchain.com/java-99")
                .assertHasCause("Could not HEAD 'https://exoticJavaToolchain.com/java-99'.")
    }

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def "registering a custom toolchain registry adds dynamic extension"() {
        settingsFile << """
            ${applyToolchainManagementBasePlugin()}
            ${applyToolchainRegistryPlugin("customRegistry", "CustomToolchainRegistry", customToolchainRegistryCode())}            
            toolchainManagement {
                jdks.request(jdks.customRegistry)
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
                .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'")
                .assertHasCause("Unable to download toolchain matching the requirements ({languageVersion=99, vendor=matching('exotic'), implementation=vendor-specific}) from: https://exoticJavaToolchain.com/java-99")
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
                .runWithFailure()

        then:

        failure.getOutput().contains("Starting from Gradle 8.0 there will be no default Java Toolchain Registry. Need to inject such registries via settings plugins and explicitly request them via the 'toolchainManagement' block.")

        failure.assertHasDescription("Execution failed for task ':compileJava'.")
                .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'")
                .assertHasCause("Unable to download toolchain matching the requirements ({languageVersion=99, vendor=any, implementation=vendor-specific}) from: https://api.adoptium.net/v3/binary/latest/99/ga/${os()}/x64/jdk/hotspot/normal/eclipse")
                .assertHasCause("Could not read 'https://api.adoptium.net/v3/binary/latest/99/ga/${os()}/x64/jdk/hotspot/normal/eclipse' as it does not exist.")
    }

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def "if toolchain registries are explicitly requested, then the default is NOT automatically added to the request"() {
        settingsFile << """
            ${applyToolchainManagementBasePlugin()}
            ${applyToolchainRegistryPlugin("uselessRegistry", "UselessToolchainRegistry", uselessToolchainRegistryCode("UselessToolchainRegistry"))}            
            toolchainManagement {
                jdks.request("uselessRegistry")
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
                .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'")
                .assertHasCause("No compatible toolchains found for request filter: {languageVersion=99, vendor=any, implementation=vendor-specific} (auto-detect false, auto-download true)")
    }

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def "it is possible to explicitly request the default registry via name"() {
        settingsFile << """
            ${applyToolchainManagementBasePlugin()}
            toolchainManagement {
                $jdksRequest
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
                .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'")
                .assertHasCause("Unable to download toolchain matching the requirements ({languageVersion=99, vendor=any, implementation=vendor-specific}) from: https://api.adoptium.net/v3/binary/latest/99/ga/${os()}/x64/jdk/hotspot/normal/eclipse")
                .assertHasCause("Could not read 'https://api.adoptium.net/v3/binary/latest/99/ga/${os()}/x64/jdk/hotspot/normal/eclipse' as it does not exist.")

        where:
        jdksRequest | _
        """jdks.request("${DefaultJavaToolchainRepositoryRegistry.DEFAULT_REGISTRY_NAME}")""" | _
        """jdks.request(jdks.${DefaultJavaToolchainRepositoryRegistry.DEFAULT_REGISTRY_NAME})"""   | _
    }

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def "fails on name collision when registering repositories"() {
        settingsFile << """
            ${applyToolchainManagementBasePlugin()}
            ${applyToolchainRegistryPlugin("uselessRegistry", "UselessToolchainRegistry1", uselessToolchainRegistryCode("UselessToolchainRegistry1"))}            
            ${applyToolchainRegistryPlugin("uselessRegistry", "UselessToolchainRegistry2", uselessToolchainRegistryCode("UselessToolchainRegistry2"))}            
            toolchainManagement {
                jdks.request("uselessRegistry")
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

        failure.assertHasCause("Failed to apply plugin class 'UselessToolchainRegistry2Plugin'.")
                .assertHasCause("Duplicate JavaToolchainRepository registration under the name 'uselessRegistry'")
    }

    private static String applyToolchainManagementBasePlugin() {
        //TODO (#21082): the base plugin which injects the "jdks" block will need to be a Gradle core plugin, this is an intermediate state of development
        """
            import org.gradle.internal.event.ListenerManager;
            import org.gradle.jvm.toolchain.internal.DefaultJdksBlockForToolchainManagement;

            public abstract class ToolchainManagementBasePlugin implements Plugin<Settings> {
                @Inject
                protected abstract JavaToolchainRepositoryRegistry getToolchainRepositoryRegistry();

                @Inject
                protected abstract ListenerManager getListenerManager();

                void apply(Settings settings) {
                    JavaToolchainRepositoryRegistry registry = getToolchainRepositoryRegistry();
                    ListenerManager listenerManager = getListenerManager();
                    settings.getToolchainManagement().getExtensions()
                        .create(JdksBlockForToolchainManagement.class, "jdks", DefaultJdksBlockForToolchainManagement.class, registry, listenerManager);
                }
            }

            apply plugin: ToolchainManagementBasePlugin
        """
    }

    private static String applyToolchainRegistryPlugin(String name, String className, String code) {
        """
            public abstract class ${className}Plugin implements Plugin<Settings> {
                @Inject
                protected abstract JavaToolchainRepositoryRegistry getToolchainRepositoryRegistry();
            
                void apply(Settings settings) {
                    JavaToolchainRepositoryRegistry registry = getToolchainRepositoryRegistry();
                    registry.register("${name}", ${className}.class)
                }
            }
            
            ${code}

            apply plugin: ${className}Plugin
        """
    }

    private static String customToolchainRegistryCode() {
        """
            import java.util.Optional;

            public abstract class CustomToolchainRegistry implements JavaToolchainRepository {

                @Override
                public Optional<URI> toUri(JavaToolchainSpec spec) {
                    return Optional.of(URI.create("https://exoticJavaToolchain.com/java-" + spec.getLanguageVersion().get()));
                }
        
                @Override
                public JavaToolchainSpecVersion getToolchainSpecCompatibility() {
                    return JavaToolchainSpecVersion.V1;
                }
            }
            """
    }

    private static String uselessToolchainRegistryCode(String className) {
        """
            import java.util.Optional;

            public abstract class ${className} implements JavaToolchainRepository {

                @Override
                public Optional<URI> toUri(JavaToolchainSpec spec) {
                    return Optional.empty();
                }
        
                @Override
                public JavaToolchainSpecVersion getToolchainSpecCompatibility() {
                    return JavaToolchainSpecVersion.V1;
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
