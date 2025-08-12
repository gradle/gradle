/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.util.internal.TextUtil

import static org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult.STACK_TRACE_ELEMENT

class OutputScrapingExecutionResultTest extends AbstractExecutionResultTest {
    def "can have empty output"() {
        def result = OutputScrapingExecutionResult.from("", "")

        expect:
        result.output.empty
        result.normalizedOutput.empty
        result.error.empty
    }

    def "normalizes line ending in output"() {
        def output = "\n\r\nabc\r\n\n"
        def error = "\r\n\nerror\n\r\n"
        def result = OutputScrapingExecutionResult.from(output, error)

        expect:
        result.output == "\n\nabc\n\n"
        result.normalizedOutput == "\n\nabc\n\n"
        result.error == "\n\nerror\n\n"
    }

    def "retains trailing line ending in output"() {
        def output = "\n\nabc\n"
        def error = "\nerror\n"
        def result = OutputScrapingExecutionResult.from(output, error)

        expect:
        result.output == output
        result.normalizedOutput == output
        result.error == error
    }

    def "finds stack traces when present"() {
        def output = '''
* What went wrong:
A problem occurred evaluating root project '4j0h2'.
org.gradle.api.GradleScriptException: A problem occurred evaluating root project '4j0h2'.
	at org.gradle.groovy.scripts.internal.DefaultScriptRunnerFactory$ScriptRunnerImpl.run(DefaultScriptRunnerFactory.java:93)
	at org.gradle.configuration.DefaultScriptPluginFactory$ScriptPluginImpl.lambda$apply$0(DefaultScriptPluginFactory.java:133)
	at org.gradle.configuration.ProjectScriptTarget.addConfiguration(ProjectScriptTarget.java:79)
	at org.gradle.configuration.DefaultScriptPluginFactory$ScriptPluginImpl.apply(DefaultScriptPluginFactory.java:136)
	at org.gradle.configuration.BuildOperationScriptPlugin$1.run(BuildOperationScriptPlugin.java:65)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:29)
'''
        def matches = output.readLines().grep(line -> STACK_TRACE_ELEMENT.matcher(line).matches())
        expect:
        matches.size() == 6
        matches[0] == '\tat org.gradle.groovy.scripts.internal.DefaultScriptRunnerFactory$ScriptRunnerImpl.run(DefaultScriptRunnerFactory.java:93)'
    }

    def "does not find things that might look like stack traces"() {
        def output = """
* What went wrong:
A problem occurred evaluating root project '4j0h2'.
> Could not create an instance of type Thing.
   > Multiple constructors for parameters ['a', 'b']:
       1. candidate: Thing(String, String, ProjectLayout)
       2. best match: Thing(String, String, ObjectFactory)
"""
        def matches = output.readLines().grep(line -> STACK_TRACE_ELEMENT.matcher(line).matches())
        expect:
        matches.empty
    }

    def "can assert build output is present in main content"() {
        def output = """message
message 2

BUILD SUCCESSFUL in 12s

post build
"""
        when:
        def result = OutputScrapingExecutionResult.from(output, "")

        then:
        result.assertOutputContains("message")
        result.assertOutputContains("message\nmessage 2")
        result.assertOutputContains("message\nmessage 2\n")
        result.assertOutputContains("message\r\nmessage 2")

        when:
        result.assertOutputContains("missing")

        then:
        def e = thrown(AssertionError)
        error(e).startsWith(error('''
            Did not find expected text in build output.
            Expected: missing

            Build output:
            =======
            message
            message 2
            '''))

        when:
        result.assertOutputContains("post build")

        then:
        def e2 = thrown(AssertionError)
        error(e2).startsWith(error('''
            Did not find expected text in build output.
            Expected: post build

            Build output:
            =======
            message
            message 2
            '''))

        when:
        result.assertOutputContains("BUILD")

        then:
        def e3 = thrown(AssertionError)
        error(e3).startsWith(error('''
            Did not find expected text in build output.
            Expected: BUILD

            Build output:
            =======
            message
            message 2
            '''))

        when:
        result.assertOutputContains("message extra")

        then:
        def e4 = thrown(AssertionError)
        error(e4).startsWith(error('''
            Did not find expected text in build output.
            Expected: message extra

            Build output:
            =======
            message
            message 2
            '''))

        when:
        result.assertOutputContains("message 3")

        then:
        def e6 = thrown(AssertionError)
        error(e6).trim().startsWith(error('''
            Expected: "message 3"
             but: was "message 2"
            '''))

        when:
        result.assertOutputContains("message\n\n")

        then:
        def e7 = thrown(AssertionError)
        error(e7).startsWith(error('''
            Did not find expected text in build output.
            Expected: message



            Build output:
            =======
            message
            message 2
            '''))
    }

