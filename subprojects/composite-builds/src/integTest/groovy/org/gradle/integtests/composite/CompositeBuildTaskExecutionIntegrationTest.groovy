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

class CompositeBuildTaskExecutionIntegrationTest extends AbstractCompositeBuildTaskExecutionIntegrationTest {

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
        2.times {
            succeeds("other-build:doSomething")
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

    def "reports candidates when task path cannot be resolved"() {
        createDirs("aaBb", "aaaBbb", "aaaaBbbb", "aaBb/ccDD", "aaBb/cccDDD")
        settingsFile << """
            rootProject.name = 'broken'
            includeBuild("aaBb")
            include("aaaBbb")
            include("aaaaBbbb")
        """
        file("aaBb/settings.gradle") << """
            include("ccDD")
            include("cccDDD")
        """

        expect:
        fails(requested)
        failure.assertHasDescription(message)

        where:
        requested            | message
        "unknown"            | "Task 'unknown' not found in root project 'broken' and its subprojects."
        "unknown:help"       | "Cannot locate tasks that match 'unknown:help' as project 'unknown' not found in root project 'broken'."
        ":unknown:help"      | "Cannot locate tasks that match ':unknown:help' as project 'unknown' not found in root project 'broken'."
        "aB:help"            | "Cannot locate tasks that match 'aB:help' as project 'aB' is ambiguous in root project 'broken'. Candidates are: 'aaBb', 'aaaBbb', 'aaaaBbbb'."
        "aaBb:unknown"       | "Cannot locate tasks that match 'aaBb:unknown' as task 'unknown' not found in project ':aaBb'."
        "aaBb:unknown:help"  | "Cannot locate tasks that match 'aaBb:unknown:help' as project 'unknown' not found in project ':aaBb'."
        ":aB:help"           | "Cannot locate tasks that match ':aB:help' as project 'aB' is ambiguous in root project 'broken'. Candidates are: 'aaBb', 'aaaBbb', 'aaaaBbbb'."
        ":aaBb:cD:help"      | "Cannot locate tasks that match ':aaBb:cD:help' as project 'cD' is ambiguous in project ':aaBb'. Candidates are: 'ccDD', 'cccDDD'."
        ":aaBb:ccDD:unknown" | "Cannot locate tasks that match ':aaBb:ccDD:unknown' as task 'unknown' not found in project ':aaBb:ccDD'."
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

    def "can use pattern matching to address tasks in included build"() {
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
        result.assertTaskExecuted(":other-build:doSomething")
        outputContains("do something")

        succeeds(":o-b:doSomething")
        result.assertTaskExecuted(":other-build:doSomething")
        outputContains("do something")

        succeeds("o-b:dS")
        result.assertTaskExecuted(":other-build:doSomething")
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

    def "can run task from included build that requires a plugin from another build"() {
        setup:
        settingsFile << """
            includeBuild('build-logic')
            includeBuild('app')
        """
        def rootDir = file("build-logic")
        addPluginIncludedBuild(rootDir)
        file("app/build.gradle") << """
            plugins {
                id("test.plugin")
                id("java-library")
            }
        """

        expect:
        2.times {
            succeeds(":app:assemble")
            result.assertTaskExecuted(":app:processResources")
        }
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

}
