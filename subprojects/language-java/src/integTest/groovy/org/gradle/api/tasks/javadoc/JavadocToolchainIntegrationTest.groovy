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
import spock.lang.IgnoreIf
import spock.lang.Unroll

class JavadocToolchainIntegrationTest extends AbstractIntegrationSpec {

    @Unroll
    @IgnoreIf({ AvailableJavaHomes.getJdk(JavaVersion.VERSION_11) == null })
    def "can manually set javadoc tool via  #type toolchain on javadoc task #jdk"() {
        buildFile << """
            import org.gradle.jvm.toolchain.internal.JavaToolchainQueryService
            import org.gradle.jvm.toolchain.internal.DefaultToolchainSpec

            plugins {
                id 'java'
            }

            abstract class ApplyTestToolchain implements Plugin<Project> {
                @javax.inject.Inject
                abstract JavaToolchainQueryService getQueryService()

                void apply(Project project) {
                    def filter = project.objects.newInstance(DefaultToolchainSpec)
                    filter.languageVersion = JavaVersion.${jdk.javaVersion.name()}
                    def toolchain = getQueryService().findMatchingToolchain(filter)

                    project.tasks.withType(JavaCompile) {
                        javaCompiler = toolchain.map({it.javaCompiler})
                    }
                    project.tasks.withType(Javadoc) {
                        javadocTool = toolchain.map({it.javadocTool})
                    }
                }
            }

            apply plugin: ApplyTestToolchain

            // need to do as separate task as -version stop javadoc generation
            task javadocVersionOutput(type: Javadoc) {
                options.jFlags("-version")
            }

        """

        file('src/main/java/Lib.java') << testLib()

        when:
        result = executer
            .withArgument("-Porg.gradle.java.installations.auto-detect=false")
            .withArgument("-Porg.gradle.java.installations.paths=" + jdk.javaHome.absolutePath)
            .withArgument("--info")
            .withTasks("javadoc", "javadocVersionOutput")
            .run()
        then:

        file("build/docs/javadoc/Lib.html").text.contains("Some API documentation.")
        outputContains(jdk.javaVersion.majorVersion)
        noExceptionThrown()

        where:
        type           | jdk
        'differentJdk' | AvailableJavaHomes.getJdk(JavaVersion.VERSION_11)
        'current'      | Jvm.current()
    }

    @IgnoreIf({ AvailableJavaHomes.differentJdk == null })
    def "JavaExec task is configured using default toolchain"() {
        def someJdk = AvailableJavaHomes.getDifferentJdk()
        buildFile << """
            plugins {
                id 'java'
            }

            java {
                toolchain {
                    languageVersion = JavaVersion.toVersion(${someJdk.javaVersion.majorVersion})
                }
            }

            // need to do as separate task as -version stop javadoc generation
            task javadocVersionOutput(type: Javadoc) {
                options.jFlags("-version")
            }
        """

        file('src/main/java/Lib.java') << testLib()

        when:
        result = executer
            .withArgument("-Porg.gradle.java.installations.auto-detect=false")
            .withArgument("-Porg.gradle.java.installations.paths=" + someJdk.javaHome.absolutePath)
            .withArgument("--info")
            .withTasks("javadoc", "javadocVersionOutput")
            .run()

        then:
        file("build/docs/javadoc/Lib.html").text.contains("Some API documentation.")
        outputContains(someJdk.javaVersion.majorVersion)
        noExceptionThrown()
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
