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
import org.gradle.test.fixtures.archive.ZipTestFixture
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.Ignore

class InstantExecutionIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        executer.noDeprecationChecks()
    }

    def "classic"() {
        expect:
        run "help"
    }

    def "instant execution for help on empty project"() {
        given:
        instantRun "help"
        def firstRunOutput = result.normalizedOutput

        when:
        instantRun "help"

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
        instantRun "compileJava"
        outputContains("running build script")
        result.assertTasksExecuted(":compileJava")
        def classFile = file("build/classes/java/main/Thing.class")
        classFile.isFile()

        when:
        classFile.delete()
        instantRun "compileJava"

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
        instantRun "assemble"
        outputContains("running build script")
        result.assertTasksExecuted(":compileJava", ":processResources", ":classes", ":jar", ":assemble")
        def classFile = file("build/classes/java/main/Thing.class")
        classFile.isFile()

        when:
        classFile.delete()
        instantRun "assemble"

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
        def firstRunOutput = result.normalizedOutput

        when:
        instantRun ":a:b:help", ":a:c:help"

        then:
        result.normalizedOutput == firstRunOutput
    }

    @Ignore
    def "instant execution for compileGroovy on Groovy project with no dependencies"() {
        given:
        buildFile << """
            plugins { id 'groovy' }
            
            println "running build script"
        """
        file("src/main/java/Thing.groovy") << """
            class Thing {
            }
        """

        expect:
        instantRun "compileGroovy"
        outputContains("running build script")
        result.assertTasksExecuted(":compileGroovy")
        def classFile = file("build/classes/java/main/Thing.class")
        classFile.isFile()

        when:
        classFile.delete()
        instantRun "compileGroovy"

        then:
        outputDoesNotContain("running build script")
        result.assertTasksExecuted(":compileGroovy")
        classFile.isFile()
    }

    @Ignore
    def "android"() {

        given:
        new TestFile("/Users/paul/src/local/gradle/instant-application").copyTo(testDirectory)

        expect:
        instantRun 'mainApkListPersistenceDebug', 'compileDebugAidl'
        instantRun 'mainApkListPersistenceDebug', 'compileDebugAidl'
    }

    def "multi-project java build"() {
        given:
        settingsFile << """
            include("a", "b")
        """
        buildFile << """
            allprojects { apply plugin: 'java' }
            project(":b") {
                dependencies {
                    implementation(project(":a"))
                }
            }
        """
        file("a/src/main/java/a/A.java") << """
            package a;
            public class A {}
        """
        file("b/src/main/java/b/B.java") << """
            package b;
            public class B extends a.A {}
        """

        when:
        instantRun ":b:assemble", "-s"

        and:
        file("b/build/classes/java/main/b/B.class").delete()
        instantRun ":b:assemble", "-s"

        then:
        new ZipTestFixture(file("b/build/libs/b.jar")).assertContainsFile("b/B.class")
    }

    private void instantRun(String... args) {
        run(INSTANT_EXECUTION_PROPERTY, *args)
    }

    private static final String INSTANT_EXECUTION_PROPERTY = "-Dorg.gradle.unsafe.instant-execution"

}
