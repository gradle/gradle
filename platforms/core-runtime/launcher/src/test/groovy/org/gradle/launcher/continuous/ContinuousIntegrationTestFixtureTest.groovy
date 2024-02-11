/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.launcher.continuous

import org.gradle.integtests.fixtures.AbstractContinuousIntegrationTest
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.integtests.fixtures.executer.UnexpectedBuildFailure
import spock.lang.Specification


class ContinuousIntegrationTestFixtureTest extends Specification {
    def gradleExecuter = Stub(GradleExecuter)

    def "successful build should be parsed"() {
        given:
        def sampleTest = testCase()
        def gradleHandle = setupStubs(sampleTest)
        gradleHandle.getStandardOutput() >> '''
:sometask

BUILD SUCCESSFUL in 1s

Waiting for changes to input files... (ctrl-d then enter to exit)
'''
        when:
        sampleTest.succeeds("sometask")
        then:
        noExceptionThrown()
    }

    def "failure output should be parsed"() {
        given:
        def sampleTest = testCase()
        def gradleHandle = setupStubs(sampleTest)
        gradleHandle.getStandardOutput() >> '''
FAILURE: Build failed with an exception.

* What went wrong:
Task 'missingtask' not found in root project 'gradle'. Some candidates are: 'init'.

* Try:
Run gradlew tasks to get a list of available tasks. Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.

BUILD FAILED in 0s
'''
        when:
        sampleTest.succeeds("missingtask")
        then:
        thrown(UnexpectedBuildFailure)
    }

    def "output parsing with info logging after 'waiting for changes' line"() {
        given:
        def sampleTest = testCase()
        def gradleHandle = setupStubs(sampleTest)
        gradleHandle.getStandardOutput() >> '''
:sometask

BUILD SUCCESSFUL in 1s

Waiting for changes to input files... (ctrl-d then enter to exit)
[info] play - Listening for HTTP on /0:0:0:0:0:0:0:0:49788
'''
        when:
        sampleTest.succeeds("sometask")
        then:
        noExceptionThrown()
    }

    def "output parsing of multiple continuous builds"() {
        given:
        def sampleTest = testCase()
        def gradleHandle = setupStubs(sampleTest)
        gradleHandle.getStandardOutput() >> '''
:sometask

BUILD SUCCESSFUL in 1s

Waiting for changes to input files... (ctrl-d then enter to exit)
new file: /some/new/file.txt
Change detected, executing build...
:sometask

BUILD SUCCESSFUL in 1s

Waiting for changes to input files... (ctrl-d then enter to exit)
'''
        when:
        sampleTest.succeeds("sometask")
        then:
        def results = sampleTest.results
        results.size() == 2
        results[1].output.startsWith("new file:")
    }

    private AbstractContinuousIntegrationTest testCase() {
        ContinuousIntegrationTestFixture fixture = new ContinuousIntegrationTestFixture()
        fixture.executer = gradleExecuter
        return fixture
    }

    private static class ContinuousIntegrationTestFixture extends AbstractContinuousIntegrationTest {
        GradleExecuter executer

        @Override
        GradleExecuter createExecuter() {
            return executer
        }
    }

    private GradleHandle setupStubs(AbstractContinuousIntegrationTest sampleTest) {
        sampleTest.results = [] // fields are null for some reason, perhaps Spock internally modifies constructors
        def gradleHandle = Stub(GradleHandle)
        gradleExecuter.withStdinPipe() >> gradleExecuter
        gradleExecuter.withTasks(_) >> gradleExecuter
        gradleExecuter.withForceInteractive(_) >> gradleExecuter
        gradleExecuter.withArgument(_) >> gradleExecuter
        gradleExecuter.start() >> gradleHandle
        gradleHandle.getErrorOutput() >> ''
        gradleHandle.isRunning() >>> [true, false]
        gradleHandle
    }
}
