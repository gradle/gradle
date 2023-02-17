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

package org.gradle.integtests.fixtures.logging

import org.gradle.integtests.fixtures.executer.LogContent
import org.gradle.integtests.fixtures.logging.GroupedWorkOutputFixture.ComparisonFailureFormat
import org.gradle.integtests.fixtures.logging.comparison.LineSearchFailures
import org.gradle.integtests.fixtures.logging.comparison.LineSearchFailures.PotentialMatchesExistComparisonFailure
import spock.lang.Specification

class GroupedOutputFixtureTest extends Specification {

    def "parses task names"() {
        given:
        def consoleOutput = """
\u001B[2A\u001B[1m<-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> settings\u001B[m\u001B[10D\u001B[1B\u001B[1A\u001B[1m> settings > Compiling /Users/daniel/gradle/gradle/build/tmp/test files/BasicGroupedTaskLoggingFunctionalSpec/test/oww7k/settings.gradle into local compilation cache > Compiling settings file '/Users/daniel/gradle/gradle/build/tmp/test files/BasicGroupedTaskLoggingFunctionalSpec/test/oww7k/settings.gradle' to cross build script cache\u001B[m\u001B[335D\u001B[1B\u001B[1A\u001B[1m> settings > Compiling /Users/daniel/gradle/gradle/build/tmp/test files/BasicGroupedTaskLoggingFunctionalSpec/test/oww7k/settings.gradle into local compilation cache\u001B[m\u001B[0K\u001B[165D\u001B[1B\u001B[1A\u001B[1m> settings\u001B[m\u001B[0K\u001B[10D\u001B[1B\u001B[1A\u001B[90m> IDLE\u001B[39m\u001B[0K\u001B[6D\u001B[1B\u001B[2A\u001B[1m<-------------> 0% CONFIGURING [0s]\u001B[m\u001B[0K\u001B[35D\u001B[2B\u001B[1A\u001B[1m> root project > Compiling /Users/daniel/gradle/gradle/build/tmp/test files/BasicGroupedTaskLoggingFunctionalSpec/test/oww7k/build.gradle into local compilation cache\u001B[m\u001B[166D\u001B[1B\u001B[2A\u001B[1m<-------------> 0% CONFIGURING [1s]\u001B[m\u001B[35D\u001B[1B\u001B[1m> root project\u001B[m\u001B[0K\u001B[14D\u001B[1B\u001B[2A\u001B[1m<-------------> 0% EXECUTING [1s]\u001B[m\u001B[0K\u001B[33D\u001B[1B\u001B[90m> IDLE\u001B[39m\u001B[0K\u001B[6D\u001B[1B\u001B[2A\u001B[0K
\u001B[1m> Task :1:log\u001B[m\u001B[0K
Output from 1

\u001B[1m> Task :2:log\u001B[m
Output from 2
More output from 2

\u001B[1m> Task :3:log\u001B[m
Output from 3



Handles lots of newline characters


\u001B[32;1mBUILD SUCCESSFUL\u001B[0;39m in 2s
3 actionable tasks: 3 executed
\u001B[2K
"""
        when:
        GroupedOutputFixture groupedOutput = new GroupedOutputFixture(LogContent.of(consoleOutput))

        then:
        groupedOutput.taskCount == 3
        groupedOutput.task(':1:log').output == 'Output from 1'
        groupedOutput.task(':2:log').output == 'Output from 2\nMore output from 2'
        groupedOutput.task(':3:log').output == 'Output from 3\n\n\n\nHandles lots of newline characters'
    }

