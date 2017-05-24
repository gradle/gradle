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
        GroupedOutputFixture groupedOutput = new GroupedOutputFixture(consoleOutput)

        then:
        groupedOutput.taskCount == 3
        groupedOutput.task(':1:log').output == 'Output from 1'
        groupedOutput.task(':2:log').output == 'Output from 2\nMore output from 2'
        groupedOutput.task(':3:log').output == 'Output from 3\n\n\n\nHandles lots of newline characters'
    }

    def "parses incremental tasks"() {
        given:
        def consoleOutput = """
\u001B[2A\u001B[1m<-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> settings\u001B[m\u001B[10D\u001B[1B\u001B[1A\u001B[90m> IDLE\u001B[39m\u001B[0K\u001B[6D\u001B[1B\u001B[2A\u001B[1m<-------------> 0% CONFIGURING [0s]\u001B[m\u001B[0K\u001B[35D\u001B[2B\u001B[1A\u001B[1m> root project\u001B[m\u001B[14D\u001B[1B\u001B[2A\u001B[1m<-------------> 0% EXECUTING [0s]\u001B[m\u001B[0K\u001B[33D\u001B[1B\u001B[90m> IDLE\u001B[39m\u001B[0K\u001B[6D\u001B[1B\u001B[1A\u001B[1m> :longRunningTask\u001B[m\u001B[6D\u001B[1B\u001B[2A\u001B[1m<-------------> 0% EXECUTING [1s]\u001B[m\u001B[33D\u001B[2B\u001B[2A\u001B[1m<-------------> 0% EXECUTING [2s]\u001B[m\u001B[33D\u001B[2B\u001B[2A\u001B[1m<-------------> 0% EXECUTING [3s]\u001B[m\u001B[33D\u001B[2B\u001B[2A\u001B[1m<-------------> 0% EXECUTING [4s]\u001B[m\u001B[33D\u001B[2B\u001B[2A\u001B[1m<-------------> 0% EXECUTING [5s]\u001B[m\u001B[33D\u001B[2B\u001B[2A\u001B[0K
\u001B[1m> Task :longRunningTask\u001B[m\u001B[0K
First incremental output
\u001B[0K
\u001B[0K
\u001B[2A\u001B[1m<-------------> 0% EXECUTING [6s]\u001B[m\u001B[33D\u001B[1B\u001B[1m> :longRunningTask\u001B[m\u001B[6D\u001B[1B\u001B[2A\u001B[1m<-------------> 0% EXECUTING [7s]\u001B[m\u001B[33D\u001B[2B\u001B[2A\u001B[1m<-------------> 0% EXECUTING [8s]\u001B[m\u001B[33D\u001B[2B\u001B[2ASecond incremental output\u001B[0K
\u001B[1B\u001B[0K
\u001B[2A\u001B[1m<-------------> 0% EXECUTING [8s]\u001B[m\u001B[33D\u001B[1B\u001B[1m> :longRunningTask\u001B[m\u001B[6D\u001B[1B\u001B[2A\u001B[0K
\u001B[0K
\u001B[32;1mBUILD SUCCESSFUL\u001B[0;39m in 9s
1 actionable task: 1 executed
\u001B[2K
"""
        when:
        GroupedOutputFixture fixture = new GroupedOutputFixture(consoleOutput)

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
        GroupedOutputFixture groupedOutput = new GroupedOutputFixture(consoleOutput)

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
        GroupedOutputFixture groupedOutput = new GroupedOutputFixture(consoleOutput)

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
        GroupedOutputFixture groupedOutput = new GroupedOutputFixture(consoleOutput)

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
        GroupedOutputFixture groupedOutput = new GroupedOutputFixture(consoleOutput)

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
        GroupedOutputFixture groupedOutput = new GroupedOutputFixture(consoleOutput)

        when:
        groupedOutput.task(':doesNotExist')

        then:
        def t = thrown(AssertionError)
        t.message == "The grouped output for task ':doesNotExist' could not be found"
    }

    def "handles output right before end of failed build"() {
        given:
        def consoleOutput = """
\u001B[2A\u001B[1m<-------------> 0% INITIALIZING [0s]\u001B[m\u001B[36D\u001B[1B\u001B[1m> settings\u001B[m\u001B[10D\u001B[1B\u001B[1A\u001B[90m> IDLE\u001B[39m\u001B[0K\u001B[6D\u001B[1B\u001B[2A\u001B[1m<-------------> 0% CONFIGURING [0s]\u001B[m\u001B[0K\u001B[35D\u001B[2B\u001B[2AParallel execution is an incubating feature.\u001B[0K
\u001B[1B\u001B[0K
\u001B[2A\u001B[1m<-------------> 0% CONFIGURING [0s]\u001B[m\u001B[35D\u001B[1B\u001B[1m> root project\u001B[m\u001B[14D\u001B[1B\u001B[2A\u001B[1m<-------------> 0% EXECUTING [0s]\u001B[m\u001B[0K\u001B[33D\u001B[1B\u001B[90m> IDLE\u001B[39m\u001B[0K\u001B[6D\u001B[1B\u001B[2A\u001B[0K
\u001B[1m> Task :log\u001B[m\u001B[0K
Output from :log


\u001B[31mFAILURE: \u001B[39m\u001B[31mBuild failed with an exception.\u001B[39m

* Where:
Build file '/Users/daniel/gradle/gradle/build/tmp/test files/BasicGroupedTaskLoggingFunctionalSpec/test/9k46f/build.gradle' line: 58

* What went wrong:
Execution failed for task ':log'.
\u001B[33m> \u001B[39mjava.lang.Exception:

* Try:
Run with \u001B[1m--info\u001B[m or \u001B[1m--debug\u001B[m option to get more log output.

* Exception is:
org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':log'.
        at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.executeActions(ExecuteActionsTaskExecuter.java:100)
        at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.execute(ExecuteActionsTaskExecuter.java:70)
        at org.gradle.api.internal.tasks.execution.SkipUpToDateTaskExecuter.execute(SkipUpToDateTaskExecuter.java:61)
        at org.gradle.api.internal.tasks.execution.ResolveTaskOutputCachingStateExecuter.execute(ResolveTaskOutputCachingStateExecuter.java:54)
        at org.gradle.api.internal.tasks.execution.ValidatingTaskExecuter.execute(ValidatingTaskExecuter.java:58)
        at org.gradle.api.internal.tasks.execution.SkipEmptySourceFilesTaskExecuter.execute(SkipEmptySourceFilesTaskExecuter.java:88)
        at org.gradle.api.internal.tasks.execution.ResolveTaskArtifactStateTaskExecuter.execute(ResolveTaskArtifactStateTaskExecuter.java:52)
        at org.gradle.api.internal.tasks.execution.SkipTaskWithNoActionsExecuter.execute(SkipTaskWithNoActionsExecuter.java:52)
        at org.gradle.api.internal.tasks.execution.SkipOnlyIfTaskExecuter.execute(SkipOnlyIfTaskExecuter.java:54)
        at org.gradle.api.internal.tasks.execution.ExecuteAtMostOnceTaskExecuter.execute(ExecuteAtMostOnceTaskExecuter.java:43)
        at org.gradle.api.internal.tasks.execution.CatchExceptionTaskExecuter.execute(CatchExceptionTaskExecuter.java:34)
        ... 42 more


\u001B[31;1mBUILD FAILED\u001B[0;39m in 1s
1 actionable task: 1 executed
\u001B[2K
"""
        when:
        GroupedOutputFixture groupedOutput = new GroupedOutputFixture(consoleOutput)

        then:
        groupedOutput.task(':log').output == 'Output from :log'
    }
}
