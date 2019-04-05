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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule

class InstantExecutionIntegrationTest extends AbstractIntegrationSpec {

    def "classic"() {
        expect:
        run "help"
    }

    def "instant execution for help on empty project"() {
        given:
        run "help", "-DinstantExecution"
        def firstRunOutput = result.normalizedOutput

        when:
        run "help", "-DinstantExecution"

        then:
        result.normalizedOutput == firstRunOutput
    }

    def "instant execution for compileJava on Java project with no dependencies"() {
        given:
        buildFile << """
            plugins { id 'java' }
            
            println "running build script"
        """
        file("src/main/java/Thing.java") << """
            class Thing {
            }
        """

        expect:
        executer.expectDeprecationWarning()
        run "compileJava", "-DinstantExecution"
        outputContains("running build script")
        result.assertTasksExecuted(":compileJava")
        def classFile = file("build/classes/java/main/Thing.class")
        classFile.isFile()

        when:
        classFile.delete()
        run "compileJava", "-DinstantExecution"

        then:
        outputDoesNotContain("running build script")
        result.assertTasksExecuted(":compileJava")
        classFile.isFile()
    }

    def "instant execution for assemble on Java project with multiple source directories"() {
        given:
        buildFile << """
            plugins { id 'java' }
            
            sourceSets.main.java.srcDir("src/common/java") 
            
            println "running build script"
        """
        file("src/common/java/OtherThing.java") << """
            class OtherThing {
            }
        """
        file("src/main/java/Thing.java") << """
            class Thing extends OtherThing {
            }
        """

        expect:
        executer.expectDeprecationWarning()
        run "assemble", "-DinstantExecution"
        outputContains("running build script")
        result.assertTasksExecuted(":compileJava", ":processResources", ":classes", ":jar", ":assemble")
        def classFile = file("build/classes/java/main/Thing.class")
        classFile.isFile()

        when:
        classFile.delete()
        run "assemble", "-DinstantExecution"

        then:
        outputDoesNotContain("running build script")
        result.assertTasksExecuted(":compileJava", ":processResources", ":classes", ":jar", ":assemble")
        classFile.isFile()
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
        run "slow", "-DinstantExecution", "--parallel"

        then:
        noExceptionThrown()

        when:
        def pendingCalls = server.expectConcurrentAndBlock("b", "c")
        server.expectConcurrent("a")

        def buildHandle = executer.withTasks("slow", "-DinstantExecution", "--parallel", "--max-workers=3").start()
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
        run ":a:b:help", ":a:c:help", "-DinstantExecution"
        def firstRunOutput = result.normalizedOutput

        when:
        run ":a:b:help", ":a:c:help", "-DinstantExecution"

        then:
        result.normalizedOutput == firstRunOutput
    }
}
