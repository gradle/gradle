/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.plugin.devel.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.GroovyBuildScriptLanguage
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.gradle.test.fixtures.file.TestFile

class TaskFromPluginValidationIntegrationTest extends AbstractIntegrationSpec implements ValidationMessageChecker {

    def setup() {
        expectReindentedValidationMessage()
    }

    def "detects that a problem is from a task declared in a precompiled script plugin"() {
        withPrecompiledScriptPlugins()
        def pluginFile = file("buildSrc/src/main/groovy/test.gradle.demo.plugin.gradle")
        writeTaskInto(pluginFile)
        pluginFile << """
            tasks.register("myTask", SomeTask) {
                input.set("hello")
                output.set(layout.buildDirectory.file("out.txt"))
            }
        """

        buildFile """plugins {
            id 'test.gradle.demo.plugin'
        }"""

        when:
        fails ':myTask'

        then:
        failureDescriptionContains("In plugin 'test.gradle.demo.plugin' type 'SomeTask' property 'input' is missing")
    }

    def "detects that a problem is from a task declared in plugin"() {
        settingsFile << """
            includeBuild 'my-plugin'
        """
        def pluginFile = file("my-plugin/src/main/groovy/org/gradle/demo/plugin/MyTask.groovy")
        writeTaskInto("""package org.gradle.demo.plugin

            import org.gradle.api.*
            import org.gradle.api.file.*
            import org.gradle.api.provider.*
            import org.gradle.api.tasks.*
        """, pluginFile)

        file("my-plugin/build.gradle") << """
            plugins {
                id 'groovy-gradle-plugin'
            }

            gradlePlugin {
                plugins {
                    create("simplePlugin") {
                        id = "org.gradle.demo.plugin"
                        implementationClass = "org.gradle.demo.plugin.MyPlugin"
                    }
                }
            }
        """
        file("my-plugin/settings.gradle") << """
            rootProject.name = "my-plugin"
        """
        file("my-plugin/src/main/groovy/org/gradle/demo/plugin/MyPlugin.groovy") << """package org.gradle.demo.plugin
            import org.gradle.api.*
            class MyPlugin implements Plugin<Project> {
                void apply(Project p) {
                    p.tasks.register("myTask", SomeTask) {
                        it.input.set("hello")
                        it.output.set(p.layout.buildDirectory.file("out.txt"))
                    }
                }
            }
        """

        buildFile """plugins {
            id 'org.gradle.demo.plugin'
        }"""

        when:
        fails ':myTask'

        then:
        failureDescriptionContains("In plugin 'org.gradle.demo.plugin' type 'org.gradle.demo.plugin.SomeTask' property 'input' is missing")
    }

    private TestFile withPrecompiledScriptPlugins() {
        file("buildSrc/build.gradle") << """
            plugins {
                id 'groovy-gradle-plugin'
            }
        """
    }

    private void writeTaskInto(@GroovyBuildScriptLanguage String header = "", TestFile testFile) {
        testFile << """$header
            abstract class SomeTask extends DefaultTask {
                abstract Property<String> getInput()

                @OutputFile
                abstract RegularFileProperty getOutput()

                @TaskAction
                void doSomething() {}
            }
        """
    }
}