    def "can assert post build output is present"() {
        def output = """
message

BUILD SUCCESSFUL in 12s

post build
more post build
"""
        when:
        def result = OutputScrapingExecutionResult.from(output, "")

        then:
        result.assertHasPostBuildOutput("post build")
        result.assertHasPostBuildOutput("post build\nmore post build\n")
        result.assertHasPostBuildOutput("post build\r\nmore post build\r\n")
        result.assertHasPostBuildOutput("\npost build\n")
        result.assertHasPostBuildOutput("\r\npost build\r\nmore")

        when:
        result.assertHasPostBuildOutput("missing")

        then:
        def e = thrown(AssertionError)
        error(e).startsWith(error('''
            Did not find expected text in post-build output.
            Expected: missing

            Post-build output:
            =======

            post build
            more post build
            '''))

        when:
        result.assertHasPostBuildOutput("message")

        then:
        def e2 = thrown(AssertionError)
        error(e2).startsWith(error('''
            Did not find expected text in post-build output.
            Expected: message

            Post-build output:
            =======

            post build
            more post build
            '''))

        when:
        result.assertHasPostBuildOutput("BUILD")

        then:
        def e3 = thrown(AssertionError)
        error(e3).startsWith(error('''
            Did not find expected text in post-build output.
            Expected: BUILD

            Post-build output:
            =======

            post build
            more post build
            '''))

        when:
        result.assertHasPostBuildOutput("post build extra")

        then:
        def e4 = thrown(AssertionError)
        error(e4).startsWith(error('''
            Did not find expected text in post-build output.
            Expected: post build extra

            Post-build output:
            =======

            post build
            more post build
            '''))

        when:
        result.assertHasPostBuildOutput("post build\n\n")

        then:
        def e5 = thrown(AssertionError)
        error(e5).startsWith(error('''
            Did not find expected text in post-build output.
            Expected: post build



            Post-build output:
            =======

            post build
            more post build
            '''))
    }

    def "can assert output is not present = #text"() {
        def output = """
message

BUILD SUCCESSFUL in 12s

post build
more post build
"""
        def errorOut = """
broken
"""
        when:
        def result = OutputScrapingExecutionResult.from(output, errorOut)

        then:
        result.assertNotOutput("missing")
        result.assertNotOutput("missing extra")
        result.assertNotOutput("missing\n\nBUILD FAILED")
        result.assertNotOutput("missing\n\n\nBUILD")

        when:
        result.assertNotOutput(text)

        then:
        def e = thrown(AssertionError)
        error(e).startsWith(error("""
Found unexpected text in build output.
Expected not present: ${text}

Output:
"""))

        where:
        text               | _
        "message"          | _
        "message\n\nBUILD" | _
        "BUILD"            | _
        "post"             | _
        "broken"           | _
        "broken\n"         | _
    }

