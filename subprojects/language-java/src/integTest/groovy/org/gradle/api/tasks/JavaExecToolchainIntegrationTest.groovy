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

package org.gradle.api.tasks;

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractPluginIntegrationTest
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.jvm.Jvm
import spock.lang.IgnoreIf
import spock.lang.Unroll

class JavaExecToolchainIntegrationTest extends AbstractPluginIntegrationTest {

    @Unroll
    @IgnoreIf({ AvailableJavaHomes.differentJdk == null })
    def "can manually set java launcher via  #type toolchain on java exec task #jdk"() {
        buildFile << """
            import org.gradle.jvm.toolchain.internal.JavaToolchainQueryService
            import org.gradle.jvm.toolchain.internal.DefaultToolchainSpec

            plugins {
                id 'java'
                id 'application'
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
                    project.tasks.withType(JavaExec) {
                        javaLauncher = toolchain.map({it.javaLauncher})
                    }
                }
            }

            apply plugin: ApplyTestToolchain

            application {
                mainClass = 'App'
            }
        """

        file('src/main/java/App.java') << testApp()

        when:
        result = executer
            .withArgument("-Porg.gradle.java.installations.auto-detect=false")
            .withArgument("-Porg.gradle.java.installations.paths=" + jdk.javaHome.absolutePath)
            .withArgument("--info")
            .withTasks("run")
            .run()

        then:
        outputContains("App running with ${jdk.javaHome.absolutePath}")
        noExceptionThrown()

        where:
        type           | jdk
        'differentJdk' | AvailableJavaHomes.getJdk(JavaVersion.VERSION_1_8)
        'current'      | Jvm.current()
    }

    @IgnoreIf({ AvailableJavaHomes.differentJdk == null })
    def "JavaExec task is configured using default toolchain"() {
        def someJdk = AvailableJavaHomes.getDifferentJdk()
        buildFile << """
            plugins {
                id 'java'
                id 'application'
            }

            java {
                toolchain {
                    languageVersion = JavaVersion.toVersion(${someJdk.javaVersion.majorVersion})
                }
            }

            application {
                mainClass = 'App'
            }
        """

        file('src/main/java/App.java') << testApp()

        when:
        result = executer
            .withArgument("-Porg.gradle.java.installations.auto-detect=false")
            .withArgument("-Porg.gradle.java.installations.paths=" + someJdk.javaHome.absolutePath)
            .withArgument("--info")
            .withTasks("run")
            .run()

        then:
        outputContains("App running with ${someJdk.javaHome.absolutePath}")
        noExceptionThrown()
    }

    private static String testApp() {
        return """
            public class App {
               public static void main(String[] args) {
                 System.out.println("App running with " + System.getProperty("java.home"));
               }
            }
        """.stripIndent()
    }

}
