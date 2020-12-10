/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.composite

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class CompositeBuildTaskExecutionIntegrationTest extends AbstractIntegrationSpec {

    def "can run included root project task"() {
        setup:
        settingsFile << "includeBuild('other-build')"
        file('other-build/settings.gradle') << "rootProject.name = 'other-build'"
        file('other-build/build.gradle') << """
            tasks.register('doSomething') {
                doLast {
                    println 'do something'
                }
            }
        """

        expect:
        succeeds(":other-build:doSomething")
    }

    def "can run included build task included with --include-build"() {
        setup:
        settingsFile << ""
        file('other-build/settings.gradle') << "rootProject.name = 'other-build'"
        file('other-build/build.gradle') << """
            tasks.register('doSomething') {
                doLast {
                    println 'do something'
                }
            }
        """

        expect:
        succeeds("--include-build", "other-build", ":other-build:doSomething")
    }

    def "can run included subproject task"() {
        setup:
        settingsFile << "includeBuild('other-build')"
        file('other-build/settings.gradle') << """
            rootProject.name = 'other-build'
            include 'sub'
        """
        file('other-build/sub/build.gradle') << """
            tasks.register('doSomething') {
                doLast {
                    println 'do something'
                }
            }
        """

        expect:
        succeeds(":other-build:sub:doSomething")
    }

    def "only absolute task paths can be used to target included builds"() {
        setup:
        settingsFile << "includeBuild('other-build')"
        file('other-build/settings.gradle') << """
            rootProject.name = 'other-build'
            include 'sub'
            include 'sub:subsub'
        """
        file('other-build/sub/build.gradle') << """
            tasks.register('doSomething') {
                doLast {
                    println '[sub] do something'
                }
            }
        """
        file('other-build/sub/subsub/build.gradle') << """
            tasks.register('doSomething') {
                doLast {
                    println '[sub/subsub] do something'
                }
            }
        """

        expect:
        succeeds(":other-build:sub:doSomething")
        fails("other-build:sub:doSomething")
    }

    def "can run task from included build with applied plugin"() {
        setup:
        buildFile << """
            plugins {
                id 'other.plugin'
            }
        """
        settingsFile << """
            pluginManagement {
                includeBuild('other-plugin')
            }
        """
        file('other-plugin/settings.gradle') << "rootProject.name = 'other-plugin'"
        file('other-plugin/build.gradle') << """
            plugins {
                id 'java-gradle-plugin'
            }

            repositories {
                ${jcenterRepository()}
            }

            gradlePlugin {
                plugins {
                    greeting {
                        id = 'other.plugin'
                        implementationClass = 'com.example.OtherPlugin'
                    }
                }
            }

            tasks.register('taskFromIncludedPlugin') {
                doLast {
                    println 'Task from included plugin'
               }
            }
        """
        file('other-plugin/src/main/java/com/example/OtherPlugin.java') << """
            package com.example;
            import org.gradle.api.*;
            public class OtherPlugin implements Plugin<Project> {
                public void apply(Project project) {
                    project.getTasks().register("greeting", task -> {
                        task.doLast(s -> System.out.println("Hello world"));
                    });
                }
            }
        """

        expect:
        succeeds(":other-plugin:taskFromIncludedPlugin")
        output.count(":other-plugin:jar") == 1
    }

    def "can pass options to task in included build"() {
        setup:
        settingsFile << "includeBuild('other-build')"
        file('other-build/settings.gradle') << "rootProject.name = 'other-build'"
        file('other-build/build.gradle') << """
            tasks.register('doSomething', MyTask)

            class MyTask extends DefaultTask {
                private String content = 'default content'

                @Option(option = "content", description = "Message to print")
                public void setContent(String content) {
                    this.content = content
                }

                @TaskAction
                public void run() {
                    println content
                }
            }
        """

        succeeds(":other-build:doSomething", "--content", "do something")
    }

    def "can list tasks from included build"() {
        setup:
        settingsFile << "includeBuild('other-build')"
        file('other-build/settings.gradle') << "rootProject.name = 'other-build'"
        file('other-build/build.gradle') << """
            tasks.register('doSomething') {
                description = "Prints the message 'do something'"
                doLast {
                    println 'do something'
                }
            }
        """

        expect:
        succeeds(":other-build:tasks", "--all")
        outputContains("doSomething - Prints the message 'do something'")
    }

    def "can run help from included build"() {
        setup:
        settingsFile << "includeBuild('other-build')"
        file('other-build/settings.gradle') << "rootProject.name = 'other-build'"
        file('other-build/build.gradle') << """
            tasks.register('doSomething') {
                description = "Prints the message 'do something'"
                doLast {
                    println 'do something'
                }
            }
        """

        expect:
        succeeds("help", "--task", ":other-build:doSomething")
        outputContains("Prints the message 'do something'")
    }

    def "can use pattern matching to address tasks"() {
        setup:
        settingsFile << "includeBuild('other-build')"
        file('other-build/settings.gradle') << "rootProject.name = 'other-build'"
        file('other-build/build.gradle') << """
            tasks.register('doSomething') {
                description = "Prints the message 'do something'"
                doLast {
                    println 'do something'
                }
            }
        """

        expect:
        succeeds(":other-build:dSo")
        outputContains("do something")
    }

    def "can run tasks from transitive included builds"() {
        setup:
        settingsFile << """
            rootProject.name = 'root-project'
            includeBuild('other-build')
        """
        file('other-build/settings.gradle') << """
            rootProject.name = 'other-build'
            includeBuild('../third-build')
        """
        file('third-build/settings.gradle') << """
            rootProject.name = 'third-build'
            include('sub')
        """

        file('third-build/sub/build.gradle') << """
            tasks.register('doSomething') {
                doLast {
                    println 'do something'
                }
            }
        """

        expect:
        succeeds(":third-build:sub:doSomething")
    }

    def "task in an included build cannot be addressed with name patterns"() {
        setup:
        settingsFile << "includeBuild('other-build')"
        file('other-build/settings.gradle') << "rootProject.name = 'other-build'"
        file('other-build/build.gradle') << """
            tasks.register('doSomething') {
                doLast {
                    println 'do something'
                }
            }
        """

        expect:
        fails(":oB:doSo").assertHasDescription("Project 'oB' not found in root project")
    }

    def "gives reasonable error message when a task does not exist in the referenced included build"() {
        setup:
        settingsFile << """
            rootProject.name = 'root-project'
            includeBuild('other-build')
        """
        file('other-build/settings.gradle') << """
            rootProject.name = 'other-build'
        """

        expect:
        fails(":other-build:nonexistent").assertHasDescription("Task 'nonexistent' not found in project ':other-build'")
    }

    def "gives reasonable error message when a project does not exist in the referenced included build"() {
        setup:
        settingsFile << """
            rootProject.name = 'root-project'
            includeBuild('other-build')
        """
        file('other-build/settings.gradle') << """
            rootProject.name = 'other-build'
        """

        expect:
        fails(":other-build:sub:nonexistent").assertHasDescription("Project 'sub' not found in project ':other-build'.")
    }

    def "handles overlapping names between composite and a subproject within the composite"() {
        setup:
        settingsFile << """
            rootProject.name = 'root-project'
            includeBuild('lib')
        """
        file('lib/settings.gradle') << """
            include('lib')
        """
        file('lib/lib/build.gradle') << """
            tasks.register('doSomething') {
                doLast {
                    println 'do something'
                }
            }
        """

        expect:
        succeeds(":lib:lib:doSomething")
        outputContains("do something")
    }

    def "can run task from included build via task reference"() {
        setup:
        settingsFile << """
            rootProject.name = 'root-project'
            includeBuild('lib')
        """
        buildFile << """
            tasks.register('doSomething') {
                dependsOn gradle.includedBuild('lib').task(':lib:doSomething')
            }
        """

        file('lib/settings.gradle') << """
            include('lib')
        """
        file('lib/lib/build.gradle') << """
            tasks.register('doSomething') {
                doLast {
                    println 'do something'
                }
            }
        """

        expect:
        succeeds("doSomething")
        outputContains("do something")
    }
}