    def "parses incremental tasks"() {
        given:
        def consoleOutput = """
\u001B[1m<-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> settings\u001B[m\u001B[10D\u001B[1B\u001B[1A\u001B[90m> IDLE\u001B[39m\u001B[0K\u001B[6D\u001B[1B\u001B[2A\u001B[1m<-------------> 0% CONFIGURING [0s]\u001B[m\u001B[0K\u001B[35D\u001B[2B\u001B[1A\u001B[1m> root project\u001B[m\u001B[14D\u001B[1B\u001B[2A\u001B[1m<-------------> 0% EXECUTING [0s]\u001B[m\u001B[0K\u001B[33D\u001B[1B\u001B[90m> IDLE\u001B[39m\u001B[0K\u001B[6D\u001B[1B\u001B[1A\u001B[1m> :longRunningTask\u001B[m\u001B[18D\u001B[1B\u001B[2A\u001B[1m<-------------> 0% EXECUTING [1s]\u001B[m\u001B[33D\u001B[2B\u001B[2A\u001B[1m<-------------> 0% EXECUTING [2s]\u001B[m\u001B[33D\u001B[2B\u001B[2A\u001B[1m<-------------> 0% EXECUTING [3s]\u001B[m\u001B[33D\u001B[2B\u001B[2A\u001B[1m<-------------> 0% EXECUTING [4s]\u001B[m\u001B[33D\u001B[2B\u001B[2A\u001B[1m<-------------> 0% EXECUTING [5s]\u001B[m\u001B[33D\u001B[2B\u001B[2A\u001B[0K
\u001B[1m> Task :longRunningTask\u001B[m\u001B[0K
First incremental output
\u001B[0K
\u001B[0K
\u001B[2A\u001B[1m<-------------> 0% EXECUTING [6s]\u001B[m\u001B[33D\u001B[1B\u001B[1m> :longRunningTask\u001B[m\u001B[18D\u001B[1B\u001B[2A\u001B[1m<-------------> 0% EXECUTING [7s]\u001B[m\u001B[33D\u001B[2B\u001B[2A\u001B[1m<-------------> 0% EXECUTING [8s]\u001B[m\u001B[33D\u001B[2B\u001B[2ASecond incremental output\u001B[0K
\u001B[0K
\u001B[0K
\u001B[1A\u001B[1m<-------------> 0% EXECUTING [8s]\u001B[m\u001B[33D\u001B[1B\u001B[1m> :longRunningTask\u001B[m\u001B[18D\u001B[1B\u001B[2A\u001B[0K
\u001B[0K
\u001B[32;1mBUILD SUCCESSFUL\u001B[0;39m in 9s
1 actionable task: 1 executed
\u001B[2K
"""
        when:
        GroupedOutputFixture fixture = new GroupedOutputFixture(LogContent.of(consoleOutput))

        then:
        fixture.taskCount == 1
        fixture.task(':longRunningTask').output == 'First incremental output\nSecond incremental output'
    }

    def "parses tasks with progress bar interference"() {
        given:
        def consoleOutput = """
\u001B[2A\u001B[1m<-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> settings\u001B[m\u001B[10D\u001B[1B\u001B[1A\u001B[90m> IDLE\u001B[39m\u001B[0K\u001B[6D\u001B[1B\u001B[2A\u001B[1m<-------------> 0% CONFIGURING [0s]\u001B[m\u001B[0K\u001B[35D\u001B[2B\u001B[1A\u001B[1m> root project > Compiling /Users/daniel/gradle/gradle/build/tmp/test files/BasicGroupedTaskLoggingFunctionalSpec/test/rxozi/build.gradle into local compilation cache > Compiling build file '/Users/daniel/gradle/gradle/build/tmp/test files/BasicGroupedTaskLoggingFunctionalSpec/test/rxozi/build.gradle' to cross build script cache\u001B[m\u001B[330D\u001B[1B\u001B[1A\u001B[1m> root project > Compiling /Users/daniel/gradle/gradle/build/tmp/test files/BasicGroupedTaskLoggingFunctionalSpec/test/rxozi/build.gradle into local compilation cache\u001B[m\u001B[0K\u001B[166D\u001B[1B\u001B[1A\u001B[1m> root project > Compiling /Users/daniel/gradle/gradle/build/tmp/test files/BasicGroupedTaskLoggingFunctionalSpec/test/rxozi/build.gradle into local compilation cache > Compiling build file '/Users/daniel/gradle/gradle/build/tmp/test files/BasicGroupedTaskLoggingFunctionalSpec/test/rxozi/build.gradle' to cross build script cache\u001B[m\u001B[330D\u001B[1B\u001B[2A\u001B[1m<-------------> 0% CONFIGURING [1s]\u001B[m\u001B[35D\u001B[1B\u001B[1m> root project\u001B[m\u001B[0K\u001B[14D\u001B[1B\u001B[2A\u001B[1m<-------------> 0% EXECUTING [1s]\u001B[m\u001B[0K\u001B[33D\u001B[1B\u001B[90m> IDLE\u001B[39m\u001B[0K\u001B[6D\u001B[1B\u001B[2A\u001B[0K
\u001B[1m> Task :1:log\u001B[m\u001B[0K
Output from 1
\u001B[0K
\u001B[0K
\u001B[2A\u001B[1m<====---------> 33% EXECUTING [1s]\u001B[m\u001B[34D\u001B[1B\u001B[1m> :2:log\u001B[m\u001B[8D\u001B[1B\u001B[2A\u001B[0K
\u001B[1m> Task :2:log\u001B[m\u001B[0K
Output from 2

\u001B[1m> Task :3:log\u001B[m
Output from 3


\u001B[32;1mBUILD SUCCESSFUL\u001B[0;39m in 2s
3 actionable tasks: 3 executed
\u001B[2K
"""
        when:
        GroupedOutputFixture groupedOutput = new GroupedOutputFixture(LogContent.of(consoleOutput))

        then:
        groupedOutput.taskCount == 3
        groupedOutput.task(':1:log').output == 'Output from 1'
        groupedOutput.task(':2:log').output == 'Output from 2'
    }

