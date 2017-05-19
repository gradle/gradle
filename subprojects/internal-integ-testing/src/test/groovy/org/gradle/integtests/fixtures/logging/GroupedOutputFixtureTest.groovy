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
        def consoleOutput = """EXECUTING [1s]\u001B[m\u001B[0K\u001B[33D\u001B[1B\u001B[1A\u001B[1m> Task :1:log\u001B[m\u001B[0K
Output from 1

\u001B[1m> Task :2:log\u001B[m
Output from 2
More output from 2

\u001B[1m> Task :3:log\u001B[m
Output from 3



Handles lots of newline characters


\u001B[32;1mBUILD SUCCESSFUL\u001B[0;39m in 1s
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
\u001B[1m> Task :longRunningTask\u001B[m
First incremental output

\u001B[1m> Task :longRunningTask\u001B[m
Second incremental output


\u001B[32;1mBUILD SUCCESSFUL\u001B[0;39m in 1s
"""
        when:
        GroupedOutputFixture fixture = new GroupedOutputFixture(consoleOutput)

        then:
        fixture.taskCount == 1
        fixture.task(':longRunningTask').output == 'First incremental output\nSecond incremental output'
    }

    def "parses tasks with progress bar interference"() {
        given:
        def consoleOutput = """EXECUTING [1s]\u001B[m\u001B[0K\u001B[33D\u001B[1B\u001B[1A\u001B[1m
\u001B[1m> Task :1:log\u001B[m\u001B[0K
Output from 1

\u001B[0K
\u001B[1A [1m<====---------> 33% EXECUTING [6s] [m [34D [1B
\u001B[1m> Task :2:log\u001B[m
Output from 2


\u001B[32;1mBUILD SUCCESSFUL\u001B[0;39m in 1s
3 actionable tasks: 3 executed
\u001B[2K
"""
        when:
        GroupedOutputFixture groupedOutput = new GroupedOutputFixture(consoleOutput)

        then:
        groupedOutput.taskCount == 2
        groupedOutput.task(':1:log').output == 'Output from 1'
        groupedOutput.task(':2:log').output == 'Output from 2'
    }
}
