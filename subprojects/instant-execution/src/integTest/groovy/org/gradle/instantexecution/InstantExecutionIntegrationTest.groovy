/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.instantexecution

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.initialization.LoadProjectsBuildOperationType
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.Unroll

class InstantExecutionIntegrationTest extends AbstractInstantExecutionIntegrationTest {

    def "instant execution for help on empty project"() {
        given:
        instantRun "help"
        def firstRunOutput = result.normalizedOutput.replace('Calculating task graph as no instant execution cache is available for tasks: help', '')

        when:
        instantRun "help"

        then:
        firstRunOutput == result.normalizedOutput.replace('Reusing instant execution cache. This is not guaranteed to work in any way.', '')
    }

    def "restores some details of the project structure"() {
        def fixture = new BuildOperationsFixture(executer, temporaryFolder)

        settingsFile << """
            rootProject.name = 'thing'
        """

        when:
        instantRun "help"

        then:
        def event = fixture.first(LoadProjectsBuildOperationType)
        event.result.rootProject.name == 'thing'

        when:
        instantRun "help"

        then:
        def event2 = fixture.first(LoadProjectsBuildOperationType)
        event2.result.rootProject.name == 'thing'
    }

    def "does not configure build when task graph is already cached for requested tasks"() {
        given:
        buildFile << """
            println "running build script"
            task a {}
            task b {
                dependsOn a
            }
        """

        when:
        instantRun "a"

        then:
        outputContains("running build script")
        result.assertTasksExecuted(":a")

        when:
        instantRun "a"

        then:
        outputDoesNotContain("running build script")
        result.assertTasksExecuted(":a")

        when:
        instantRun "b"

        then:
        outputContains("running build script")
        result.assertTasksExecuted(":a", ":b")

        when:
        instantRun "a"

        then:
        outputDoesNotContain("running build script")
        result.assertTasksExecuted(":a")
    }

    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def "instant execution for task in multiple projects"() {
        server.start()

        given:
        settingsFile << """
            include 'a', 'b', 'c'
        """
        buildFile << """
            class SlowTask extends DefaultTask {
                @TaskAction
                def go() {
                    ${server.callFromBuildUsingExpression("project.name")}
                }
            }

            subprojects {
                tasks.create('slow', SlowTask)
            }
            project(':a') {
                tasks.slow.dependsOn(project(':b').tasks.slow, project(':c').tasks.slow)
            }
        """

        when:
        server.expectConcurrent("b", "c")
        server.expectConcurrent("a")
        instantRun "slow", "--parallel"

        then:
        noExceptionThrown()

        when:
        def pendingCalls = server.expectConcurrentAndBlock("b", "c")
        server.expectConcurrent("a")

        def buildHandle = executer.withTasks("slow", "--parallel", "--max-workers=3", INSTANT_EXECUTION_PROPERTY).start()
        pendingCalls.waitForAllPendingCalls()
        pendingCalls.releaseAll()
        buildHandle.waitForFinish()

        then:
        noExceptionThrown()
    }

    def "instant execution for multi-level subproject"() {
        given:
        settingsFile << """
            include 'a:b', 'a:c'
        """
        instantRun ":a:b:help", ":a:c:help"
        def firstRunOutput = result.groupedOutput

        when:
        instantRun ":a:b:help", ":a:c:help"

        then:
        result.groupedOutput.task(":a:b:help").output == firstRunOutput.task(":a:b:help").output
        result.groupedOutput.task(":a:c:help").output == firstRunOutput.task(":a:c:help").output
    }

    def "restores fields whose value is a simple bean"() {
        buildFile << """
            class SomeBean {
                String value 
                SomeBean parent
                
                SomeBean() {
                    println("creating bean")
                    value = "default"
                }
            }

            class SomeTask extends DefaultTask {
                private final SomeBean bean = new SomeBean()
                
                SomeTask() {
                    bean.parent = new SomeBean()
                    bean.value = "child"
                    bean.parent.value = "parent"
                }

                @TaskAction
                void run() {
                    println "bean.value = " + bean.value
                    println "bean.parent.value = " + bean.parent.value
                }
            }

            task ok(type: SomeTask)
        """

        when:
        instantRun "ok"

        then:
        result.output.count("creating bean") == 2

        when:
        instantRun "ok"

        then:
        result.output.count("creating bean") == 2 // still running the task constructor, which creates values that are then discarded
        outputContains("bean.value = child")
        outputContains("bean.parent.value = parent")
    }

    @Unroll
    def "warns when task instance references an object of type #type"() {
        buildFile << """
            class SomeBean {
                private ${type} badReference
            }
            
            class SomeTask extends DefaultTask {
                private final ${type} badReference
                private final bean = new SomeBean()
                
                SomeTask() {
                    badReference = ${reference}
                    bean.badReference = ${reference}
                }

                @TaskAction
                void run() {
                    println "reference = " + badReference
                    println "bean.reference = " + bean.badReference
                }
            }

            task broken(type: SomeTask)
        """

        when:
        instantRun "broken"

        then:
        outputContains("instant-execution > Cannot serialize object of type ${type} as these are not supported with instant execution.")
        outputContains("instant-execution > task ':broken' field 'SomeTask.badReference' cannot be serialized because there's no serializer for")
        outputContains("instant-execution > task ':broken' field 'SomeBean.badReference' cannot be serialized because there's no serializer for")

        where:
        type          | reference
        Project.name  | "project"
        Gradle.name   | "project.gradle"
        Settings.name | "project.gradle.settings"
        Task.name     | "this"
    }

    @Ignore
    def "android"() {

        given:
        new TestFile("/Users/paul/src/local/gradle/instant-application").copyTo(testDirectory)

        expect:
        instantRun 'mainApkListPersistenceDebug', 'compileDebugAidl'
        instantRun 'mainApkListPersistenceDebug', 'compileDebugAidl'
    }
}