    def "handles task outputs with erase to end of line chars"() {
        given:
        def consoleOutput = """
\u001B[2A\u001B[1m<-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> settings\u001B[m\u001B[10D\u001B[1B\u001B[1A\u001B[90m> IDLE\u001B[39m\u001B[0K\u001B[6D\u001B[1B\u001B[2A\u001B[1m<-------------> 0% CONFIGURING [0s]\u001B[m\u001B[0K\u001B[35D\u001B[2B\u001B[2AParallel execution is an incubating feature.\u001B[0K
\u001B[1B\u001B[0K
\u001B[2A\u001B[1m<-------------> 0% CONFIGURING [0s]\u001B[m\u001B[35D\u001B[1B\u001B[1m> root project > Compiling /Users/daniel/gradle/gradle/build/tmp/test files/BasicGroupedTaskLoggingFunctionalSpec/test/u4jgn/build.gradle into local compilation cache > Compiling build file '/Users/daniel/gradle/gradle/build/tmp/test files/BasicGroupedTaskLoggingFunctionalSpec/test/u4jgn/build.gradle' to cross build script cache\u001B[m\u001B[330D\u001B[1B\u001B[1A\u001B[1m> root project > Compiling /Users/daniel/gradle/gradle/build/tmp/test files/BasicGroupedTaskLoggingFunctionalSpec/test/u4jgn/build.gradle into local compilation cache\u001B[m\u001B[0K\u001B[166D\u001B[1B\u001B[1A\u001B[1m> root project > Compiling /Users/daniel/gradle/gradle/build/tmp/test files/BasicGroupedTaskLoggingFunctionalSpec/test/u4jgn/build.gradle into local compilation cache > Compiling build file '/Users/daniel/gradle/gradle/build/tmp/test files/BasicGroupedTaskLoggingFunctionalSpec/test/u4jgn/build.gradle' to cross build script cache\u001B[m\u001B[330D\u001B[1B\u001B[1A\u001B[1m> root project\u001B[m\u001B[0K\u001B[14D\u001B[1B\u001B[2A\u001B[1m<-------------> 0% EXECUTING [1s]\u001B[m\u001B[0K\u001B[33D\u001B[1B\u001B[1m> :log\u001B[m\u001B[0K\u001B[6D\u001B[1B


\u001B[3A\u001B[1m> :1:log\u001B[m\u001B[8D\u001B[1B\u001B[1m> :3:log\u001B[m\u001B[8D\u001B[1B\u001B[1m> :2:log\u001B[m\u001B[8D\u001B[1B\u001B[5A\u001B[1m<-------------> 0% EXECUTING [2s]\u001B[m\u001B[33D\u001B[5B\u001B[5A\u001B[0K
\u001B[1m> Task :log\u001B[m\u001B[0K
First line of text\u001B[0K
\u001B[0K
\u001B[0K

Last line of text
\u001B[0K
\u001B[0K
\u001B[0K
\u001B[0K
\u001B[0K
\u001B[5A\u001B[1m<==-----------> 20% EXECUTING [2s]\u001B[m\u001B[34D\u001B[1B\u001B[90m> IDLE\u001B[39m\u001B[6D\u001B[1B\u001B[1m> :1:log\u001B[m\u001B[8D\u001B[1B\u001B[1m> :3:log\u001B[m\u001B[8D\u001B[1B\u001B[1m> :2:log\u001B[m\u001B[8D\u001B[1B\u001B[5A\u001B[1m<==-----------> 20% EXECUTING [3s]\u001B[m\u001B[34D\u001B[5B\u001B[5A\u001B[0K
\u001B[0K
\u001B[32;1mBUILD SUCCESSFUL\u001B[0;39m in 4s\u001B[0K
4 actionable tasks: 4 executed\u001B[0K
\u001B[2K\u001B[1B\u001B[2K\u001B[1A
"""

        when:
        GroupedOutputFixture groupedOutput = new GroupedOutputFixture(LogContent.of(consoleOutput))

        then:
        groupedOutput.task(':log').output == 'First line of text\n\n\n\nLast line of text'
    }

