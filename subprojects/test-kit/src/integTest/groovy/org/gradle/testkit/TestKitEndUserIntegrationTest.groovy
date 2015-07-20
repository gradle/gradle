/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testkit

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GFileUtils

class TestKitEndUserIntegrationTest extends AbstractIntegrationSpec {
    public static final String[] GROOVY_SRC_PATH = ['src', 'test', 'groovy'] as String[]
    public static final String[] TEST_CLASS_PACKAGE = ['org', 'gradle', 'test'] as String[]
    public static final String[] TEST_CLASS_PATH = GROOVY_SRC_PATH + TEST_CLASS_PACKAGE
    File functionalTestClassDir

    def setup() {
        executer.requireGradleHome()
        functionalTestClassDir = testDirectoryProvider.createDir(TEST_CLASS_PATH)
        buildFile << buildFileForGroovyProject()
    }

    def "use of GradleRunner API in test class without declaring test-kit dependency causes compilation error"() {
        given:
        GFileUtils.writeFile("""package org.gradle.test

import org.gradle.testkit.runner.GradleRunner

class BuildLogicFunctionalTest {
    def "create GradleRunner"() {
        when:
        GradleRunner.create()

        then:
        noExceptionThrown()
    }
}
""", new File(functionalTestClassDir, 'BuildLogicFunctionalTest.groovy'))

        when:
        ExecutionFailure failure = fails('build')

        then:
        result.executedTasks.contains(':compileTestGroovy')
        !result.skippedTasks.contains(':compileTestGroovy')
        failure.error.contains("unable to resolve class $GradleRunner.name")
        failure.error.contains('Compilation failed; see the compiler error output for details.')
    }

    def "successfully execute functional test and verify expected result"() {
        buildFile << gradleTestKitDependency()
        GFileUtils.writeFile("""package org.gradle.test

import org.gradle.testkit.runner.GradleRunner
import static org.gradle.testkit.runner.TaskResult.*
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class BuildLogicFunctionalTest {
    @Rule final TemporaryFolder testProjectDir = TemporaryFolder()
    File buildFile

    def setup() {
        buildFile = testProjectDir.newFile('build.gradle')
    }

    def "execute helloWorld task"() {
        given:
        buildFile << '''
            task helloWorld {
                doLast {
                    println 'Hello world!'
                }
            }
        '''

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('helloWorld')
            .build()

        then:
        result.standardOutput.contains('Hello world!')
        result.standardError == ''
        result.taskPaths(SUCCESS) == [':helloWorld']
        result.tasks(SKIPPED).empty
        result.tasks(UP_TO_DATE).empty
        result.tasks(FAILED).empty
    }
}
""", new File(functionalTestClassDir, 'BuildLogicFunctionalTest.groovy'))

        when:
        ExecutionResult result = succeeds('build')

        then:
        result.executedTasks.containsAll([':test', ':build'])
        !result.skippedTasks.contains(':test')
    }

    def "functional test fails due to invalid JVM parameter for test execution"() {
        buildFile << gradleTestKitDependency()
        GFileUtils.writeFile('org.gradle.jvmargs=-unknown', testDirectory.file('gradle.properties'))
        GFileUtils.writeFile("""package org.gradle.test

import org.gradle.testkit.runner.GradleRunner
import static org.gradle.testkit.runner.TaskResult.*
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class BuildLogicFunctionalTest {
    @Rule final TemporaryFolder testProjectDir = TemporaryFolder()
    File buildFile

    def setup() {
        buildFile = testProjectDir.newFile('build.gradle')
    }

    def "execute helloWorld task"() {
        given:
        buildFile << '''
            task helloWorld {
                doLast {
                    println 'Hello world!'
                }
            }
        '''

        when:
        GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('helloWorld')
            .build()

        then:
        notExceptionThrown()
    }
}
""", new File(functionalTestClassDir, 'BuildLogicFunctionalTest.groovy'))

        when:
        ExecutionFailure failure = fails('build')

        then:
        !result.executedTasks.contains(':test')
        !result.skippedTasks.contains(':test')
        failure.error.contains('Unrecognized option: -unknown')
    }

    private String buildFileForGroovyProject() {
        """
            apply plugin: 'groovy'

            dependencies {
                testCompile localGroovy()
                testCompile 'org.spockframework:spock-core:1.0-groovy-2.3'
            }

            repositories {
                mavenCentral()
            }
        """
    }

    private String gradleTestKitDependency() {
        """
            dependencies {
                testCompile gradleTestKit()
            }
        """
    }
}
