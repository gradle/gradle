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
            ${customToolchainRegistryPluginCode()}            
            apply plugin: CustomToolchainRegistryPlugin
            
            toolchainManagement {
                jdks("customRegistry")
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
                .withToolchainDetectionEnabled()
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
                .withToolchainDetectionEnabled()
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
            ${uselessToolchainRegistryPluginCode()}            
            apply plugin: UselessToolchainRegistryPlugin
            
            toolchainManagement {
                jdks("uselessRegistry")
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
                .withToolchainDetectionEnabled()
                .withToolchainDownloadEnabled()
                .runWithFailure()

        then:
        failure.assertHasDescription("Execution failed for task ':compileJava'.")
                .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'")
                .assertHasCause("No compatible toolchains found for request filter: {languageVersion=99, vendor=any, implementation=vendor-specific} (auto-detect true, auto-download true)")
    }

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def "it is possible to explicitly request the default registry"() {
        settingsFile << """
            toolchainManagement {
                jdks("${DefaultJavaToolchainRepositoryRegistry.DEFAULT_REGISTRY_NAME}")
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
                .withToolchainDetectionEnabled()
                .withToolchainDownloadEnabled()
                .runWithFailure()

        then:

        failure.assertHasDescription("Execution failed for task ':compileJava'.")
                .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'")
                .assertHasCause("Unable to download toolchain matching the requirements ({languageVersion=99, vendor=any, implementation=vendor-specific}) from: https://api.adoptium.net/v3/binary/latest/99/ga/${os()}/x64/jdk/hotspot/normal/eclipse")
                .assertHasCause("Could not read 'https://api.adoptium.net/v3/binary/latest/99/ga/${os()}/x64/jdk/hotspot/normal/eclipse' as it does not exist.")
    }

    private static String customToolchainRegistryPluginCode() {
        """
            public abstract class CustomToolchainRegistryPlugin implements Plugin<Settings> {
                @Inject
                protected abstract JavaToolchainRepositoryRegistry getToolchainRepositoryRegistry();
            
                void apply(Settings settings) {
                    JavaToolchainRepositoryRegistry registry = getToolchainRepositoryRegistry();
                    registry.register("customRegistry", CustomToolchainRegistry.class)
                }
            }
            
            ${customToolchainRegistryCode()}
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

    private static String uselessToolchainRegistryPluginCode() {
        """
            public abstract class UselessToolchainRegistryPlugin implements Plugin<Settings> {
                @Inject
                protected abstract JavaToolchainRepositoryRegistry getToolchainRepositoryRegistry();
            
                void apply(Settings settings) {
                    JavaToolchainRepositoryRegistry registry = getToolchainRepositoryRegistry();
                    registry.register("uselessRegistry", UselessToolchainRegistry.class)
                }
            }
            
            ${uselessToolchainRegistryCode()}
        """
    }

    private static String uselessToolchainRegistryCode() {
        """
            import java.util.Optional;

            public abstract class UselessToolchainRegistry implements JavaToolchainRepository {

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
            return "windows";
        } else if (os.isMacOsX()) {
            return "mac";
        } else if (os.isLinux()) {
            return "linux";
        }
        return os.getFamilyName();
    }

}