    def "handles erase directly before progress bar right before end of build"() {
        given:
        def consoleOutput = """
\u001B[2A\u001B[1m<-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> settings\u001B[m\u001B[10D\u001B[1B\u001B[1A\u001B[90m> IDLE\u001B[39m\u001B[0K\u001B[6D\u001B[1B\u001B[2A\u001B[1m<-------------> 0% CONFIGURING [0s]\u001B[m\u001B[0K\u001B[35D\u001B[2B\u001B[1A\u001B[1m> root project > Compiling /Users/daniel/gradle/gradle/build/tmp/test files/BasicGroupedTaskLoggingFunctionalSpec/test/7n483/build.gradle into local compilation cache > Compiling build file '/Users/daniel/gradle/gradle/build/tmp/test files/BasicGroupedTaskLoggingFunctionalSpec/test/7n483/build.gradle' to cross build script cache\u001B[m\u001B[330D\u001B[1B\u001B[1A\u001B[1m> root project > Compiling /Users/daniel/gradle/gradle/build/tmp/test files/BasicGroupedTaskLoggingFunctionalSpec/test/7n483/build.gradle into local compilation cache\u001B[m\u001B[0K\u001B[166D\u001B[1B\u001B[1A\u001B[1m> root project > Compiling /Users/daniel/gradle/gradle/build/tmp/test files/BasicGroupedTaskLoggingFunctionalSpec/test/7n483/build.gradle into local compilation cache > Compiling build file '/Users/daniel/gradle/gradle/build/tmp/test files/BasicGroupedTaskLoggingFunctionalSpec/test/7n483/build.gradle' to cross build script cache\u001B[m\u001B[330D\u001B[1B\u001B[1A\u001B[1m> root project\u001B[m\u001B[0K\u001B[14D\u001B[1B\u001B[2A\u001B[1m<-------------> 0% EXECUTING [1s]\u001B[m\u001B[0K\u001B[33D\u001B[1B\u001B[90m> IDLE\u001B[39m\u001B[0K\u001B[6D\u001B[1B\u001B[2A\u001B[0K
\u001B[1m> Task :1:log\u001B[m\u001B[0K
Output from 1
\u001B[0K
\u001B[0K
\u001B[2A\u001B[1m<======-------> 50% EXECUTING [1s]\u001B[m\u001B[34D\u001B[1B\u001B[1m> :2:log\u001B[m\u001B[8D\u001B[1B\u001B[2A\u001B[0K
\u001B[0K
\u001B[32;1mBUILD SUCCESSFUL\u001B[0;39m in 2s
2 actionable tasks: 2 executed
\u001B[2K
"""
        when:
        GroupedOutputFixture groupedOutput = new GroupedOutputFixture(LogContent.of(consoleOutput))

        then:
        groupedOutput.task(':1:log').output == "Output from 1"
    }

    def "handles multiple lines of progress bar"() {
        given:
        def consoleOutput = """
\u001B[2A\u001B[1m<-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> settings\u001B[m\u001B[10D\u001B[1B\u001B[1A\u001B[90m> IDLE\u001B[39m\u001B[0K\u001B[6D\u001B[1B\u001B[2A\u001B[1m<-------------> 0% CONFIGURING [0s]\u001B[m\u001B[0K\u001B[35D\u001B[2B\u001B[2AParallel execution is an incubating feature.\u001B[0K
\u001B[1B\u001B[0K
\u001B[2A\u001B[1m<-------------> 0% CONFIGURING [0s]\u001B[m\u001B[35D\u001B[1B\u001B[1m> root project > Compiling /Users/daniel/gradle/gradle/build/tmp/test files/BasicGroupedTaskLoggingFunctionalSpec/test/omm11/build.gradle into local compilation cache > Compiling build file '/Users/daniel/gradle/gradle/build/tmp/test files/BasicGroupedTaskLoggingFunctionalSpec/test/omm11/build.gradle' to cross build script cache\u001B[m\u001B[330D\u001B[1B\u001B[1A\u001B[1m> root project > Compiling /Users/daniel/gradle/gradle/build/tmp/test files/BasicGroupedTaskLoggingFunctionalSpec/test/omm11/build.gradle into local compilation cache\u001B[m\u001B[0K\u001B[166D\u001B[1B\u001B[1A\u001B[1m> root project > Compiling /Users/daniel/gradle/gradle/build/tmp/test files/BasicGroupedTaskLoggingFunctionalSpec/test/omm11/build.gradle into local compilation cache > Compiling build file '/Users/daniel/gradle/gradle/build/tmp/test files/BasicGroupedTaskLoggingFunctionalSpec/test/omm11/build.gradle' to cross build script cache\u001B[m\u001B[330D\u001B[1B\u001B[2A\u001B[1m<-------------> 0% CONFIGURING [1s]\u001B[m\u001B[35D\u001B[1B\u001B[1m> root project\u001B[m\u001B[0K\u001B[14D\u001B[1B\u001B[2A\u001B[1m<-------------> 0% EXECUTING [1s]\u001B[m\u001B[0K\u001B[33D\u001B[1B\u001B[90m> IDLE\u001B[39m\u001B[0K\u001B[6D\u001B[1B

\u001B[3A\u001B[1m> :1:log\u001B[m\u001B[8D\u001B[1B\u001B[1m> :2:log\u001B[m\u001B[8D\u001B[1B\u001B[1m> :3:log\u001B[m\u001B[8D\u001B[1B\u001B[4A\u001B[0K
\u001B[1m> Task :1:log\u001B[m\u001B[0K
Output from 1\u001B[0K
\u001B[0K
\u001B[1m> Task :3:log\u001B[m
Output from 3

\u001B[1m> Task :2:log\u001B[m
Output from 2


\u001B[32;1mBUILD SUCCESSFUL\u001B[0;39m in 2s
3 actionable tasks: 3 executed
\u001B[2K
"""
        when:
        GroupedOutputFixture groupedOutput = new GroupedOutputFixture(LogContent.of(consoleOutput))

        then:
        groupedOutput.taskCount == 3
        groupedOutput.task(':1:log').output == "Output from 1"
        groupedOutput.task(':2:log').output == "Output from 2"
        groupedOutput.task(':3:log').output == "Output from 3"
    }

