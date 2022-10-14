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

package org.gradle.api.tasks.javadoc

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import spock.lang.IgnoreIf

import static org.gradle.integtests.fixtures.AvailableJavaHomes.getDifferentVersion
import static org.gradle.integtests.fixtures.AvailableJavaHomes.getJvmInstallationMetadata

class JavadocToolchainIntegrationTest extends AbstractIntegrationSpec {

    def "changing toolchain invalidates task"() {
        def jdkMetadata1 = getJvmInstallationMetadata(Jvm.current())
        def jdkMetadata2 = getJvmInstallationMetadata(getDifferentVersion())

        buildFile << """
            plugins {
                id 'java'
            }

            javadoc {
                javadocTool = javaToolchains.javadocToolFor {
                    def version = ${jdkMetadata1.languageVersion.majorVersion}
                    version = providers.gradleProperty('test.javadoc.version').getOrElse(version)
                    languageVersion = JavaLanguageVersion.of(version)
                }
            }
        """
        file('src/main/java/Lib.java') << testLib()

        when:
        withInstallations(jdkMetadata1, jdkMetadata2).run(":javadoc", "--info")
        then:
        executedAndNotSkipped(":javadoc")
        file("build/docs/javadoc/Lib.html").text.contains("Some API documentation.")

        when:
        withInstallations(jdkMetadata1, jdkMetadata2).run(":javadoc", "--info")
        then:
        skipped(":javadoc")

        when:
        executer.withArgument("-Ptest.javadoc.version=${jdkMetadata2.languageVersion.majorVersion}")
        withInstallations(jdkMetadata1, jdkMetadata2).run(":javadoc", "--info")
        then:
        executedAndNotSkipped(":javadoc")
        file("build/docs/javadoc/Lib.html").text.contains("Some API documentation.")

        when:
        executer.withArgument("-Ptest.javadoc.version=${jdkMetadata2.languageVersion.majorVersion}")
        withInstallations(jdkMetadata1, jdkMetadata2).run(":javadoc", "--info")
        then:
        skipped(":javadoc")
    }

    @IgnoreIf({ AvailableJavaHomes.getJdk(JavaVersion.VERSION_11) == null })
    def "can manually set javadoc tool via  #type toolchain on javadoc task #type : #jdk"() {
        buildFile << """
            plugins {
                id 'java'
            }

            // need to do as separate task as -version stop javadoc generation
            task javadocVersionOutput(type: Javadoc) {
                source = tasks.javadoc.source
                options.jFlags("-version")
                javadocTool = javaToolchains.javadocToolFor {
                    languageVersion = JavaLanguageVersion.of(${jdk.javaVersion.majorVersion})
                }
            }

            javadoc {
                javadocTool = javaToolchains.javadocToolFor {
                    languageVersion = JavaLanguageVersion.of(${jdk.javaVersion.majorVersion})
                }
                dependsOn "javadocVersionOutput"
            }
        """

        file('src/main/java/Lib.java') << testLib()

        when:
        result = executer
            .withArgument("-Porg.gradle.java.installations.paths=" + jdk.javaHome.absolutePath)
            .withTasks("javadoc")
            .run()
        then:

        file("build/docs/javadoc/Lib.html").text.contains("Some API documentation.")
        errorOutput.contains(jdk.javaVersion.majorVersion)
        noExceptionThrown()

        where:
        type           | jdk
        'differentJdk' | AvailableJavaHomes.getJdk(JavaVersion.VERSION_11)
        'current'      | Jvm.current()
    }

    @IgnoreIf({ AvailableJavaHomes.differentVersion == null })
    def "javadoc task is configured using default toolchain"() {
        def someJdk = AvailableJavaHomes.getDifferentVersion()
        buildFile << """
            plugins {
                id 'java'
            }

            // need to do as separate task as -version stop javadoc generation
            task javadocVersionOutput(type: Javadoc) {
                source = tasks.javadoc.source
                options.jFlags("-version")
            }

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${someJdk.javaVersion.majorVersion})
                }
            }
        """

        file('src/main/java/Lib.java') << testLib()

        when:
        result = executer
            .withArgument("-Porg.gradle.java.installations.paths=" + someJdk.javaHome.absolutePath)
            .withTasks("javadocVersionOutput")
            .run()

        then:
        errorOutput.contains(someJdk.javaVersion.majorVersion)
        noExceptionThrown()
    }

    private withInstallations(JvmInstallationMetadata... jdkMetadata) {
        def installationPaths = jdkMetadata.collect { it.javaHome.toAbsolutePath().toString() }.join(",")
        executer.withArgument("-Porg.gradle.java.installations.paths=" + installationPaths)
        this
    }

    private static String testLib() {
        return """
            public class Lib {
               /**
                * Some API documentation.
                */
               public void foo() {
               }
            }
        """.stripIndent()
    }
}
