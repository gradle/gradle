/*
 * Copyright 2022 the original author or authors.
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
package org.gradle.integtests.tooling.r76

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.TestLauncher

import java.util.regex.Pattern

@TargetGradleVersion(">=7.6")
@ToolingApiVersion(">=7.6")
class TestLauncherTaskExecutionCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        file('build.gradle') << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            dependencies {
                testImplementation 'junit:junit:4.13'
            }
        """
        file('src/test/java/MyTest.java') << '''
            public class MyTest {

                @org.junit.Test
                public void pass() {
                }

                // if the test task is executed without a filter then the build fails
                @org.junit.Test
                public void fail() {
                    throw new RuntimeException();
                }
            }
        '''
    }

    @TargetGradleVersion(">=6.1 <7.6") // TestLauncher.withTaskAndTestMethods() was introduced in Gradle 6.1
    def "old Gradle version ignores task execution request"() {
        when:
        launchTestWithTestFilter { tl ->
            tl.forTasks(':help')
        }

        then:
        !taskExecuted(':help')
    }

    def "can execute task"() {
        when:
        launchTestWithTestFilter { tl ->
            tl.forTasks(':help')
        }

        then:
        taskExecuted(':help')
    }

    def "default task is executed when forTasks() receives empty task list"() {
        setup:
        buildFile << '''
            tasks.register('myDefaultTask')
            defaultTasks 'myDefaultTask'
        '''

        when:
        launchTestWithTestFilter { tl ->
            tl.forTasks(':help')
        }

        then:
        !taskExecuted(':myDefaultTask')

        when:
        launchTestWithTestFilter { tl ->
            tl.forTasks()
        }

        then:
        taskExecuted(':myDefaultTask')
    }

    def "can use task selector"() {
        setup:
        settingsFile << '''
            rootProject.name = 'root'
            include 'a'
        '''
        buildFile << '''
            allprojects {
                tasks.register('foo')
            }
        '''
        file('a').mkdirs()

        when:
        launchTestWithTestFilter { tl ->
            tl.forTasks('foo', 'test')
        }

        then:
        taskExecuted(':foo')
        taskExecuted(':a:foo')
    }

    def "can exclude task"() {
        setup:
        buildFile << '''
            def foo = tasks.register('foo')
            tasks.register('bar') {
                dependsOn foo
            }
        '''

        when:
        launchTestWithTestFilter { tl ->
            tl.forTasks('bar', 'test').addArguments('-x', 'foo')
        }

        then:
        taskExecuted(':bar')
        !taskExecuted(':foo')
    }

    def "if selected test overlaps with launched task then the test filter still applies"() {
        when:
        launchTestWithTestFilter { tl ->
            tl.forTasks(':help', ':test')
        }

        then:
        taskExecuted(':help')
    }

    def "can execute task from subproject"() {
        setup:
        settingsFile << '''
            rootProject.name = 'root'
            include 'sub1'
            include 'sub1:sub2'
        '''
        buildFile << '''
            allprojects {
                tasks.register('foo')
            }
        '''
        file('sub1/sub2').mkdirs()

        when:
        launchTestWithTestFilter(toolingApi.connector().forProjectDirectory(projectDir.file('sub1'))) {
            it.forTasks('foo')
        }

        then:
        !taskExecuted(':foo')
        taskExecuted(':sub1:foo')
        taskExecuted(':sub1:sub2:foo')
    }

    def "can execute task from included build"() {
        setup:
        settingsFile << '''
            rootProject.name = 'root'
            includeBuild 'included'
        '''
        file('included/settings.gradle') << '''
            rootProject.name = 'included'
        '''
        file('included/build.gradle') << '''
            tasks.register('foo')
        '''

        when:
        launchTestWithTestFilter { tl ->
            tl.forTasks(':included:foo')
        }

        then:
        taskExecuted(':included:foo')
    }

    def "can control the order of tasks and tests"() {
        setup:
        buildFile << '''
            tasks.register('setupTest')
            tasks.register('cleanupTest')
        '''

        when:
        withConnection { ProjectConnection connection ->
            TestLauncher testLauncher = connection.newTestLauncher()
            collectOutputs(testLauncher)

            testLauncher.forTasks("setupTest")
                        .withTestsFor(s -> s.forTaskPath(":test")
                        .includeMethod('MyTest', 'pass'))
                        .forTasks("cleanupTest")
                        .run()
        }

        then:
        tasksExecutedInOrder(':setupTest', ':test', ':cleanupTest')

        when:
        withConnection { connection ->
            TestLauncher testLauncher = connection.newTestLauncher()
            collectOutputs(testLauncher)
            testLauncher.withTestsFor(s -> s.forTaskPath(":test").includeMethod('MyTest', 'pass')).forTasks('setupTest')
            testLauncher.run()
        }

        then:
        tasksExecutedInOrder(':test', ':setupTest')
    }

    private def launchTestWithTestFilter(connector, @DelegatesTo(TestLauncher) @ClosureParams(value = SimpleType, options = ['org.gradle.tooling.TestLauncher']) Closure testLauncherSpec) {
        withConnection(connector, connectionConfiguration(testLauncherSpec))
    }

    private def launchTestWithTestFilter(Closure testLauncherSpec) {
        withConnection(connectionConfiguration(testLauncherSpec))
    }

    private def connectionConfiguration(Closure testLauncherSpec) {
        {   ProjectConnection connection ->
            TestLauncher testLauncher = connection.newTestLauncher()
            testLauncher.withTaskAndTestMethods(':test', 'MyTest', ['pass'])
            collectOutputs(testLauncher)
            testLauncherSpec(testLauncher)
            testLauncher.run()
        }
    }

    def taskExecuted(String path) {
        stdout.toString().contains("Task ${path}")
    }

    def tasksExecutedInOrder(String... tasks) {
        stdout.toString().matches(Pattern.compile(".*Task ${tasks.join('.* Task ')}.*", Pattern.DOTALL | Pattern.MULTILINE))
    }
}