    def "throws exception if task group could not be parsed"() {
        given:
        def consoleOutput = """
\u001B[2A\u001B[1m<-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> settings\u001B[m\u001B[10D\u001B[1B\u001B[1A\u001B[1m> settings > Compiling /Users/daniel/gradle/gradle/build/tmp/test files/BasicGroupedTaskLoggingFunctionalSpec/test/oww7k/settings.gradle into local compilation cache > Compiling settings file '/Users/daniel/gradle/gradle/build/tmp/test files/BasicGroupedTaskLoggingFunctionalSpec/test/oww7k/settings.gradle' to cross build script cache\u001B[m\u001B[335D\u001B[1B\u001B[1A\u001B[1m> settings > Compiling /Users/daniel/gradle/gradle/build/tmp/test files/BasicGroupedTaskLoggingFunctionalSpec/test/oww7k/settings.gradle into local compilation cache\u001B[m\u001B[0K\u001B[165D\u001B[1B\u001B[1A\u001B[1m> settings\u001B[m\u001B[0K\u001B[10D\u001B[1B\u001B[1A\u001B[90m> IDLE\u001B[39m\u001B[0K\u001B[6D\u001B[1B\u001B[2A\u001B[1m<-------------> 0% CONFIGURING [0s]\u001B[m\u001B[0K\u001B[35D\u001B[2B\u001B[1A\u001B[1m> root project > Compiling /Users/daniel/gradle/gradle/build/tmp/test files/BasicGroupedTaskLoggingFunctionalSpec/test/oww7k/build.gradle into local compilation cache\u001B[m\u001B[166D\u001B[1B\u001B[2A\u001B[1m<-------------> 0% CONFIGURING [1s]\u001B[m\u001B[35D\u001B[1B\u001B[1m> root project\u001B[m\u001B[0K\u001B[14D\u001B[1B\u001B[2A\u001B[1m<-------------> 0% EXECUTING [1s]\u001B[m\u001B[0K\u001B[33D\u001B[1B\u001B[90m> IDLE\u001B[39m\u001B[0K\u001B[6D\u001B[1B\u001B[2A\u001B[0K
\u001B[1m> Task :1:log\u001B[m\u001B[0K
Output from 1

\u001B[1m> Task :2:log\u001B[m
Output from 2


\u001B[32;1mBUILD SUCCESSFUL\u001B[0;39m in 2s
3 actionable tasks: 3 executed
\u001B[2K
"""
        GroupedOutputFixture groupedOutput = new GroupedOutputFixture(LogContent.of(consoleOutput))

        when:
        groupedOutput.task(':doesNotExist')

        then:
        def t = thrown(AssertionError)
        t.message.startsWith("The grouped output for task ':doesNotExist' could not be found.")
    }

