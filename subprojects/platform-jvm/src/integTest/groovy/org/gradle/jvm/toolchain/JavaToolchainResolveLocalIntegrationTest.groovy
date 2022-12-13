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

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.internal.jvm.Jvm

/**
 * Tests resolution of local Java toolchains. Particularly the behavior of plugins
 * which implement {@link JavaToolchainResolver#resolveLocal(JavaToolchainRequest)}.
 */
class JavaToolchainResolveLocalIntegrationTest extends AbstractJavaToolchainDownloadSpiIntegrationTest {

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def "can inject custom local toolchain via settings plugin"() {

        Jvm jdk = AvailableJavaHomes.getDifferentJdk()
        String javaHome = jdk.javaHome.toString()
        String javaVersion = jdk.javaVersion.getMajorVersion()

        settingsFile << """
            ${applyToolchainResolverPlugin("CustomToolchainResolver", customToolchainLocalResolverCode(javaHome))}
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

        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of($javaVersion)
                }
            }
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        succeeds "compileJava", "--info"

        then:
        outputContains("Compiling with toolchain '$javaHome'")
    }

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def "local JDK is checked against the spec"() {

        Jvm jdk = AvailableJavaHomes.getDifferentJdk()
        String javaHome = jdk.javaHome.toString()
        String javaVersion = jdk.javaVersion.getMajorVersion()
        int otherJavaVersion = Integer.parseInt(javaVersion) + 1

        settingsFile << """
            ${applyToolchainResolverPlugin("CustomToolchainResolver", customToolchainLocalResolverCode(javaHome))}
            toolchainManagement {
                jvm {
                    javaRepositories {
                        repository('broken') {
                            resolverClass = CustomToolchainResolver
                        }
                    }
                }
            }
        """

        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of($otherJavaVersion)
                }
            }
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        def failure = fails "compileJava"

        then:
        failure.assertHasCause("Toolchain provisioned from '$javaHome' doesn't satisfy the specification: {languageVersion=$otherJavaVersion, vendor=any, implementation=vendor-specific}.")
    }

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def "local JDKs are prioritized over remote jdk archive downloads"() {

        settingsFile << """
            ${applyToolchainResolverPlugin("CustomToolchainResolver", loggingLocalResolverCode("CustomToolchainResolver"))}
            ${applyToolchainResolverPlugin("CustomToolchainResolver2", loggingLocalResolverCode("CustomToolchainResolver2"))}
            toolchainManagement {
                jvm {
                    javaRepositories {
                        repository('first') {
                            resolverClass = CustomToolchainResolver
                        }
                        repository('second') {
                            resolverClass = CustomToolchainResolver2
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
        outputContains("""
CustomToolchainResolver resolveLocal {languageVersion=99, vendor=any, implementation=vendor-specific}
CustomToolchainResolver2 resolveLocal {languageVersion=99, vendor=any, implementation=vendor-specific}
CustomToolchainResolver resolve {languageVersion=99, vendor=any, implementation=vendor-specific}
CustomToolchainResolver2 resolve {languageVersion=99, vendor=any, implementation=vendor-specific}
""")
    }

    private static String customToolchainLocalResolverCode(String javaHome, String name = "CustomToolchainResolver") {
        """
            import java.nio.file.Paths;
            import java.util.Optional;
            import org.gradle.platform.BuildPlatform;

            public abstract class ${name} implements JavaToolchainResolver {
                @Override
                public Optional<JavaToolchainInstallation> resolveLocal(JavaToolchainRequest request) {
                    return Optional.of(JavaToolchainInstallation.fromJavaHome(Paths.get("$javaHome")));
                }
            }
        """
    }

    private static String loggingLocalResolverCode(String name) {
        """
            import java.nio.file.Paths;
            import java.util.Optional;
            import org.gradle.platform.BuildPlatform;

            public abstract class ${name} implements JavaToolchainResolver {
                @Override
                public Optional<JavaToolchainInstallation> resolveLocal(JavaToolchainRequest request) {
                    System.out.println("$name resolveLocal " + request.spec);
                    return Optional.empty();
                }
                @Override
                public Optional<JavaToolchainDownload> resolve(JavaToolchainRequest request) {
                    System.out.println("$name resolve " + request.spec);
                    return Optional.empty();
                }
            }
        """

    }
}
