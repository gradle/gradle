/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.plugin.devel.plugins

import org.gradle.integtests.fixtures.AbstractProjectRelocationIntegrationTest
import org.gradle.test.fixtures.file.TestFile

class JavaGradlePluginRelocationTest extends AbstractProjectRelocationIntegrationTest {
    String taskName = ":test"

    @Override
    protected void setupProjectIn(TestFile projectDir) {
        projectDir.with {
            file("src/main/groovy/CustomTask.groovy") << """
                import org.gradle.api.*
                import org.gradle.api.tasks.*

                @CacheableTask
                class CustomTask extends DefaultTask {
                    @InputFile @PathSensitive(PathSensitivity.NONE) File inputFile
                    @OutputFile File outputFile
                    @TaskAction void doSomething() {
                        outputFile.text = inputFile.text
                    }
                }
            """

            file("src/main/groovy/CustomPlugin.groovy") << """
                import org.gradle.api.*

                class CustomPlugin implements Plugin<Project> {
                    @Override
                    void apply(Project project) {
                        project.tasks.create("customTask", CustomTask) {
                            inputFile = project.file("input.txt")
                            outputFile = project.file("build/output.txt")
                        }
                    }
                }
            """
            file("src/test/groovy/PluginSpec.groovy") << """
                import spock.lang.Specification

                class PluginSpec extends Specification {
                    def "dummy test"() {
                        expect:
                        true
                    }
                }
            """
            file("build.gradle") << """
                apply plugin: "java-gradle-plugin"
                apply plugin: "groovy"

                gradlePlugin {
                    plugins {
                        examplePlugin {
                            id = "org.example.plugin"
                            implementationClass = "CustomPlugin"
                        }
                    }
                }

                ${mavenCentralRepository()}

                testing {
                    suites {
                        test {
                            useSpock()
                        }
                    }
                }
            """
        }

    }

    @Override
    protected extractResultsFrom(TestFile projectDir) {
        return projectDir.file("build/${JavaGradlePluginPlugin.GENERATE_PLUGIN_DESCRIPTORS_TASK_NAME}/org.example.plugin.properties").text
    }
}
