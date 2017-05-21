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
        def consoleOutput = """
Condition not satisfied:

result.groupedOutput.taskCount == 3
|      |             |         |
|      |             2         false
|      org.gradle.integtests.fixtures.logging.GroupedOutputFixture@1a0066b
org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult@6d5e1075

    at org.gradle.internal.logging.BasicGroupedTaskLoggingFunctionalSpec.multi-project build tasks logs are grouped(BasicGroupedTaskLoggingFunctionalSpec.groovy:38)

------- Stdout: -------
Starting build with: /home/tcagent2/agent/work/4b92f910977a653d/build/integ test/bin/gradle --no-daemon --stacktrace --gradle-user-home /home/tcagent2/agent/work/4b92f910977a653d/intTestHomeDir/worker-1 --console=rich log --stacktrace
Working directory: /home/tcagent2/agent/work/4b92f910977a653d/subprojects/logging/build/tmp/test files/BasicGroupedTaskLoggingFunctionalSpec/multi_project_build...e_grouped/e4mj6
Environment vars:
    JAVA_HOME: /opt/files/jdk-linux/jigsaw-jdk-9-ea+131_linux-x64_bin.tar.gz
    GRADLE_HOME: 
    GRADLE_USER_HOME: null
    JAVA_OPTS: 
    GRADLE_OPTS: -Dorg.gradle.daemon.idletimeout=120000 -Dorg.gradle.daemon.registry.base=/home/tcagent2/agent/work/4b92f910977a653d/build/daemon -Dorg.gradle.native.dir=/home/tcagent2/agent/work/4b92f910977a653d/intTestHomeDir/worker-1/native -Dorg.gradle.deprecation.trace=true -Djava.io.tmpdir=/home/tcagent2/agent/work/4b92f910977a653d/subprojects/logging/build/tmp -Dfile.encoding=UTF-8 -Dorg.gradle.classloaderscope.strict=true -ea -ea

 [1A [1m<-------------> 0% INITIALIZING [1s] [m [36D [1B
 [1A [1m<-------------> 0% INITIALIZING [2s] [m [36D [1B
 [1A [1m<-------------> 0% INITIALIZING [3s] [m [36D [1B
 [1A [1m<-------------> 0% INITIALIZING [4s] [m [36D [1B
 [1A [1m<-------------> 0% INITIALIZING [5s] [m [36D [1B
 [1A [1m<-------------> 0% CONFIGURING [5s] [m [0K [35D [1B
 [1A [1m<-------------> 0% CONFIGURING [6s] [m [35D [1B
 [1A [1m<=============> 100% CONFIGURING [6s] [m [37D [1B
 [1A [1m<=============> 100% CONFIGURING [7s] [m [37D [1B
 [1A [1m<-------------> 0% EXECUTING [7s] [m [0K [33D [1B
 [1A [1m> Task :1:log [m [0K
Output from 1

 [0K
 [1A [1m<====---------> 33% EXECUTING [7s] [m [34D [1B
 [1A [1m> Task :2:log [m [0K
Output from 2

 [1m> Task :3:log [m
Output from 3


 [32;1mBUILD SUCCESSFUL [0;39m in 8s
3 actionable tasks: 3 executed
 [2K
------- Stderr: -------
SLF4J: Class path contains multiple SLF4J bindings.
SLF4J: Found binding in [jar:file:/home/tcagent2/agent/work/4b92f910977a653d/subprojects/logging/build/libs/gradle-logging-4.0.jar!/org/slf4j/impl/StaticLoggerBinder.class]
SLF4J: Found binding in [file:/home/tcagent2/agent/work/4b92f910977a653d/subprojects/logging/build/classes/java/main/org/slf4j/impl/StaticLoggerBinder.class]
SLF4J: See http://www.slf4j.org/codes.html#multiple_bindings for an explanation.
SLF4J: Actual binding is of type [org.gradle.internal.logging.slf4j.OutputEventListenerBackedLoggerContext]
(?ms)> Task (:[\\w:]*)[^\\n]*\\n(.*?(?=\\n\\n(?:[^\\n]*?> Task (:[\\w:]*)[^\\n]*\\n|\\n[^\\n]*?BUILD SUCCESSFUL|\\n[^\\n]*?FAILURE:| \\[0K\\n \\[1A \\[1m<)))
"""
        when:
        GroupedOutputFixture groupedOutput = new GroupedOutputFixture(consoleOutput)

        then:
        groupedOutput.taskCount == 3
        groupedOutput.task(':1:log').output == 'Output from 1'
        groupedOutput.task(':2:log').output == 'Output from 2'
    }
}