    def "handles output right before end of failed build"() {
        given:
        def consoleOutput = """
\u001B[2A\u001B[1m<-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> settings\u001B[m\u001B[10D\u001B[1B\u001B[1A\u001B[90m> IDLE\u001B[39m\u001B[0K\u001B[6D\u001B[1B\u001B[2A\u001B[1m<-------------> 0% CONFIGURING [0s]\u001B[m\u001B[0K\u001B[35D\u001B[2B\u001B[2AParallel execution is an incubating feature.\u001B[0K
\u001B[1B\u001B[0K
\u001B[2A\u001B[1m<-------------> 0% CONFIGURING [0s]\u001B[m\u001B[35D\u001B[1B\u001B[1m> root project\u001B[m\u001B[14D\u001B[1B\u001B[2A\u001B[1m<-------------> 0% EXECUTING [0s]\u001B[m\u001B[0K\u001B[33D\u001B[1B\u001B[90m> IDLE\u001B[39m\u001B[0K\u001B[6D\u001B[1B\u001B[2A\u001B[0K
\u001B[1m> Task :log\u001B[m\u001B[0K
Output from :log


\u001B[31;1mBUILD FAILED\u001B[0;39m in 1s
1 actionable task: 1 executed
\u001B[2K
"""
        when:
        GroupedOutputFixture groupedOutput = new GroupedOutputFixture(LogContent.of(consoleOutput))

        then:
        groupedOutput.task(':log').output == 'Output from :log'
    }

    def "handles output interleaved with end-of-line erasing"() {
        given:
        def consoleOutput = """\u001B[1A\u001B[1m> Connecting to Daemon\u001B[m\u001B[22D
\u001B[1A\u001B[90m> IDLE\u001B[39m\u001B[0K\u001B[6D
\u001B[1A\u001B[1m<-------------> 0% CONFIGURING [0s]\u001B[m\u001B[35D\u001B[1B\u001B[1m> root project\u001B[m\u001B[14D
\u001B[1A\u001B[1m> root project > Compiling /home/tcagent2/agent/work/1c72cb73edd79150/subprojects/logging/build/tmp/test files/BasicGroupedTaskLoggingFunctionalSpec/long_running_task_o..._5s_delay/m5mh7/build.gradle into local compilation cache\u001B[m\u001B[228D
\u001B[2A\u001B[1m<-------------> 0% CONFIGURING [1s]\u001B[m\u001B[35D\u001B[1B
\u001B[2A\u001B[1m<-------------> 0% EXECUTING [1s]\u001B[m\u001B[0K\u001B[33D\u001B[1B\u001B[1m> :log\u001B[m\u001B[0K\u001B[6D
\u001B[2A\u001B[1m<-------------> 0% EXECUTING [2s]\u001B[m\u001B[33D\u001B[1B
\u001B[2A\u001B[1m<-------------> 0% EXECUTING [3s]\u001B[m\u001B[33D\u001B[1B
\u001B[2A\u001B[1m<-------------> 0% EXECUTING [4s]\u001B[m\u001B[33D\u001B[1B
\u001B[2A\u001B[1m> Task :log\u001B[m\u001B[0K
Before\u001B[0K
\u001B[0K
\u001B[0K
\u001B[2A\u001B[1m<-------------> 0% EXECUTING [6s]\u001B[m\u001B[33D\u001B[1B\u001B[1m> :log\u001B[m\u001B[6D
\u001B[2A\u001B[1m<-------------> 0% EXECUTING [7s]\u001B[m\u001B[33D\u001B[1B
\u001B[2A\u001B[1m<-------------> 0% EXECUTING [8s]\u001B[m\u001B[33D\u001B[1B
\u001B[2AAfter\u001B[0K
\u001B[0K
\u001B[0K
\u001B[2A\u001B[1m<-------------> 0% EXECUTING [8s]\u001B[m\u001B[33D\u001B[1B\u001B[1m> :log\u001B[m\u001B[6D
\u001B[2A\u001B[0K
\u001B[0K
\u001B[32;1mBUILD SUCCESSFUL\u001B[0;39m in 9s
1 actionable task: 1 executed
\u001B[2K"""

        when:
        GroupedOutputFixture groupedOutput = new GroupedOutputFixture(LogContent.of(consoleOutput))

        then:
        groupedOutput.task(':log').output == 'Before\nAfter'
    }

    def "handles complex work-in-progress items"() {
        given:
        def consoleOutput = """
\u001B[1m> Task :buildSrc:helloWorld\u001B[m
Hello world


\u001B[2A\u001B[1m<-------------> 0% CONFIGURING [0s]\u001B[m\u001B[35D\u001B[1B\u001B[1m> root project > Compiling /Users/daniel/gradle/gradle/build/tmp/test files/ConsoleBuildSrcFunctionalTest/can_group_task_outp..._buildSrc/pco71/build.gradle into local compilation cache\u001B[m\u001B[185D\u001B[1B\u001B[2A\u001B[0K
\u001B[1m> Task :byeWorld\u001B[m\u001B[0K
Bye world


\u001B[32;1mBUILD SUCCESSFUL\u001B[0;39m in 0s
1 actionable task: 1 executed
\u001B[2K"""

        when:
        GroupedOutputFixture groupedOutput = new GroupedOutputFixture(LogContent.of(consoleOutput))

        then:
        groupedOutput.task(':buildSrc:helloWorld').output == 'Hello world'
    }

