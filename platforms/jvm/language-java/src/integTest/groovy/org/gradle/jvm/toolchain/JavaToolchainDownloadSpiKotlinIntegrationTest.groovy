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
import org.gradle.internal.os.OperatingSystem

class JavaToolchainDownloadSpiKotlinIntegrationTest extends AbstractIntegrationSpec {

    def "can inject custom toolchain registry via settings plugin"() {
        settingsKotlinFile << """
            ${applyToolchainRegistryPlugin("CustomToolchainResolver", customToolchainRegistryCode())}
            toolchainManagement {
                jvm {
                    javaRepositories {
                        repository("custom") {
                            resolverClass.set(CustomToolchainResolver::class.java)
                        }
                    }
                }
            }
        """

        buildKotlinFile << """
            plugins {
                java
            }

            java {
                toolchain {
                    languageVersion.set(JavaLanguageVersion.of(99))
                    vendor.set(JvmVendorSpec.matching("exotic"))
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
        failure.assertHasDescription("Could not determine the dependencies of task ':compileJava'.")
               .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'.")
               .assertHasCause("Cannot find a Java installation on your machine (${OperatingSystem.current()}) matching: {languageVersion=99, vendor=vendor matching('exotic'), implementation=vendor-specific}")
               .assertHasCause("No matching toolchain could be found in the locally installed toolchains or the configured toolchain download repositories. " +
                   "Some toolchain resolvers had provisioning failures: custom (Unable to download toolchain matching the requirements " +
                   "({languageVersion=99, vendor=vendor matching('exotic'), implementation=vendor-specific}) from 'https://exoticJavaToolchain.com/java-99', " +
                   "due to: Could not HEAD 'https://exoticJavaToolchain.com/java-99'.).")
    }

    private static String applyToolchainRegistryPlugin(String className, String code) {
        """
            import java.net.URI
            import java.util.Optional

            abstract class ${className}Plugin: Plugin<Settings> {

                @get:Inject
                protected abstract val toolchainResolverRegistry: JavaToolchainResolverRegistry

                override fun apply(settings: Settings) {
                    settings.plugins.apply("jvm-toolchain-management")
                    val registry: JavaToolchainResolverRegistry = toolchainResolverRegistry
                    registry.register(${className}::class.java)
                }

            }

            ${code}

            apply<${className}Plugin>()
        """
    }

    private static String customToolchainRegistryCode() {
        """
            abstract class CustomToolchainResolver: JavaToolchainResolver {
                override fun resolve(request: JavaToolchainRequest): Optional<JavaToolchainDownload> {
                    val uri = URI.create("https://exoticJavaToolchain.com/java-" + request.getJavaToolchainSpec().getLanguageVersion().get())
                    return Optional.of(JavaToolchainDownload.fromUri(uri))
                }
            }
            """
    }

}
