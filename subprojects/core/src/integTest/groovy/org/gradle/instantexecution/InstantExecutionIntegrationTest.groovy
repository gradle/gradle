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
import spock.lang.Ignore

class InstantExecutionIntegrationTest extends AbstractIntegrationSpec {

    def "classic"() {
        expect:
        run "help"
    }

    def "instant execution for help on empty project"() {
        given:
        run "help", "-DinstantExecution=true"
        def firstRunOutput = result.normalizedOutput

        when:
        run "help", "-DinstantExecution=true"

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
        run "compileJava", "-DinstantExecution=true"
        outputContains("running build script")
        result.assertTasksExecuted(":compileJava")
        def classFile = file("build/classes/java/main/Thing.class")
        classFile.isFile()

        when:
        classFile.delete()
        run "compileJava", "-DinstantExecution=true"

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
        run "assemble", "-DinstantExecution=true"
        outputContains("running build script")
        result.assertTasksExecuted(":compileJava", ":processResources", ":classes", ":jar", ":assemble")
        def classFile = file("build/classes/java/main/Thing.class")
        classFile.isFile()

        when:
        classFile.delete()
        run "assemble", "-DinstantExecution=true"

        then:
        outputDoesNotContain("running build script")
        result.assertTasksExecuted(":compileJava", ":processResources", ":classes", ":jar", ":assemble")
        classFile.isFile()
    }

    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    @Ignore
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

            allprojects {
                tasks.create('slow', SlowTask)
            }
            project(':a') {
                tasks.slow.dependsOn(project(':b').tasks.slow, project(':c').tasks.slow)
            }
        """

        when:
        server.expectConcurrent("b", "c")
        server.expectConcurrent("a")
        run "slow", "-DinstantExecution"

        then:
        noExceptionThrown()

        when:
        server.expectConcurrent("b", "c")
        server.expectConcurrent("a")
        run "slow", "-DinstantExecution"

        then:
        noExceptionThrown()
    }
}
