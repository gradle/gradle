/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.fixtures.executer

import org.gradle.api.Action
import org.gradle.process.ExecResult
import org.gradle.process.internal.ExecHandle
import spock.lang.Specification

import static org.gradle.util.internal.TextUtil.normaliseLineSeparators

class ForkingGradleHandleTest extends Specification {

    private final static int SUCCESS_EXIT_VALUE = 0
    private final static int FAILURE_EXIT_VALUE = 1
    private final static File EXEC_HANDLE_DIR = new File('/some/dir')
    private final static String EXEC_HANDLE_CMD = 'gradle'
    private final static List<String> EXEC_HANDLE_ARGS = ['build', '--parallel']

    def execHandle = Mock(ExecHandle)
    def execResult = Mock(ExecResult)
    def resultAssertion = Mock(Action)
    def forkingGradleHandle = new ForkingGradleHandle(null, false, resultAssertion, 'UTF-8', null, null)

    def setup() {
        forkingGradleHandle.execHandleRef.set(execHandle)
    }

    def "wait for finish for successful execution"() {
        when:
        def executionResult = forkingGradleHandle.waitForFinish()

        then:
        1 * execHandle.waitForFinish() >> execResult
        0 * execHandle._
        1 * execResult.rethrowFailure()
        1 * execResult.getExitValue() >> SUCCESS_EXIT_VALUE
        1 * resultAssertion.execute(_)
        executionResult instanceof OutputScrapingExecutionResult
    }

    def "wait for finish for failed execution"() {
        given:
        def executionResultMessage = "Failed for some reason with exit value $FAILURE_EXIT_VALUE"

        when:
        forkingGradleHandle.waitForFinish()

        then:
        1 * execHandle.waitForFinish() >> execResult
        1 * execResult.rethrowFailure()
        1 * execResult.getExitValue() >> FAILURE_EXIT_VALUE
        0 * resultAssertion.execute(_)
        1 * execHandle.getDirectory() >> EXEC_HANDLE_DIR
        1 * execHandle.getCommand() >> EXEC_HANDLE_CMD
        1 * execHandle.getArguments() >> EXEC_HANDLE_ARGS
        1 * execResult.toString() >> executionResultMessage
        def t = thrown(UnexpectedBuildFailure)
        normaliseLineSeparators(t.message) == createUnexpectedBuildFailureMessage('failed', executionResultMessage)
    }

    def "wait for failure for failed execution"() {
        when:
        def executionResult = forkingGradleHandle.waitForFailure()

        then:
        1 * execHandle.waitForFinish() >> execResult
        0 * execHandle._
        1 * execResult.rethrowFailure()
        1 * execResult.getExitValue() >> FAILURE_EXIT_VALUE
        1 * resultAssertion.execute(_)
        executionResult instanceof OutputScrapingExecutionFailure
    }

    def "wait for failure for successful execution"() {
        given:
        def executionResultMessage = "Successful execution with exit value $SUCCESS_EXIT_VALUE"

        when:
        forkingGradleHandle.waitForFailure()

        then:
        1 * execHandle.waitForFinish() >> execResult
        1 * execResult.rethrowFailure()
        1 * execResult.getExitValue() >> SUCCESS_EXIT_VALUE
        0 * resultAssertion.execute(_)
        1 * execHandle.getDirectory() >> EXEC_HANDLE_DIR
        1 * execHandle.getCommand() >> EXEC_HANDLE_CMD
        1 * execHandle.getArguments() >> EXEC_HANDLE_ARGS
        1 * execResult.toString() >> executionResultMessage
        def t = thrown(UnexpectedBuildFailure)
        normaliseLineSeparators(t.message) == createUnexpectedBuildFailureMessage('did not fail', executionResultMessage)
    }

    static String createUnexpectedBuildFailureMessage(String failureResult, String executionResultMessage) {
        """Gradle execution $failureResult in $EXEC_HANDLE_DIR with: $EXEC_HANDLE_CMD $EXEC_HANDLE_ARGS
Process ExecResult:
$executionResultMessage
-----
Output:

-----
Error:

-----
"""
    }
}
