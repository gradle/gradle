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

package org.gradle.api.tasks

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class TaskExecutionInCompositeIntegrationTest extends AbstractIntegrationSpec {

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

    def "Does not run non-qualified tasks"() {
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
        output.contains("[sub] do something")
        !output.contains("[sub/subsub] do something")
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


    //@NotYetImplemented // "Task exclusion doesn't work"
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
    }

    //@NotYetImplemented // the current setup configures the bridge task
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

    //@NotYetImplemented // Help task prints details of the bridge task at the moment
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
}
