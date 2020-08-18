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

package org.gradle.testing

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractPluginIntegrationTest
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.jvm.Jvm
import spock.lang.IgnoreIf
import spock.lang.Unroll

class TestTaskToolchainIntegrationTest extends AbstractPluginIntegrationTest {

    @Unroll
    @IgnoreIf({ AvailableJavaHomes.differentJdk == null })
    def "can manually set java launcher via  #type toolchain on java test task #jdk"() {
        buildFile << """
            import org.gradle.jvm.toolchain.internal.JavaToolchainQueryService
            import org.gradle.jvm.toolchain.internal.DefaultToolchainSpec

            apply plugin: "java"

            ${jcenterRepository()}

            dependencies {
                testImplementation 'junit:junit:4.13'
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
                    project.tasks.withType(Test) {
                        javaLauncher = toolchain.map({it.javaLauncher})
                    }
                }
            }

            apply plugin: ApplyTestToolchain
        """

        file('src/test/java/ToolchainTest.java') << testClass("ToolchainTest")

        when:
        result = executer
            .withArgument("-Porg.gradle.java.installations.auto-detect=false")
            .withArgument("-Porg.gradle.java.installations.paths=" + jdk.javaHome.absolutePath)
            .withArgument("--info")
            .withTasks("test")
            .run()

        then:
        outputContains("Tests running with ${jdk.javaHome.absolutePath}")
        noExceptionThrown()

        where:
        type           | jdk
        'differentJdk' | AvailableJavaHomes.getJdk(JavaVersion.VERSION_1_8)
        'current'      | Jvm.current()
    }

    @IgnoreIf({ AvailableJavaHomes.differentJdk == null })
    def "Test task is configured using default toolchain"() {
        def someJdk = AvailableJavaHomes.getDifferentJdk()
        buildFile << """
            apply plugin: "java"

            ${jcenterRepository()}

            dependencies {
                testImplementation 'junit:junit:4.13'
            }

            java {
                toolchain {
                    languageVersion = JavaVersion.toVersion(${someJdk.javaVersion.majorVersion})
                }
            }
        """

        file('src/test/java/ToolchainTest.java') << testClass("ToolchainTest")

        when:
        result = executer
            .withArguments("-Porg.gradle.java.installations.paths=" + someJdk.javaHome.absolutePath, "--info")
            .withTasks("test")
            .run()

        then:
        outputContains("Tests running with ${someJdk.javaHome.absolutePath}")
        noExceptionThrown()
    }

    private static String testClass(String className) {
        return """
            import org.junit.*;

            public class $className {
               @Test
               public void test() {
                  System.out.println("Tests running with " + System.getProperty("java.home"));
                  Assert.assertEquals(1,1);
               }
            }
        """.stripIndent()
    }

}