    def "strip output removes all ANSI control sequences and work-in-progress area"() {
        def consoleOutput = """
\u001B[1m> Task :buildSrc:helloWorld\u001B[m
Hello world


\u001B[2A\u001B[1m<-------------> 0% CONFIGURING [0s]\u001B[m\u001B[35D\u001B[1B\u001B[1m> root project > Compiling /Users/daniel/gradle/gradle/build/tmp/test files/ConsoleBuildSrcFunctionalTest/can_group_task_outp..._buildSrc/pco71/build.gradle into local compilation cache\u001B[m\u001B[185D\u001B[1B\u001B[2A\u001B[0K
\u001B[1m> Task :byeWorld\u001B[m\u001B[0K
Bye world


\u001B[32;1mBUILD SUCCESSFUL\u001B[0;39m in 0s
1 actionable task: 1 executed
\u001B[2K"""

        when:
        GroupedOutputFixture groupedOutput = new GroupedOutputFixture(LogContent.of(consoleOutput))

        then:
        groupedOutput.strippedOutput == '''
> Task :buildSrc:helloWorld
Hello world

> Task :byeWorld
Bye world


BUILD SUCCESSFUL in 0s
1 actionable task: 1 executed
'''
    }

    def "strip output removes formatted work in-progress items"() {
        def consoleOutput = """
\u001B[2A\u001B[1m<-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> settings\u001B[m\u001B[10D
\u001B[1A> IDLE\u001B[0K\u001B[6D
\u001B[2A\u001B[1m<-------------> 0% CONFIGURING [1s]\u001B[m\u001B[0K\u001B[35D\u001B[1B\u001B[1m> root project\u001B[m\u001B[14D
\u001B[2A\u001B[1m<-------------> 0% EXECUTING [2s]\u001B[m\u001B[0K\u001B[33D\u001B[1B> IDLE\u001B[0K\u001B[6D
\u001B[1A\u001B[1m> :run\u001B[m\u001B[6D
\u001B[2A\u001B[0K
\u001B[1m> Task :run\u001B[m\u001B[0K
Hello, World!
\u001B[0K
\u001B[0K
\u001B[2A\u001B[1m<=============> 100% EXECUTING [3s]\u001B[m\u001B[35D\u001B[1B> IDLE\u001B[6D
\u001B[2A\u001B[0K
\u001B[0K
\u001B[32;1mBUILD SUCCESSFUL\u001B[0;39m in 6s
1 actionable task: 1 executed
\u001B[2K"""

        when:
        GroupedOutputFixture groupedOutput = new GroupedOutputFixture(LogContent.of(consoleOutput))

        then:
        groupedOutput.strippedOutput == '''
> Task :run
Hello, World!


BUILD SUCCESSFUL in 6s
1 actionable task: 1 executed
'''
        groupedOutput.task(':run').output == 'Hello, World!'
    }

    def "accepts start of embedded build as end of group"() {
        def consoleOutput = """
> Task :helloWorld
Hello, World!

> :otherBuild > :abc"""

        when:
        GroupedOutputFixture groupedOutput = new GroupedOutputFixture(LogContent.of(consoleOutput))

        then:
        groupedOutput.task(':helloWorld').output == 'Hello, World!'
    }

    def "accepts start of sub progress logger as end of group"() {
        def consoleOutput = """
> Task :helloWorld
Hello, World!

> :otherBuild > Doing some work"""

        when:
        GroupedOutputFixture groupedOutput = new GroupedOutputFixture(LogContent.of(consoleOutput))

        then:
        groupedOutput.task(':helloWorld').output == 'Hello, World!'
    }

