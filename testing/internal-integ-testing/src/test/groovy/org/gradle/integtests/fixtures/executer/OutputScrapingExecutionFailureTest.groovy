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
        e.message.trim().startsWith('Expected: a collection containing "none"')

        when:
        failure.assertHasLineNumber(23)

        then:
        def e2 = thrown(AssertionError)
        e2.message.trim().startsWith('Expected: a collection containing "23"')
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
        e.message.trim().startsWith('Expected: a collection containing "build.gradle"')

        when:
        failure.assertHasLineNumber(23)

        then:
        def e2 = thrown(AssertionError)
        e2.message.trim().startsWith('Expected: a collection containing "23"')
    }

    def "cannot make assertions about failures when failure section is missing"() {
        given:
        def output = """
some message.

broken!
"""
        def failure = OutputScrapingExecutionFailure.from(output, "")

        when:
        failure.assertHasFailures(12)

        then:
        def e = thrown(AssertionError)
        e.message.trim().startsWith('Expected: <12>')

        when:
        failure.assertHasDescription("broken!")

        then:
        def e2 = thrown(AssertionError)
        e2.message.trim().startsWith('''No matching failure description found
Expected: A failure description which is a string starting with "broken!"
     but: failure descriptions were []''')

        when:
        failure.assertHasCause("broken!")

        then:
        def e3 = thrown(AssertionError)
        e3.message.trim().startsWith('''No matching cause found
Expected: A cause which is a string starting with "broken!"
     but: causes were []''')

        when:
        failure.assertHasFileName("build.gradle")

        then:
        def e4 = thrown(AssertionError)
        e4.message.trim().startsWith('Expected: a collection containing "build.gradle"')

        when:
        failure.assertHasLineNumber(23)

        then:
        def e5 = thrown(AssertionError)
        e5.message.trim().startsWith('Expected: a collection containing "23"')
    }

    def "can assert that given number of failures are present"() {
        given:
        def output = """
FAILURE: Build completed with 2 failures.

* Where: build file 'build.gradle' line: 123

* What went wrong:
something bad

* Try:
fixing

* What went wrong:
something else bad

* Try:
fixing
"""
        when:
        def failure = OutputScrapingExecutionFailure.from(output, "")

        then:
        failure.assertHasFailures(2)

        when:
        failure.assertHasFailures(1)

        then:
        def e = thrown(AssertionError)
        e.message.trim().startsWith('Expected: <1>')
    }

    def "can assert that failure with description is present"() {
        given:
        def output = """
FAILURE: broken

Failure 1:

* Where: build file 'build.gradle' line: 123

* What went wrong:
something bad
  > cause

* Try:
Switching it off and back on again

Failure 2:

* What went wrong:
something else bad
  > cause

* Try:
Reinstalling your operating system
"""
        when:
        def failure = OutputScrapingExecutionFailure.from(output, "")

        then:
        failure.assertHasDescription("something bad")
        failure.assertHasDescription("something else bad")

        when:
        failure.assertHasDescription("other")

        then:
        def e = thrown(AssertionError)
        e.message.trim().startsWith('''No matching failure description found
Expected: A failure description which is a string starting with "other"
     but: failure descriptions were [something bad, something else bad]''')

        when:
        failure.assertHasDescription("cause")

        then:
        def e2 = thrown(AssertionError)
        e2.message.trim().startsWith('No matching failure description found')
    }

    def "can assert that failure with cause is present"() {
        given:
        def output = """
FAILURE: broken

Failure 1:

* Where: build file 'build.gradle' line: 123

* What went wrong:
something bad
> cause 1

* Try:
something

Failure 2:

* What went wrong:
something else bad
> cause 2

* Try:
something

"""
        when:
        def failure = OutputScrapingExecutionFailure.from(output, "")

        then:
        failure.assertHasCause("cause 1")
        failure.assertHasCause("cause 2")

        when:
        failure.assertHasCause("other")

        then:
        def e = thrown(AssertionError)
        e.message.trim().startsWith('''No matching cause found
Expected: A cause which is a string starting with "other"
     but: causes were [cause 1, cause 2]''')

        when:
        failure.assertHasCause("something")

        then:
        def e2 = thrown(AssertionError)
        e2.message.trim().startsWith('No matching cause found')
    }

    def "log output present assertions ignore content after failure section"() {
        given:
        def output = """
Some message

FAILURE: broken

* Exception is:
Some.Failure
"""
        def errorOutput = """
Some error
"""

        when:
        def failure = OutputScrapingExecutionFailure.from(output, errorOutput)

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
            '''))

        when:
        failure.assertHasErrorOutput("broken")

        then:
        def e2 = thrown(AssertionError)
        error(e2).startsWith(error('''
            Did not find expected text in error output.
            Expected: broken

            Error output:
            =======

            Some error
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

        and:
        !failure.mainContent.withNormalizedEol().contains("INITIALIZING")
        !failure.mainContent.withNormalizedEol().contains("IDLE")

        and:
        !failure.mainContent.withNormalizedEol().contains("DEBUG")

        where:
        output << [rawOutput, debugOutput]
    }

    def static getRawOutput() {
        return """
\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> Evaluating settings\u001B[m\u001B[21D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> Evaluating settings\u001B[m\u001B[21D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> Evaluating settings\u001B[m\u001B[21D\u001B[1B\u001B[2ASome sort of output\u001B[0K
Some sort of FAILURE: without status bar or work in progress\u001B[0K
Some more output
\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% EXECUTING [2s]\u001B[m\u001B[33D\u001B[1B> IDLE\u001B[6D\u001B[1B\u001B[2AFAILURE: \u001B[39m\u001B[31mBuild failed with an exception. \u001B[39m\u001B[0K
\u001B[0K
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
2019-10-03T09:33:06.962+0200 [DEBUG] [org.gradle.initialization.DefaultGradlePropertiesLoader] Found system project properties: []


\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> Evaluating settings\u001B[m\u001B[21D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> Evaluating settings\u001B[m\u001B[21D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> Evaluating settings\u001B[m\u001B[21D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> Evaluating settings\u001B[m\u001B[21D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> Evaluating settings\u001B[m\u001B[21D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> Evaluating settings\u001B[m\u001B[21D\u001B[1B\u001B[2A2019-10-03T09:33:07.547+0200 [DEBUG] [org.gradle.initialization.ScriptEvaluatingSettingsProcessor] Some sort of output\u001B[0K
2019-10-03T09:33:08.990+0200 [DEBUG] [org.gradle.api.internal.tasks.execution.ExecuteAtMostOnceTaskExecuter] Some sort of FAILURE: without status bar or work in progress
2019-10-03T09:33:08.990+0200 [DEBUG] [org.gradle.api.internal.tasks.execution.ExecuteAtMostOnceTaskExecuter] Some more output
2019-10-03T09:33:08.990+0200 [DEBUG] [org.gradle.execution.plan.DefaultPlanExecutor] Task worker [Thread[main,5,main]] finished, busy: 0.0 secs, idle: 0.021 secs
\u001B[0K
\u001B[0K
\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% EXECUTING [2s]\u001B[m\u001B[33D\u001B[1B> IDLE\u001B[6D\u001B[1B\u001B[2A2019-10-03T09:33:09.031+0200 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter] \u001B[31mFAILURE: \u001B[39m\u001B[31mBuild failed with an exception.\u001B[39m
2019-10-03T09:33:09.031+0200 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter]
2019-10-03T09:33:09.031+0200 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter] * Where:
2019-10-03T09:33:09.031+0200 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter] Build file 'build.gradle' line: 4
2019-10-03T09:33:09.031+0200 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter]
2019-10-03T09:33:09.031+0200 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter] * What went wrong:
2019-10-03T09:33:09.031+0200 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter] Execution failed for task ':broken'.
2019-10-03T09:33:09.031+0200 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter] \u001B[33m> \u001B[39mbroken
2019-10-03T09:33:09.031+0200 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter]
2019-10-03T09:33:09.031+0200 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter] * Try:
2019-10-03T09:33:09.031+0200 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter]  Run with \u001B[1m--scan\u001B[m to get full insights.
2019-10-03T09:33:09.032+0200 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter]
2019-10-03T09:33:09.032+0200 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter] * Exception is:
2019-10-03T09:33:09.033+0200 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter] org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':broken'.
2019-10-03T09:33:09.033+0200 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter]   at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.executeActions(ExecuteActionsTaskExecuter.java:103)
2019-10-03T09:33:09.033+0200 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter]   at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.execute(ExecuteActionsTaskExecuter.java:73)
2019-10-03T09:33:09.033+0200 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter] Caused by: java.lang.RuntimeException: broken
2019-10-03T09:33:09.033+0200 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter]   at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.executeActions(ExecuteActionsTaskExecuter.java:95)
2019-10-03T09:33:09.033+0200 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter]   ... 29 more
2019-10-03T09:33:09.033+0200 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter]
2019-10-03T09:33:09.033+0200 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter]
2019-10-03T09:33:09.033+0200 [ERROR] [org.gradle.internal.buildevents.BuildExceptionReporter] * Get more help at \u001B[1mhttps://help.gradle.org\u001B[m
2019-10-03T09:33:09.034+0200 [ERROR] [org.gradle.internal.buildevents.BuildResultLogger]
2019-10-03T09:33:09.034+0200 [ERROR] [org.gradle.internal.buildevents.BuildResultLogger] \u001B[31;1mBUILD FAILED\u001B[0;39m in 3s
2019-10-03T09:33:09.034+0200 [LIFECYCLE] [org.gradle.internal.buildevents.BuildResultLogger] 1 actionable task: 1 executed
"""
    }
}