    def "can assert task is present when task output is split into several groups"() {
        def output = """
before

> Task :a
> Task :b

> Task :a
some content

> Task :b
other content

> Task :a
all good

> Task :b SKIPPED

after

BUILD SUCCESSFUL in 12s

post build
"""
        when:
        def result = OutputScrapingExecutionResult.from(output, "")

        then:
        result.assertTasksScheduled(":a", ":b")
        result.assertTasksScheduled([":a", ":b"])
        result.assertTasksScheduled(":a", ":b", ":a", [":a", ":b"], ":b")

        and:
        result.assertTasksScheduledInOrder(":a", ":b")
        result.assertTasksScheduledInOrder([":a", ":b"])

        and:
        result.assertTaskScheduled(':a')
        result.assertTaskScheduled(':b')
        result.assertTasksNotScheduled(':c')

        and:
        result.executedTasks == [":a", ":b"]
        result.skippedTasks == [":b"] as Set

        and:
        result.assertTasksExecuted(":a")
        result.assertTasksExecuted(":a", ":a", [":a"])

        and:
        result.assertTaskExecuted(":a")

        and:
        result.assertTasksSkipped(":b")
        result.assertTasksSkipped(":b", ":b", [":b"])

        and:
        result.assertTaskSkipped(":b")

        when:
        result.assertTasksScheduled(":a")

        then:
        def e = thrown(AssertionError)
        error(e).startsWith(error('''
            Build output does not contain the expected tasks.
            Expected: [:a]
            Actual: [:a, :b]
            '''))

        when:
        result.assertTasksScheduled(":a", ":b", ":c")

        then:
        def e2 = thrown(AssertionError)
        error(e2).startsWith(error('''
            Build output does not contain the expected tasks.
            Expected: [:a, :b, :c]
            Actual: [:a, :b]
            '''))

        when:
        result.assertTasksScheduledInOrder(":b", ":a")

        then:
        def e3 = thrown(AssertionError)
        e3.message.startsWith(":a does not occur in expected order (expected: exact([:b, :a]), actual [:a, :b])")

        when:
        result.assertTasksScheduledInOrder(":a")

        then:
        def e4 = thrown(AssertionError)
        error(e4).startsWith(error('''
            Build output does not contain the expected tasks.
            Expected: [:a]
            Actual: [:a, :b]
            '''))

        when:
        result.assertTasksScheduledInOrder(":a", ":b", ":c")

        then:
        def e5 = thrown(AssertionError)
        error(e5).startsWith(error('''
            Build output does not contain the expected tasks.
            Expected: [:a, :b, :c]
            Actual: [:a, :b]
            '''))

        when:
        result.assertTaskScheduled(':c')

        then:
        def e6 = thrown(AssertionError)
        error(e6).startsWith(error('''
            Build output does not contain the expected task.
            Expected: :c
            Actual: [:a, :b]
            Output:
            '''))

        when:
        result.assertTasksNotScheduled(':b')

        then:
        def e7 = thrown(AssertionError)
        error(e7).startsWith(error('''
            Build output does contains unexpected task.
            Expected: :b
            Actual: [:a, :b]
            Output:
            '''))

        when:
        result.assertTasksSkipped()

        then:
        def e8 = thrown(AssertionError)
        error(e8).startsWith(error('''
            Build output does not contain the expected skipped tasks.
            Expected: []
            Actual: [:b]
            '''))

        when:
        result.assertTasksSkipped(":a")

        then:
        def e9 = thrown(AssertionError)
        error(e9).startsWith(error('''
            Build output does not contain the expected skipped tasks.
            Expected: [:a]
            Actual: [:b]
            '''))

        when:
        result.assertTasksSkipped(":b", ":c")

        then:
        def e10 = thrown(AssertionError)
        error(e10).startsWith(error('''
            Build output does not contain the expected skipped tasks.
            Expected: [:b, :c]
            Actual: [:b]
            '''))

        when:
        result.assertTaskSkipped(":a")

        then:
        def e11 = thrown(AssertionError)
        error(e11).startsWith(error('''
            Build output does not contain the expected skipped task.
            Expected: :a
            Actual: [:b]
            '''))

        when:
        result.assertTasksExecuted()

        then:
        def e12 = thrown(IllegalArgumentException)
        error(e12).startsWith(error('''taskPaths cannot be empty.'''))

        when:
        result.assertTasksExecuted(":b")

        then:
        def e13 = thrown(AssertionError)
        error(e13).startsWith(error('''
            Build output does not contain the expected non skipped tasks.
            Expected: [:b]
            Actual: [:a]
            '''))

        when:
        result.assertTasksExecuted(":a", ":c")

        then:
        def e14 = thrown(AssertionError)
        error(e14).startsWith(error('''
            Build output does not contain the expected non skipped tasks.
            Expected: [:a, :c]
            Actual: [:a]
            '''))

        when:
        result.assertTaskExecuted(":b")

        then:
        def e15 = thrown(AssertionError)
        error(e15).startsWith(error('''
            Build output does not contain the expected non skipped task.
            Expected: :b
            Actual: [:a]
            '''))
    }

    def "creates failure result"() {
        def output = """
message

BUILD FAILED in 12s

post build
"""
        when:
        def result = OutputScrapingExecutionResult.from(output, "")

        then:
        result instanceof OutputScrapingExecutionFailure
    }