    def "does not fail with stack overflow error"() {
        def consoleOutput = """
 [1m> Task :xx:
 [3A [1m< [0;32;1;0;39;1m-------------> build\\tmp\\test files\\ConsoleBuildSrcFunctionalTest\\can_group_task_outp... 'C\\tcagent1\\work\\4b92f910977a653d\\subprojects\\logging\\build\\tmp\\test files\\ConsoleBuildSrcFunctionalTest\\can_group_task_outp..._buildSrc\\j2q4s\\build.gradle' to cross build script cache [m [420D [2B
 [3A [1m< [0;32;1;0;39;1m-------------> build\\tmp\\test files\\ConsoleBuildSrcFunctionalTest\\can_group_task_outp..._buildSrc\\j2q4s\\build.gradle into local compilation cache > Compiling build file 'C:\\tcagent1\\work\\4b92f910977a653d\\subprojects\\logging\\build\\tmp\\test files\\ConsoleBuildSrcFunctionalTest\\can_group_task_outp..._buildSrc\\j2q4s\\build.gradle' to cross build script cache [m [420D [2B
 [3A [1m< [0;32;1;0;39;1m-------------> build\\tmp\\test files\\ConsoleBuildSrcFunctionalTest\\can_group_task_outp..._buildSrc\\j2q4s\\build.gradle into local compilation cache > Compiling build file 'C:\\tcagent1\\work\\4b92f910977a653d\\subprojects\\logging\\build\\tmp\\test files\\ConsoleBuildSrcFunctionalTest\\can_group_task_outp..._buildSrc\\j2q4s\\build.gradle' to cross build script cache [m [420D [2B
 [3A [1m< [0;32;1;0;39;1m-------------> 0% CONFIGURING [5s] [m [35D [1B [1m> root project > Compiling C:\\tcagent1\\work\\4b92f910977a653d\\subprojects\\logging\\build\\tmp\\test files\\ConsoleBuildSrcFunctionalTest\\can_group_task_outp..._buildSrc\\j2q4s\\build.gradle into local compilation cache > Compiling build file 'C:\\tcagent1\\work\\4b92f910977a653d\\subprojects\\logging\\build\\tmp\\test files\\ConsoleBuildSrcFunctionalTest\\can_group_task_outp..._buildSrc\\j2q4s\\build.gradle' to cross build script cache [m [420D [2B
"""
        when:
        new GroupedOutputFixture(LogContent.of(consoleOutput))

        then:
        noExceptionThrown()
    }

    // region assertOutputContains
    def "test assertOutputContains searching for single expected line"() {
        given: "sample output for a task not containing the expected line"
        def consoleOutput = """> Task :example1
toast
bacon
eggs
toast
ham
eggs
> Task :example2
toast
ham
eggs
toast
eggs
"""
        GroupedOutputFixture groupedOutput = new GroupedOutputFixture(LogContent.of(consoleOutput))

        when:
        groupedOutput.task(':example1').assertOutputContains("waffles")

        then:
        thrown(LineSearchFailures.NoMatchingLinesExistComparisonFailure)
    }

    def "test assertOutputContains using LINEWISE format searching for multiple expected lines"() {
        given: "sample output for a task not containing the expected lines in a continuous sequence"
        def consoleOutput = """> Task :example1
toast
bacon
eggs
toast
ham
eggs
> Task :example2
toast
ham
eggs
toast
eggs
"""
        GroupedOutputFixture groupedOutput = new GroupedOutputFixture(LogContent.of(consoleOutput))

        when:
        groupedOutput.task(':example1').assertOutputContains(ComparisonFailureFormat.LINEWISE, """eggs
bacon""")

        then:
        def e = thrown(PotentialMatchesExistComparisonFailure)
        e.message == """Lines not found.  Similar sections:

Potential Match (actual lines):
 [ X   1: toast
expected:<[eggs]> but was:<[toast]>
     ] 2: bacon
       3: eggs
       4: toast
       5: ham

Potential Match (actual lines):
       1: toast
       2: bacon
 [     3: eggs
   X ] 4: toast
expected:<[bacon]> but was:<[toast]>
       5: ham
       6: eggs
"""
        e.numPotentialMatches == 2
    }

    def "test assertOutputContains using UNIFIED format searching for multiple expected lines"() {
        given: "sample output for a task not containing the expected lines in a continuous sequence"
        def consoleOutput = """> Task :example1
toast
bacon
eggs
toast
ham
eggs
> Task :example2
toast
ham
eggs
toast
eggs
"""
        GroupedOutputFixture groupedOutput = new GroupedOutputFixture(LogContent.of(consoleOutput))

        when:
        groupedOutput.task(':example1').assertOutputContains(ComparisonFailureFormat.UNIFIED, """eggs
bacon""")

        then:
        def e = thrown(PotentialMatchesExistComparisonFailure)
        e.message == """Lines not found.  Similar sections:

@@ -1,2 +1,6 @@
-eggs
+toast
 bacon
+eggs
+toast
+ham
+eggs"""
        e.numPotentialMatches == 2
    }

    def "test assertOutputContains using legacy format where expected lines (#expected) are each present, but not in a continuous sequence"() {
        given: "sample output for a task not containing the expected lines in a continuous sequence"
        def consoleOutput = """> Task :example1
toast
bacon
eggs
toast
ham
eggs
> Task :example2
toast
ham
eggs
toast
eggs
"""
        GroupedOutputFixture groupedOutput = new GroupedOutputFixture(LogContent.of(consoleOutput))

        expect:
        groupedOutput.task(':example1').assertOutputContains("""toast
bacon
eggs
""")
    }
    // endregion assertOutputContains
}
