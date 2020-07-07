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

package org.gradle.api.tasks.compile


import org.gradle.integtests.fixtures.AbstractPluginIntegrationTest
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.jvm.Jvm

class JavaCompileToolchainIntegrationTest extends AbstractPluginIntegrationTest {

    def "can manually set java compiler via toolchain on java compile task"() {
        def someJdk = AvailableJavaHomes.getDifferentJdk()
        buildFile << """
            import org.gradle.jvm.toolchain.internal.JavaToolchainQueryService
            import org.gradle.jvm.toolchain.internal.DefaultToolchainSpec

            apply plugin: "java"

            abstract class InstallToolchain implements Plugin<Project> {
                @javax.inject.Inject
                abstract JavaToolchainQueryService getQueryService()

                void apply(Project project) {
                    project.tasks.withType(JavaCompile) {
                        javaCompiler = getQueryService().findMatchingToolchain(new DefaultToolchainSpec(JavaVersion.${someJdk.javaVersion.name()})).map({it.javaCompiler.get()})
                    }
                }
            }

            apply plugin: InstallToolchain
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        result = executer
            .withArguments("-Porg.gradle.java.installations.paths=" + someJdk.javaHome.absolutePath, "--info")
            .withTasks("compileJava")
            .run()

        then:
        outputContains("Compiling with toolchain '${someJdk.javaHome.absolutePath}'.")
        javaClassFile("Foo.class").exists()
    }

    def "can query for running vm as toolchain"() {
        buildFile << """
            import org.gradle.jvm.toolchain.internal.JavaToolchainQueryService
            import org.gradle.jvm.toolchain.internal.DefaultToolchainSpec

            apply plugin: "java"

            abstract class InstallToolchain implements Plugin<Project> {
                @javax.inject.Inject
                abstract JavaToolchainQueryService getQueryService()

                void apply(Project project) {
                    project.tasks.withType(JavaCompile) {
                        javaCompiler = getQueryService().findMatchingToolchain(new DefaultToolchainSpec(JavaVersion.current())).map({it.javaCompiler.get()})
                    }
                }
            }

            apply plugin: InstallToolchain
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        result = executer
            .withArguments("--info")
            .withTasks("compileJava")
            .run()

        then:
        outputContains("Compiling with toolchain '${Jvm.current().javaHome.absolutePath}'.")
        javaClassFile("Foo.class").exists()
    }

}
