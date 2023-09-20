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
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.jvm.inspection.JvmVendor
import spock.lang.Ignore

import static org.gradle.integtests.fixtures.AvailableJavaHomes.getJvmInstallationMetadata
import static org.gradle.jvm.toolchain.JavaToolchainDownloadSoakTest.TOOLCHAIN_WITH_VERSION
import static org.gradle.jvm.toolchain.JavaToolchainDownloadSoakTest.JAVA_VERSION
import static org.gradle.jvm.toolchain.JavaToolchainDownloadUtil.applyToolchainResolverPlugin
import static org.gradle.jvm.toolchain.JavaToolchainDownloadUtil.multiUrlResolverCode
import static org.gradle.jvm.toolchain.JavaToolchainDownloadUtil.singleUrlResolverCode

class JavaToolchainDownloadComplexProjectSoakTest extends AbstractIntegrationSpec {

    static JdkRepository jdkRepository

    static URI uri

    def setupSpec() {
        jdkRepository = new JdkRepository(JAVA_VERSION)
        uri = jdkRepository.start()
    }

    def cleanupSpec() {
        jdkRepository.stop()
    }

    def setup() {
        jdkRepository.reset()

        executer.requireOwnGradleUserHomeDir()
            .withToolchainDownloadEnabled()
    }

    def cleanup() {
        executer.gradleUserHomeDir.file("jdks").deleteDir()
    }

    def "multiple subprojects with identical toolchain definitions"() {
        given:
        settingsFile << settingsForBuildWithSubprojects(singleUrlResolverCode(uri))

        def jdkMetadata = getJvmInstallationMetadata(jdkRepository.getJdk())
        setupSubproject("subproject1", "Foo", jdkMetadata.vendor)
        setupSubproject("subproject2", "Bar", jdkMetadata.vendor)

        when:
        result = executer
                .withTasks("compileJava")
                .run()

        then:
        !result.plainTextOutput.matches("(?s).*The existing installation will be replaced by the new download.*")
    }

    def "multiple subprojects with different toolchain definitions"() {
        given:
        def otherJdk = getJdkWithDifferentVendor()
        def otherJdkRepository = new JdkRepository(otherJdk, "other_jdk.zip")
        def otherUri = otherJdkRepository.start()
        otherJdkRepository.reset()

        settingsFile << settingsForBuildWithSubprojects(multiUrlResolverCode(uri, otherUri))

        def jdkMetadata = getJvmInstallationMetadata(jdkRepository.getJdk())
        setupSubproject("subproject1", "Foo", jdkMetadata.vendor)
        def otherJdkMetadata = getJvmInstallationMetadata(otherJdk)
        setupSubproject("subproject2", "Bar", otherJdkMetadata.vendor)

        when:
        result = executer
                .withTasks("compileJava")
                .withArgument("--info")
                .run()


        then:
        result.plainTextOutput.matches("(?s).*Compiling with toolchain.*${jdkMetadata.javaHome.fileName}.*")
        result.plainTextOutput.matches("(?s).*Compiling with toolchain.*${otherJdkMetadata.javaHome.fileName}.*")
        otherJdkRepository.stop()
    }

    private Jvm getJdkWithDifferentVendor() {
        def jdkMetadata = getJvmInstallationMetadata(jdkRepository.getJdk())
        def filterForOtherJdk = metadata -> jdkRepository.getJdk().getJavaHome() != metadata.javaHome &&
            JAVA_VERSION == metadata.languageVersion && metadata.vendor.rawVendor != jdkMetadata.vendor.rawVendor
        AvailableJavaHomes.getAvailableJdks(filterForOtherJdk).stream().findFirst().orElseThrow()
    }

    private String settingsForBuildWithSubprojects(String resolverCode) {
        return """
            ${applyToolchainResolverPlugin("CustomToolchainResolver", resolverCode)}

            rootProject.name = 'main'

            include('subproject1')
            include('subproject2')
        """
    }

    private void setupSubproject(String subprojectName, String className, JvmVendor vendor) {
        file("${subprojectName}/build.gradle") << """
            plugins {
                id 'java'
            }

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of($JAVA_VERSION)
                    vendor = JvmVendorSpec.matching("${vendor.rawVendor}")
                }
            }
        """
        file("${subprojectName}/src/main/java/${className}.java") << "public class ${className} {}"
    }

    @Ignore("this test doesn't really test anything as is; see TODO in setupIncludedBuild()")
    def "included build with different toolchain repository definition"() {
        given:
        settingsFile << settingsForBuildWithIncludedBuilds()
        buildFile << buildConfigForBuildWithIncludedBuilds()

        setupIncludedBuild()

        when:
        result = executer
                .withTasks("compileJava")
                .withArgument("--info")
                .run()

        then:
        result.plainTextOutput.matches("(?s).*Compiling with toolchain.*")
    }

    private String settingsForBuildWithIncludedBuilds() {
        return """
            pluginManagement {
                includeBuild 'plugin1'
            }

            ${applyToolchainResolverPlugin("CustomToolchainResolver", singleUrlResolverCode(uri))}

            rootProject.name = 'main'
        """
    }

    private String buildConfigForBuildWithIncludedBuilds() {
        return """
            plugins {
                id 'java'
                id 'org.example.plugin1'
            }

            $TOOLCHAIN_WITH_VERSION
        """
    }

    private void setupIncludedBuild() {
        /*file("plugin1/settings.gradle") << """
            ${applyToolchainResolverPlugin(
                "CustormToolchainResolver",
                singleUrlResolverCode("https://good_for_nothing.com/"),
                JavaToolchainDownloadUtil.DEFAULT_PLUGIN,
                """
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
            )}

            rootProject.name = 'plugin1'
        """*/ //TODO: atm the included build will use the definition from its own settings file, so if this is the settings we use it won't be able to download toolchains; need to clarify if this ok in the long term
        file("plugin1/settings.gradle") << """
            ${applyToolchainResolverPlugin("CustomToolchainResolver", singleUrlResolverCode(uri))}

            rootProject.name = 'plugin1'
        """
        file("plugin1/build.gradle") << """
            plugins {
                id 'java-gradle-plugin'
            }

            $TOOLCHAIN_WITH_VERSION

            gradlePlugin {
                plugins {
                    simplePlugin {
                        id = 'org.example.plugin1'
                        implementationClass = 'org.example.Plugin1'
                    }
                }
            }
        """
        file("plugin1/src/main/java/Plugin1.java") << """
            package org.example;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;

            public class Plugin1 implements Plugin<Project> {

                @Override
                public void apply(Project project) {
                    System.out.println("Plugin1 applied!");
                }
            }
        """
    }
}
