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
import static org.gradle.jvm.toolchain.JavaToolchainDownloadSoakTest.VERSION

class JavaToolchainDownloadComplexProjectSoakTest extends AbstractIntegrationSpec {

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
        return """
            plugins {
                id 'org.gradle.toolchains.foojay-resolver-convention' version '0.2'
            }
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
                    languageVersion = JavaLanguageVersion.of($VERSION)
                    vendor = JvmVendorSpec.${vendorName}
                }
            }
        """
        file("${subprojectName}/src/main/java/${className}.java") << "public class ${className} {}"
    }
}