    def "can assert task is present when task failure output is split into several groups"() {
        def output = """
> Task :compileMyTestBinaryMyTestJava
> Task :myTestBinaryTest

MyTest > test FAILED
    java.lang.AssertionError at MyTest.java:10

1 test completed, 1 failed

> Task :myTestBinaryTest FAILED


FAILURE: Build failed with an exception.

BUILD FAILED in 13s
2 actionable tasks: 2 executed

"""
        when:
        def result = OutputScrapingExecutionResult.from(output, "")

        then:
        result.assertTasksScheduledInOrder(":compileMyTestBinaryMyTestJava", ":myTestBinaryTest")
        result.assertTasksScheduled(":myTestBinaryTest", ":compileMyTestBinaryMyTestJava")
        result.assertTasksExecuted(":myTestBinaryTest", ":compileMyTestBinaryMyTestJava")
    }

    def "can assert no tasks have been executed"() {
        def output = """

FAILURE: Build failed with an exception.

BUILD FAILED in 13s
2 actionable tasks: 2 executed

"""
        when:
        def result = OutputScrapingExecutionResult.from(output, "")

        then:
        result.assertNoTasksScheduled()
    }

    def "can assert any tasks have been executed"() {
        def output = """
> Task :compileMyTestBinaryMyTestJava
> Task :myTestBinaryTest

MyTest > test FAILED
    java.lang.AssertionError at MyTest.java:10

1 test completed, 1 failed

> Task :myTestBinaryTest FAILED


FAILURE: Build failed with an exception.

BUILD FAILED in 13s
2 actionable tasks: 2 executed

"""
        when:
        def result = OutputScrapingExecutionResult.from(output, "")

        then:
        result.assertAnyTasksScheduled()
    }

    def "can assert at least one task was executed and not skipped"() {
        def output = """
> Task :a
> Task :b

> Task :a
some content

> Task :b
other content

> Task :a
all good

> Task :b SKIPPED

FAILURE: Build failed with an exception.

BUILD FAILED in 13s
2 actionable tasks: 2 executed

"""
        when:
        def result = OutputScrapingExecutionResult.from(output, "")

        then:
        result.assertAnyTasksExecuted()
    }

    def 'assertAnyTasksExecuted() fails assertion when output contains no tasks or skipped tasks'() {
        def output = """

$tasksExecuted

FAILURE: Build failed with an exception.

BUILD FAILED in 13s
2 actionable tasks: 2 executed

"""
        when:
        def result = OutputScrapingExecutionResult.from(output, "")
        result.assertAnyTasksExecuted()

        then:
        def e = thrown(AssertionError)
        e.message.startsWith(message)

        where:
        tasksExecuted                                      | message
        '> Task :a SKIPPED\n\n> Task :b SKIPPED'           | "Build output contains only skipped tasks: [:a, :b]"
        ''                                                 | "Build output does not contain any executed tasks."
    }

    def 'assertAllTasksSkipped() fails assertion when output contains at least one task that is executed'() {
        def output = """
> Task :a
> Task :b

> Task :a
some content

> Task :b
other content

> Task :a
all good

> Task :b SKIPPED

FAILURE: Build failed with an exception.

BUILD FAILED in 13s
2 actionable tasks: 2 executed

"""
        when:
        def result = OutputScrapingExecutionResult.from(output, "")
        result.assertAllTasksSkipped()

        then:
        def e = thrown(AssertionError)
        TextUtil.convertLineSeparatorsToUnix(e.message).startsWith("Build output contains unexpected non skipped tasks.\nExpected: []\nActual: [:a]");

    }

    def "can assert all tasks are skipped"() {
        def output = """

$tasksExecuted

FAILURE: Build failed with an exception.

BUILD FAILED in 13s
2 actionable tasks: 2 executed

"""
        when:
        def result = OutputScrapingExecutionResult.from(output, "")

        then:
        result.assertAllTasksSkipped()

        where:
        tasksExecuted << [
            '> Task :a SKIPPED\n\n> Task :b SKIPPED',
            ''
        ]
    }

