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

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class CompositeBuildTaskExecutionIntegrationTest extends AbstractIntegrationSpec {

    def "Can run included root project task"() {
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

    def "Can run included subproject task"() {
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

    def "Selects only the exact task and ignores tasks with the same name in subprojects"() {
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
        succeeds(task)
        output.contains("[sub] do something")
        !output.contains("[sub/subsub] do something")

        where:
        task << ["other-build:sub:doSomething", ":other-build:sub:doSomething"]
    }

    def "Can run task from included build with applied plugin"() {
        setup:
        buildFile << """
            plugins {
                id 'other.plugin'
            }
        """
        settingsFile << "includeBuild('other-plugin')"
        file('other-plugin/settings.gradle') << "rootProject.name = 'other-plugin'"
        file('other-plugin/build.gradle') << """
            plugins {
                id 'java-gradle-plugin'
            }

            repositories {
                jcenter()
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

    @NotYetImplemented
    def "Can exclude tasks coming from included builds"() {
        setup:
        settingsFile << "includeBuild('other-build')"
        file('other-build/settings.gradle') << "rootProject.name = 'other-build'"
        file('other-build/build.gradle') << """
             def setupTask = tasks.register('setupTask') { task ->
                doLast {
                    println 'Setup task'
                }
            }
            tasks.register('doSomething') { task ->
                task.dependsOn setupTask
                doLast {
                    println 'do something'
                }
            }
        """

        expect:
        succeeds(":other-build:doSomething", "-x", ":other-build:setupTask")
        output.contains(":other-build:setupTask SKIPPED")
    }

    def "Can pass options to task in included build"() {
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

    def "Can call help on task from included build"() {
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
        output.contains("Prints the message 'do something'")
    }

    def "Can use pattern matching to address tasks"() {
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
        output.contains("do something")
    }

    def "Can run tasks from transitive included builds"() {
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

    def "Can use abbreviated names to reference included build"() {
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
        succeeds(":oB:doSo")
    }

    def "Gives reasonable error message when a task does not exist in the referenced included build"() {
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

    def "Gives reasonable error message when a project does not exist in the referenced included build"() {
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
}
