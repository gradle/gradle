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

package org.gradle.integtests.tooling.r68

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion

@ToolingApiVersion(">=3.0")
@TargetGradleVersion('>=6.8')
class CompositeBuildTaskExecutionCrossVersionSpec extends ToolingApiSpecification {

    def "can run included root project task"() {
        given:
        settingsFile << "includeBuild('other-build')"
        file('other-build/settings.gradle') << "rootProject.name = 'other-build'"
        file('other-build/build.gradle') << """
            tasks.register('doSomething') {
                doLast {
                    println 'do something'
                }
            }
        """

        when:
        executeTaskViaTAPI(":other-build:doSomething")

        then:
        assertHasBuildSuccessfulLogging()
        outputContains("do something")
    }

    def "can run included subproject task"() {
        given:
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

        when:
        executeTaskViaTAPI(":other-build:sub:doSomething")

        then:
        assertHasBuildSuccessfulLogging()
        outputContains("do something")
    }

    def "only absolute task paths can be used to target included builds"() {
        given:
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

        when:
        executeTaskViaTAPI("other-build:sub:doSomething")

        then:
        def exception = thrown(Exception)
        exception.cause.message.contains("Project 'other-build' not found in root project")
    }

    def "can pass options to task in included build"() {
        given:
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

        when:
        executeTaskViaTAPI(":other-build:doSomething", "--content", "do something")

        then:
        assertHasBuildSuccessfulLogging()
        outputContains("do something")
    }

    def "can list tasks from included build"() {
        given:
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

        when:
        executeTaskViaTAPI(":other-build:tasks", "--all")

        then:
        assertHasBuildSuccessfulLogging()
        outputContains("doSomething - Prints the message 'do something'")
    }

    def "can run help from included build"() {
        given:
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

        when:
        executeTaskViaTAPI("help", "--task", ":other-build:doSomething")

        then:
        assertHasBuildSuccessfulLogging()
        outputContains("Prints the message 'do something'")
    }

    def "can use pattern matching to address tasks"() {
        given:
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

        when:
        executeTaskViaTAPI(":other-build:dSo")

        then:
        assertHasBuildSuccessfulLogging()
        outputContains("do something")
    }

    def "can run tasks from transitive included builds"() {
        given:
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

        when:
        executeTaskViaTAPI(":third-build:sub:doSomething")

        then:
        assertHasBuildSuccessfulLogging()
        outputContains("do something")
    }

    def "included build name can not be name patterns to execute a task"() {
        given:
        settingsFile << "includeBuild('other-build')"
        file('other-build/settings.gradle') << "rootProject.name = 'other-build'"
        file('other-build/build.gradle') << """
            tasks.register('doSomething') {
                doLast {
                    println 'do something'
                }
            }
        """

        when:
        executeTaskViaTAPI(":oB:doSo")

        then:
        def exception = thrown(Exception)
        exception.cause.message.contains("Project 'oB' not found in root project")
    }

    def "gives reasonable error message when a task does not exist in the referenced included build"() {
        given:
        settingsFile << """
            rootProject.name = 'root-project'
            includeBuild('other-build')
        """
        file('other-build/settings.gradle') << """
            rootProject.name = 'other-build'
        """

        when:
        executeTaskViaTAPI(":other-build:nonexistent")

        then:
        def exception = thrown(Exception)
        exception.cause.message.contains("Task 'nonexistent' not found in project ':other-build'.")
    }

    def "gives reasonable error message when a project does not exist in the referenced included build"() {
        given:
        settingsFile << """
            rootProject.name = 'root-project'
            includeBuild('other-build')
        """
        file('other-build/settings.gradle') << """
            rootProject.name = 'other-build'
        """

        when:
        executeTaskViaTAPI(":other-build:sub:nonexistent")

        then:
        def exception = thrown(Exception)
        exception.cause.message.contains("Project 'sub' not found in project ':other-build'.")
    }

    def "handles overlapping names between composite and a subproject within the composite"() {
        given:
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

        when:
        executeTaskViaTAPI(":lib:lib:doSomething")

        then:
        assertHasBuildSuccessfulLogging()
        outputContains("do something")
    }

    private void executeTaskViaTAPI(String... task) {
        withConnection { connection ->
            def build = connection.newBuild()
            collectOutputs(build)
            build.forTasks(task).run()
        }
    }

    private boolean outputContains(String expectedOutput) {
        return stdout.toString().contains(expectedOutput)
    }
}
