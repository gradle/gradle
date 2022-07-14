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

class JavaToolchainDownloadSpiIntegrationTest extends AbstractIntegrationSpec {

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def "will use default if no custom toolchain registry injected"() {
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

        failure.getOutput().contains("Starting from Gradle 8.0 there will be no default Java Toolchain Registry. Need to explicitly request them via the 'toolchainManagement' block.")

        failure.assertHasDescription("Execution failed for task ':compileJava'.")
                .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'")
                .assertHasCause("Unable to download toolchain matching the requirements ({languageVersion=99, vendor=any, implementation=vendor-specific}) from: https://api.adoptium.net/v3/binary/latest/99/ga/${os()}/x64/jdk/hotspot/normal/eclipse")
                .assertHasCause("Unable to download 'https://api.adoptium.net/v3/binary/latest/99/ga/${os()}/x64/jdk/hotspot/normal/eclipse' into file '${testDirectory}/user-home/jdks/adoptium-99-x64-hotspot-${os()}.tar.gz'")
                .assertHasCause("Could not read 'https://api.adoptium.net/v3/binary/latest/99/ga/${os()}/x64/jdk/hotspot/normal/eclipse' as it does not exist.")
    }

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
                .withToolchainDownloadEnabled()
                .runWithFailure()

        then:
        failure.assertHasDescription("Execution failed for task ':compileJava'.")
                .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'")
                .assertHasCause("Unable to download toolchain matching the requirements ({languageVersion=99, vendor=matching('exotic'), implementation=vendor-specific}) from: https://exoticJavaToolchain.com/java-99")
                .assertHasCause("Could not GET 'https://exoticJavaToolchain.com/java-99'.")
    }

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def "if toolchain registries are explicitly requested, then the default one is excluded"() {
        //TODO
    }

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def "it is possible to explicitly request the default registry"() {
        //TODO
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
            public abstract class CustomToolchainRegistry implements JavaToolchainRepository {

                @Override
                public java.util.Optional<URI> toUri(JavaToolchainSpec spec) {
                    return java.util.Optional.of(URI.create("https://exoticJavaToolchain.com/java-" + spec.getLanguageVersion().get()));
                }
        
                @Override
                public java.util.Optional<Metadata> toMetadata(JavaToolchainSpec spec) {
                    return java.util.Optional.of(new MetadataImp(spec));
                }
        
                @Override
                public JavaToolchainSpecVersion getToolchainSpecCompatibility() {
                    return JavaToolchainSpecVersion.V1;
                }

                private static class MetadataImp implements Metadata {
        
                    private final JavaLanguageVersion languageVersion;
        
                    public MetadataImp(JavaToolchainSpec spec) {
                        this.languageVersion = spec.getLanguageVersion().get();
                    }
        
                    @Override
                    public String fileExtension() {
                        return "tgz";
                    }
        
                    @Override
                    public String vendor() {
                        return "exotic";
                    }
        
                    @Override
                    public String languageLevel() {
                        return languageVersion.toString();
                    }
        
                    @Override
                    public String operatingSystem() {
                        return "linux";
                    }
        
                    @Override
                    public String implementation() {
                        return "exoticVM";
                    }
        
                    @Override
                    public String architecture() {
                        return "x64";
                    }
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
