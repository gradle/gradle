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
import spock.lang.Ignore

import static org.gradle.jvm.toolchain.JavaToolchainDownloadSoakTest.TOOLCHAIN_WITH_VERSION
import static org.gradle.jvm.toolchain.JavaToolchainDownloadSoakTest.JAVA_VERSION

class JavaToolchainDownloadComplexProjectSoakTest extends AbstractIntegrationSpec {

    private static final String FOOJAY_PLUGIN_SECTION = """
            plugins {
                id 'org.gradle.toolchains.foojay-resolver-convention' version '0.7.0'
            }
    """.stripIndent()

    def setup() {
        executer.requireOwnGradleUserHomeDir()
            .withToolchainDownloadEnabled()
    }

    def "multiple subprojects with identical toolchain definitions"() {
        given:
        settingsFile << settingsForBuildWithSubprojects()

        setupSubproject("subproject1", "Foo", "ADOPTIUM")
        setupSubproject("subproject2", "Bar", "ADOPTIUM")

        when:
        result = executer
                .withTasks("compileJava")
                .run()

        then:
        !result.plainTextOutput.matches("(?s).*The existing installation will be replaced by the new download.*")
    }

    def "multiple subprojects with different toolchain definitions"() {
        given:
        settingsFile << settingsForBuildWithSubprojects()

        setupSubproject("subproject1", "Foo", "ADOPTIUM")
        setupSubproject("subproject2", "Bar", "ORACLE")

        when:
        result = executer
                .withTasks("compileJava")
                .withArgument("--info")
                .run()

        then:
        result.plainTextOutput.matches("(?s).*Compiling with toolchain.*adoptium.*")
        result.plainTextOutput.matches("(?s).*Compiling with toolchain.*oracle.*")
    }

    private String settingsForBuildWithSubprojects() {
        return """$FOOJAY_PLUGIN_SECTION

            rootProject.name = 'main'

            include('subproject1')
            include('subproject2')
        """
    }

    private void setupSubproject(String subprojectName, String className, String vendorName) {
        file("${subprojectName}/build.gradle") << """
            plugins {
                id 'java'
            }

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of($JAVA_VERSION)
                    vendor = JvmVendorSpec.${vendorName}
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

            $FOOJAY_PLUGIN_SECTION

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
            ${JavaToolchainDownloadUtil.applyToolchainResolverPlugin(JavaToolchainDownloadUtil.singleUrlResolverCode("https://good_for_nothing.com/"))}
            toolchainManagement {
                jvm {
                    javaRepositories {
                        repository('custom') {
                            resolverClass = CustomToolchainResolver
                        }
                    }
                }
            }

            rootProject.name = 'plugin1'
        """*/ //TODO: atm the included build will use the definition from its own settings file, so if this is the settings we use it won't be able to download toolchains; need to clarify if this ok in the long term
        file("plugin1/settings.gradle") << """
            $FOOJAY_PLUGIN_SECTION

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
