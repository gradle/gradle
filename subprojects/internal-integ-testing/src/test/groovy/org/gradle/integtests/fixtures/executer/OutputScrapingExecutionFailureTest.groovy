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

class OutputScrapingExecutionFailureTest extends AbstractExecutionResultTest {
    def "can have empty output"() {
        def result = OutputScrapingExecutionFailure.from("", "")

        expect:
        result.output.empty
        result.normalizedOutput.empty
        result.error.empty
    }

    def "can assert that failure location is present"() {
        given:
        def output = """
FAILURE: broken

* Where: build file 'build.gradle' line: 123

* What went wrong: something bad
"""
        when:
        def failure = OutputScrapingExecutionFailure.from(output, "")

        then:
        failure.assertHasFileName("build file 'build.gradle'")
        failure.assertHasLineNumber(123)

        when:
        failure.assertHasFileName("none")

        then:
        def e = thrown(AssertionError)
        e.message.trim().startsWith('Expected: "none"')

        when:
        failure.assertHasLineNumber(23)

        then:
        def e2 = thrown(AssertionError)
        e2.message.trim().startsWith('Expected: "23"')
    }

    def "cannot assert that failure location is present when missing"() {
        given:
        def output = """
FAILURE: broken

* What went wrong: something bad
"""
        def failure = OutputScrapingExecutionFailure.from(output, "")

        when:
        failure.assertHasFileName("build.gradle")

        then:
        def e = thrown(AssertionError)
        e.message.trim().startsWith('Expected: "build.gradle"')

        when:
        failure.assertHasLineNumber(23)

        then:
        def e2 = thrown(AssertionError)
        e2.message.trim().startsWith('Expected: "23"')
    }

    def "cannot make assertions about failures when failure section is missing"() {
        given:
        def output = """
some message.

broken!
"""
        def failure = OutputScrapingExecutionFailure.from(output, "")

        when:
        failure.assertHasDescription("broken!")

        then:
        def e = thrown(AssertionError)
        e.message.trim().startsWith('Expected: a string starting with "broken!"')

        when:
        failure.assertHasFileName("build.gradle")

        then:
        def e2 = thrown(AssertionError)
        e2.message.trim().startsWith('Expected: "build.gradle"')

        when:
        failure.assertHasLineNumber(23)

        then:
        def e3 = thrown(AssertionError)
        e3.message.trim().startsWith('Expected: "23"')
    }

    def "log output present assertions ignore content after failure section"() {
        given:
        def output = """
Some message
Some error

FAILURE: broken

* Exception is:
Some.Failure
"""
        when:
        def failure = OutputScrapingExecutionFailure.from(output, "")

        then:
        failure.assertOutputContains("Some message")
        failure.assertHasErrorOutput("Some error")

        when:
        failure.assertOutputContains("broken")

        then:
        def e = thrown(AssertionError)
        error(e).startsWith(error('''
            Did not find expected text in build output.
            Expected: broken
             
            Build output:
            =======
             
            Some message
            Some error
             
            Output:
        '''))

        when:
        failure.assertHasErrorOutput("broken")

        then:
        def e2 = thrown(AssertionError)
        error(e2).startsWith(error('''
            Did not find expected text in build output.
            Expected: broken
             
            Build output:
            =======
             
            Some message
            Some error
             
            Output:
        '''))
    }

    def "log output missing assertions do not ignore content after failure section"() {
        given:
        def output = """
Some message
Some error

FAILURE: broken

* Exception is:
Some.Failure
"""
        when:
        def failure = OutputScrapingExecutionFailure.from(output, "")

        then:
        failure.assertNotOutput("missing")

        when:
        failure.assertNotOutput("broken")

        then:
        def e = thrown(AssertionError)
        error(e).startsWith(error('''
            Found unexpected text in build output.
            Expected not present: broken
             
            Output:
        '''))
    }

    def "recreates exception stack trace"() {
        given:
        def output = """
Some text before

FAILURE: broken

* Exception is:
org.gradle.internal.service.ServiceCreationException: Could not create service of type CacheLockingManager
    at org.gradle.internal.service.DefaultServiceRegistry.some(DefaultServiceRegistry.java:604)
Caused by: java.io.IOException: Something in the middle
    at org.gradle.api.internal.artifacts.ivyservice.DefaultCacheLockingManager.initMetaDataStoreDir(DefaultCacheLockingManager.java:59)
Caused by: org.gradle.api.UncheckedIOException: Unable to create directory 'metadata-2.1'
    at org.gradle.api.internal.artifacts.ivyservice.DefaultCacheLockingManager.initMetaDataStoreDir(DefaultCacheLockingManager.java:59)
"""
        when:
        def failure = OutputScrapingExecutionFailure.from(output, "")

        then:
        failure.exception.class.simpleName == 'ServiceCreationException'
        failure.exception.message == 'Could not create service of type CacheLockingManager'
        failure.exception.cause.class.simpleName == 'IOException'
        failure.exception.cause.message == 'Something in the middle'
        failure.exception.cause.cause.class.simpleName == 'UncheckedIOException'
        failure.exception.cause.cause.message == "Unable to create directory 'metadata-2.1'"
    }

