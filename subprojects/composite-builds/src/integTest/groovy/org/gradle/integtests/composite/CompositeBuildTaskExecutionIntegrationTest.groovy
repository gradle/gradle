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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile

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
        2.times {
            succeeds(":other-build:doSomething")
            outputContains("do something")
        }
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
        2.times {
            succeeds(":other-build:sub:doSomething")
            outputContains("do something")
        }
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

    def "can run task from included build that also produces a plugin used from root build"() {
        setup:
        buildFile << """
            plugins {
                id 'test.plugin'
            }
        """
        settingsFile << """
            pluginManagement {
                includeBuild('other-plugin')
            }
        """

        def rootDir = file("other-plugin")
        addPluginIncludedBuild(rootDir)
        rootDir.file("build.gradle") << """
            tasks.register('taskFromIncludedPlugin') {
                doLast {
                    println 'Task from included plugin'
               }
            }
        """

        expect:
        succeeds(":other-plugin:taskFromIncludedPlugin")
        result.assertTaskExecuted(":other-plugin:taskFromIncludedPlugin")
        result.assertTaskExecuted(":other-plugin:jar")

        succeeds(":other-plugin:taskFromIncludedPlugin")
        result.assertTaskExecuted(":other-plugin:taskFromIncludedPlugin")
        // build logic tasks do not run when configuration cache is enabled
    }

    def "can run task from included build that is also required to produce a plugin used from root build"() {
        setup:
        settingsFile << "includeBuild('build-logic')"
        def rootDir = file("build-logic")
        addPluginIncludedBuild(rootDir)
        buildFile("""
            plugins {
                id("test.plugin")
                id("java-library")
            }
            dependencies {
                implementation("lib:lib:1.0")
            }
        """)

        expect:
        succeeds(":build-logic:classes")
        result.assertTaskExecuted(":build-logic:classes")

        succeeds(":build-logic:classes")
        // build logic tasks are not run when configuration cache is enabled (because their inputs are encoded in the cache key)
    }

    def "can run task from included build that also produces a plugin used from root build when configure on demand is enabled"() {
        setup:
        settingsFile << """
            includeBuild('build-logic')
            include("app")
            include("util")
        """
        def rootDir = file("build-logic")
        addPluginIncludedBuild(rootDir)

        // App project does not use the plugin, but depends on a project that does
        file("app/build.gradle") << """
            plugins {
                id("java-library")
            }
            dependencies {
                implementation(project(":util"))
            }
        """
        file("util/build.gradle") << """
            plugins {
                id("test.plugin")
                id("java-library")
            }
        """

        expect:
        2.times {
            succeeds(":build-logic:jar", ":app:assemble")
        }
        2.times {
            executer.withArgument("--configure-on-demand")
            succeeds(":build-logic:jar", ":app:assemble")
        }
    }

    def addPluginIncludedBuild(TestFile rootDir, String group = "lib", String name = "lib") {
        rootDir.file("settings.gradle") << """
            rootProject.name="$name"
        """
        rootDir.file("build.gradle") << """
            plugins {
                id("java-gradle-plugin")
            }
            group = "$group"
            gradlePlugin {
                plugins {
                    p {
                        id = "test.plugin"
                        implementationClass = "test.PluginImpl"
                    }
                }
            }
        """
        rootDir.file("src/main/java/test/PluginImpl.java") << """
            package test;

            import ${Project.name};
            import ${Plugin.name};

            public class PluginImpl implements Plugin<Project> {
                public void apply(Project project) {
                    project.getTasks().register("greeting", task -> {
                        task.doLast(s -> System.out.println("Hello world"));
                    });
                }
            }
        """
    }
}