    def 'throws exception when assertTasksScheduled taskPaths is empty'() {
        def output = """
> Task :compileMyTestBinaryMyTestJava
> Task :myTestBinaryTest

MyTest > test FAILED
    java.lang.AssertionError at MyTest.java:10

1 test completed, 1 failed

> Task :myTestBinaryTest FAILED


FAILURE: Build failed with an exception.

BUILD FAILED in 13s
2 actionable tasks: 2 executed

"""
        when:
        def result = OutputScrapingExecutionResult.from(output, "")
        result.assertTasksScheduled(taskPaths)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.startsWith("taskPaths cannot be empty.")

        where:
        taskPaths << [[] as Object[], null]
    }

    def "throws exception when assertTasksExecuted taskPaths is empty"() {
        def output = """
> Task :compileMyTestBinaryMyTestJava
> Task :myTestBinaryTest

MyTest > test FAILED
    java.lang.AssertionError at MyTest.java:10

1 test completed, 1 failed

> Task :myTestBinaryTest FAILED


FAILURE: Build failed with an exception.

BUILD FAILED in 13s
2 actionable tasks: 2 executed

"""
        when:
        def result = OutputScrapingExecutionResult.from(output, "")
        result.assertTasksExecuted(taskPaths)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.startsWith("taskPaths cannot be empty.")

        where:
        taskPaths << [[] as Object[], null]
    }



