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

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractPluginIntegrationTest
import org.gradle.integtests.fixtures.AvailableJavaHomes

class JavaCompileToolchainIntegrationTest extends AbstractPluginIntegrationTest {

    def "can manually set java compiler via toolchain on java compile task"() {
        buildFile << """
            import org.gradle.jvm.toolchain.internal.JavaToolchainQueryService

            apply plugin: "java"

            abstract class InstallToolchain implements Plugin<Project> {
                @javax.inject.Inject
                abstract JavaToolchainQueryService getQueryService()

                void apply(Project project) {
                    project.tasks.withType(JavaCompile) {
                        javaCompiler = getQueryService().findMatchingToolchain().map({it.javaCompiler.get()})
                    }
                }
            }

            apply plugin: InstallToolchain
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        def jdk14 = AvailableJavaHomes.getJdk(JavaVersion.VERSION_14)
        result = executer
            .withArguments("-Porg.gradle.java.installations.paths=" + jdk14.javaHome.absolutePath, "--info")
            .withTasks("compileJava")
            .run()

        then:
        outputContains("Toolchain selected: 14")
        javaClassFile("Foo.class").exists()
    }

}
