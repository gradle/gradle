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

package org.gradle.buildinit.plugins

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.util.GradleVersion
import spock.lang.IgnoreIf


@IgnoreIf({ GradleContextualExecuter.configCache })
class CustomTypeInitIntegrationTest extends AbstractIntegrationSpec {
    private List<String> localArgs = []

    private void setCustomInitOptions(String... args) {
        this.localArgs = args.toList()
    }

    protected ExecutionResult runWithOptions(String... tasks) {
        return super.run(*(tasks.toList() + localArgs + ["-s", "--no-daemon"]))
    }

    def "can run a plug-in from the Gradle Plug-in Portal"() {
        when:
        setCustomInitOptions('-Dorg.gradle.custom-init.plugin=org.gradle.hello-world', '-Dorg.gradle.custom-init.version=0.2')

        runWithOptions 'helloWorld'

        then:
        outputContains("Hello World!")
    }

    def "can run a custom plugin that takes options"() {
        when:
        withCustomPlugin()
        setCustomInitOptions("-Dorg.gradle.custom-init.repository=${mavenRepo.uri}", '-Dorg.gradle.custom-init.plugin=custom-hello-plugin', '-Dorg.gradle.custom-init.version=1.0')

        runWithOptions 'custom-hello', '--person', 'John Doe'

        then:
        outputContains("Hello, John Doe")
    }

    def "can run a custom plugin task as default task"() {
        when:
        withCustomPlugin()
        setCustomInitOptions("-Dorg.gradle.custom-init.repository=${mavenRepo.uri}", '-Dorg.gradle.custom-init.plugin=custom-hello-plugin', '-Dorg.gradle.custom-init.version=1.0', '-Dorg.gradle.custom-init.task=custom-hello')

        // no way to pass custom options though
        runWithOptions()

        then:
        outputContains("Hello, null")
    }

    def "can run a community plugin task without a project"() {
        given:
        setCustomInitOptions('-Dorg.gradle.custom-init.plugin=com.github.h0tk3y.gradle.eval', '-Dorg.gradle.custom-init.version=0.0.4')

        when:
        runWithOptions 'eval', """--command="Gradle version is \${gradle.gradleVersion}" """

        then:
        outputContains("Gradle version is ${GradleVersion.current().version}")
    }

    def "can run a community plugin task against a project"() {
        given:
        settingsFile """
        rootProject.name = "hello-world"
        """

        setCustomInitOptions('-Dorg.gradle.custom-init.plugin=com.github.h0tk3y.gradle.eval', '-Dorg.gradle.custom-init.version=0.0.4')

        when:
        runWithOptions 'eval', """--command="Gradle project name is '\${gradle.rootProject.name}'" """

        then:
        outputContains("Gradle project name is 'hello-world'")
    }

    private void withCustomPlugin() {
        createDir('plugin') {
            file("src/main/java/CustomHelloPlugin.java") << """
                abstract class CustomHelloTask extends ${DefaultTask.name} {
                    public CustomHelloTask() {}
                    @${Input.name}
                    @${Optional.name}
                    @${Option.name}(option = "person", description = "")
                    public abstract $Property.name<String> getPersonName();

                    @${TaskAction.name}
                    public void sayIt() {
                        System.out.println("Hello, " + getPersonName().getOrNull());
                    }

                }

                public abstract class CustomHelloPlugin implements ${Plugin.name}<$Project.name> {

                    @Override
                    public void apply($Project.name project) {
                        project.getTasks().register("custom-hello", CustomHelloTask.class);
                    }
                }
            """
            file("build.gradle") << """
                plugins {
                    id("java-gradle-plugin")
                    id("maven-publish")
                }
                group = "com.example"
                version = "1.0"
                publishing {
                    repositories {
                        maven { url '$mavenRepo.uri' }
                    }
                }
                gradlePlugin {
                    plugins {
                        customHelloPlugin {
                            id = 'custom-hello-plugin'
                            implementationClass = 'CustomHelloPlugin'
                        }
                    }
                }
            """
        }
        executer.inDirectory(file("plugin")).withTasks("publish").run()
    }
}