    def "strips out work in progress area when evaluating rich console output"() {
        def output = """
\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> Evaluating settings\u001B[m\u001B[21D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> Evaluating settings\u001B[m\u001B[21D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> Evaluating settings\u001B[m\u001B[21D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> Evaluating settings\u001B[m\u001B[21D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> Evaluating settings\u001B[m\u001B[21D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> Loading projects\u001B[m\u001B[0K\u001B[18D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> Loading projects\u001B[m\u001B[18D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> Loading projects\u001B[m\u001B[18D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> Loading projects\u001B[m\u001B[18D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> Loading projects\u001B[m\u001B[18D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% CONFIGURING [1s]\u001B[m\u001B[0K\u001B[35D\u001B[1B\u001B[1m> root project\u001B[m\u001B[0K\u001B[14D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% CONFIGURING [1s]\u001B[m\u001B[35D\u001B[1B\u001B[1m> root project\u001B[m\u001B[14D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% CONFIGURING [1s]\u001B[m\u001B[35D\u001B[1B\u001B[1m> root project\u001B[m\u001B[14D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% CONFIGURING [1s]\u001B[m\u001B[35D\u001B[1B\u001B[1m> root project > Compiling /Users/ghale/repos/gradle/build/tmp/teŝt files/RichConsoleBasicGroupedTaskLoggingFunctionalTest/long_running_task_o...ter_delay/w661/build.gradle into local compilation cache > Compiling build file '/Users/ghale/repos/gradle/build/tmp/test files/RichConsoleBasicGroupedTaskLoggingFunctionalTest/long_running_task_o...ter_delay/w661/build.gradle' to cross build script cache\u001B[m\u001B[400D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% CONFIGURING [1s]\u001B[m\u001B[35D\u001B[1B\u001B[1m> root project\u001B[m\u001B[0K\u001B[14D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% CONFIGURING [1s]\u001B[m\u001B[35D\u001B[1B\u001B[1m> root project > Compiling /Users/ghale/repos/gradle/build/tmp/test files/RichConsoleBasicGroupedTaskLoggingFunctionalTest/long_running_task_o...ter_delay/w661/build.gradle into local compilation cache > Compiling build file '/Users/ghale/repos/gradle/build/tmp/test files/RichConsoleBasicGroupedTaskLoggingFunctionalTest/long_running_task_o...ter_delay/w661/build.gradle' to cross build script cache\u001B[m\u001B[400D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% CONFIGURING [1s]\u001B[m\u001B[35D\u001B[1B\u001B[1m> root project\u001B[m\u001B[0K\u001B[14D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% CONFIGURING [1s]\u001B[m\u001B[35D\u001B[1B\u001B[1m> root project\u001B[m\u001B[14D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% CONFIGURING [1s]\u001B[m\u001B[35D\u001B[1B\u001B[1m> root project\u001B[m\u001B[14D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% CONFIGURING [1s]\u001B[m\u001B[35D\u001B[1B\u001B[1m> root project\u001B[m\u001B[14D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% CONFIGURING [2s]\u001B[m\u001B[35D\u001B[1B\u001B[1m> root project\u001B[m\u001B[14D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% EXECUTING [2s]\u001B[m\u001B[0K\u001B[33D\u001B[1B> IDLE\u001B[0K\u001B[6D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% EXECUTING [2s]\u001B[m\u001B[33D\u001B[1B\u001B[1m> :log\u001B[m\u001B[6D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% EXECUTING [2s]\u001B[m\u001B[33D\u001B[1B\u001B[1m> :log\u001B[m\u001B[6D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% EXECUTING [2s]\u001B[m\u001B[33D\u001B[1B\u001B[1m> :log\u001B[m\u001B[6D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% EXECUTING [2s]\u001B[m\u001B[33D\u001B[1B\u001B[1m> :log\u001B[m\u001B[6D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% EXECUTING [2s]\u001B[m\u001B[33D\u001B[1B\u001B[1m> :log\u001B[m\u001B[6D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% EXECUTING [2s]\u001B[m\u001B[33D\u001B[1B\u001B[1m> :log\u001B[m\u001B[6D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% EXECUTING [2s]\u001B[m\u001B[33D\u001B[1B\u001B[1m> :log\u001B[m\u001B[6D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% EXECUTING [2s]\u001B[m\u001B[33D\u001B[1B\u001B[1m> :log\u001B[m\u001B[6D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% EXECUTING [3s]\u001B[m\u001B[33D\u001B[1B\u001B[1m> :log\u001B[m\u001B[6D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% EXECUTING [3s]\u001B[m\u001B[33D\u001B[1B\u001B[1m> :log\u001B[m\u001B[6D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% EXECUTING [3s]\u001B[m\u001B[33D\u001B[1B\u001B[1m> :log\u001B[m\u001B[6D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% EXECUTING [3s]\u001B[m\u001B[33D\u001B[1B\u001B[1m> :log\u001B[m\u001B[6D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% EXECUTING [3s]\u001B[m\u001B[33D\u001B[1B\u001B[1m> :log\u001B[m\u001B[6D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% EXECUTING [3s]\u001B[m\u001B[33D\u001B[1B\u001B[1m> :log\u001B[m\u001B[6D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% EXECUTING [3s]\u001B[m\u001B[33D\u001B[1B\u001B[1m> :log\u001B[m\u001B[6D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% EXECUTING [3s]\u001B[m\u001B[33D\u001B[1B\u001B[1m> :log\u001B[m\u001B[6D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% EXECUTING [3s]\u001B[m\u001B[33D\u001B[1B\u001B[1m> :log\u001B[m\u001B[6D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% EXECUTING [3s]\u001B[m\u001B[33D\u001B[1B\u001B[1m> :log\u001B[m\u001B[6D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% EXECUTING [4s]\u001B[m\u001B[33D\u001B[1B\u001B[1m> :log\u001B[m\u001B[6D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% EXECUTING [4s]\u001B[m\u001B[33D\u001B[1B\u001B[1m> :log\u001B[m\u001B[6D\u001B[1B\u001B[2A\u001B[0K
\u001B[1m> Task :log\u001B[m\u001B[0K
Before
\u001B[0K
\u001B[0K
\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% EXECUTING [4s]\u001B[m\u001B[33D\u001B[1B\u001B[1m> :log\u001B[m\u001B[6D\u001B[1B\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% EXECUTING [4s]\u001B[m\u001B[33D\u001B[1B\u001B[1m> :log\u001B[m\u001B[6D\u001B[1B\u001B[2AAfter\u001B[0K
\u001B[0K
\u001B[32;1mBUILD SUCCESSFUL\u001B[0;39m in 7s
1 actionable task: 1 executed
\u001B[0K
\u001B[0K
\u001B[2A\u001B[1m<\u001B[0;32;1;0;39;1m-------------> 0% WAITING\u001B[m\u001B[26D\u001B[1B\u001B[1m> :log\u001B[m\u001B[6D\u001B[1B\u001B[2A\u001B[2K\u001B[1B\u001B[2K\u001B[1A
"""
        when:
        def result = OutputScrapingExecutionResult.from(output, "")

        then:
        result.assertTasksScheduled(':log')
        result.groupedOutput.task(':log').output == "Before\nAfter"
    }
}