    def "ignores ansi chars, debug prefix, build status bar and work in progress"() {
        when:
        def failure = OutputScrapingExecutionFailure.from(output, "")

        then:
        failure.assertHasFileName("Build file 'build.gradle'")
        failure.assertHasLineNumber(4)

        and:
        failure.assertHasDescription("Execution failed for task ':broken'")
        failure.assertHasCause("broken")

        and:
        failure.assertOutputContains("Some sort of output")
        failure.assertOutputContains "Some more output"

        where:
        output << [ rawOutput, debugOutput ]
    }

    def static getRawOutput() {
        return """
\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> Evaluating settings\u001B[m\u001B[21D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> Evaluating settings\u001B[m\u001B[21D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> Evaluating settings\u001B[m\u001B[21D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> Evaluating settings\u001B[m\u001B[21D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> Evaluating settings\u001B[m\u001B[21D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> Evaluating settings\u001B[m\u001B[21D\u001B[1B\u001BSome sort of output\u001B[0K
Some sort of FAILURE: without status bar or work in progress
Some more output
\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% EXECUTING [2s]\u001B[m\u001B[33D\u001B[1B> IDLE\u001B[6D\u001B[1B\u001B[2AFAILURE: \u001B[39m\u001B[31mBuild failed with an exception. \u001B[39m\u001B[0K

* Where:
Build file 'build.gradle' line: 4

* What went wrong:
Execution failed for task ':broken'.
 \u001B[33m> \u001B[39mbroken

* Try:
Run with  \u001B[1m--info\u001B[m or  \u001B[1m--debug\u001B[m option to get more log output. Run with  \u001B[1m--scan\u001B[m to get full insights.

* Exception is:
org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':broken'.
        at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.executeActions(ExecuteActionsTaskExecuter.java:103)
        at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.execute(ExecuteActionsTaskExecuter.java:73)
Caused by: java.lang.RuntimeException: broken
        at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.executeActions(ExecuteActionsTaskExecuter.java:95)
        ... 29 more
"""
    }

    def static getDebugOutput() {
        return """
09:33:06.962 [DEBUG] [org.gradle.initialization.DefaultGradlePropertiesLoader] Found system project properties: []


\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> Evaluating settings\u001B[m\u001B[21D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> Evaluating settings\u001B[m\u001B[21D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> Evaluating settings\u001B[m\u001B[21D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> Evaluating settings\u001B[m\u001B[21D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> Evaluating settings\u001B[m\u001B[21D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> Evaluating settings\u001B[m\u001B[21D\u001B[1B\u001B[2A09:33:07.547 [DEBUG] [org.gradle.initialization.ScriptEvaluatingSettingsProcessor] Some sort of output\u001B[0K
09:33:08.990 [DEBUG] [org.gradle.api.internal.tasks.execution.ExecuteAtMostOnceTaskExecuter] Some sort of FAILURE: without status bar or work in progress
09:33:08.990 [DEBUG] [org.gradle.api.internal.tasks.execution.ExecuteAtMostOnceTaskExecuter] Some more output
09:33:08.990 [DEBUG] [org.gradle.execution.taskgraph.DefaultTaskPlanExecutor] Task worker [Thread[main,5,main]] finished, busy: 0.0 secs, idle: 0.021 secs
\u001B[0K
\u001B[0K
\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% EXECUTING [2s]\u001B[m\u001B[33D\u001B[1B> IDLE\u001B[6D\u001B[1B\u001B[2A09:33:09.031 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter] \u001B[31mFAILURE: \u001B[39m\u001B[31mBuild failed with an exception.\u001B[39m
09:33:09.031 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter] 
09:33:09.031 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter] * Where:
09:33:09.031 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter] Build file 'build.gradle' line: 4
09:33:09.031 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter] 
09:33:09.031 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter] * What went wrong:
09:33:09.031 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter] Execution failed for task ':broken'.
09:33:09.031 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter] \u001B[33m> \u001B[39mbroken
09:33:09.031 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter] 
09:33:09.031 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter] * Try:
09:33:09.031 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter]  Run with \u001B[1m--scan\u001B[m to get full insights.
09:33:09.032 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter] 
09:33:09.032 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter] * Exception is:
09:33:09.033 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter] org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':broken'.
09:33:09.033 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter]   at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.executeActions(ExecuteActionsTaskExecuter.java:103)
09:33:09.033 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter]   at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.execute(ExecuteActionsTaskExecuter.java:73)
09:33:09.033 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter] Caused by: java.lang.RuntimeException: broken
33:09.033 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter]   at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.executeActions(ExecuteActionsTaskExecuter.java:95)
09:33:09.033 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter]   ... 29 more
09:33:09.033 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter] 
09:33:09.033 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter] 
09:33:09.033 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter] * Get more help at \u001B[1mhttps://help.gradle.org\u001B[m
09:33:09.034 [ERROR] [org.gradle.internal.buildevents.BuildResultLogger] 
09:33:09.034 [ERROR] [org.gradle.internal.buildevents.BuildResultLogger] \u001B[31;1mBUILD FAILED\u001B[0;39m in 3s
09:33:09.034 [LIFECYCLE] [org.gradle.internal.buildevents.BuildResultLogger] 1 actionable task: 1 executed
"""
    }
}
