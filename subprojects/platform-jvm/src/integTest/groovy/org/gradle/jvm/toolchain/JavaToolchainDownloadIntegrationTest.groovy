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
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.fixtures.file.TestFile

class JavaToolchainDownloadIntegrationTest extends AbstractIntegrationSpec {

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def "fails for missing combination"() {
        setFoojayDiscoToolchainProvider()

        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(14)
                    implementation = JvmImplementation.J9
                    vendor = JvmVendorSpec.ADOPTIUM
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
            .assertHasCause("Error while evaluating property 'javaCompiler' of task ':compileJava'.")
            .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'")
            .assertHasCause("No matching toolchains found for requested specification: {languageVersion=14, vendor=ADOPTIUM, implementation=J9}.")
            .assertHasDocumentedCause("No locally installed toolchains match (see https://docs.gradle.org/current/userguide/toolchains.html#sec:auto_detection) " +
                    "and the configured toolchain download repositories aren't able to provide a match either (see https://docs.gradle.org/current/userguide/toolchains.html#sub:download_repositories).")
    }

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def 'toolchain selection that requires downloading fails when it is disabled'() {
        setFoojayDiscoToolchainProvider()

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
            .assertHasCause("Error while evaluating property 'javaCompiler' of task ':compileJava'.")
            .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'")
            .assertHasCause("No matching toolchains found for requested specification: {languageVersion=14, vendor=any, implementation=vendor-specific}.")
            .assertHasDocumentedCause("No locally installed toolchains match (see https://docs.gradle.org/current/userguide/toolchains.html#sec:auto_detection) " +
                    "and toolchain auto-provisioning is not enabled (see https://docs.gradle.org/current/userguide/toolchains.html#sec:auto_detection).")
    }

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def 'toolchain download on http fails'() {
        setUnsecuredToolchainProvider()

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
            .assertHasCause("Unable to download toolchain matching the requirements ({languageVersion=99, vendor=any, implementation=vendor-specific}) from 'http://exoticJavaToolchain.com/java-99'.")
            .assertHasCause("Attempting to download a file from an insecure URI http://exoticJavaToolchain.com/java-99. This is not supported, use a secure URI instead.")
    }

    private TestFile setFoojayDiscoToolchainProvider() {
        settingsFile << """
            plugins {
                id 'org.gradle.toolchains.foojay-resolver-convention' version '0.4.0'
            }
        """
    }

    private TestFile setUnsecuredToolchainProvider() {
        settingsFile << """
            public abstract class CustomToolchainResolverPlugin implements Plugin<Settings> {
                @Inject
                protected abstract JavaToolchainResolverRegistry getToolchainResolverRegistry();
            
                void apply(Settings settings) {
                    settings.getPlugins().apply("jvm-toolchain-management");
                
                    JavaToolchainResolverRegistry registry = getToolchainResolverRegistry();
                    registry.register(CustomToolchainResolver.class);
                }
            }
            
            
            import java.util.Optional;
            import org.gradle.platform.BuildPlatform;

            public abstract class CustomToolchainResolver implements JavaToolchainResolver {
                @Override
                public Optional<JavaToolchainDownload> resolve(JavaToolchainRequest request) {
                    URI uri = URI.create("http://exoticJavaToolchain.com/java-" + request.getJavaToolchainSpec().getLanguageVersion().get());
                    return Optional.of(JavaToolchainDownload.fromUri(uri));
                }
            }
            

            apply plugin: CustomToolchainResolverPlugin
                       
            toolchainManagement {
                jvm {
                    javaRepositories {
                        repository('custom') {
                            resolverClass = CustomToolchainResolver
                        }
                    }
                }
            }
        """
    }

}
