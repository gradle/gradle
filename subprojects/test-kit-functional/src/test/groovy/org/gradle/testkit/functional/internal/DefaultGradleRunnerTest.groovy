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

package org.gradle.testkit.functional.internal

import spock.lang.Specification

class DefaultGradleRunnerTest extends Specification {
    DefaultGradleRunner defaultGradleRunner = new DefaultGradleRunner()
    File workingDir = new File('my/tests')
    List<String> tasks = ['compile', 'test']
    List<String> arguments = ['--parallel', '-Pfoo=bar']

    def setup() {
        defaultGradleRunner.setWorkingDir(workingDir)
        defaultGradleRunner.setTasks(tasks)
        defaultGradleRunner.setArguments(arguments)
    }

    def "creates diagnostic message for execution result"() {
        given:
        ByteArrayOutputStream standardOutput = new ByteArrayOutputStream()
        standardOutput.write('This is some output'.bytes)
        ByteArrayOutputStream standardError = new ByteArrayOutputStream()
        standardError.write('This is some error'.bytes)
        GradleExecutionResult gradleExecutionResult = new GradleExecutionResult(standardOutput, standardError)

        when:
        String message = defaultGradleRunner.createDiagnosticsMessage("Gradle build executed", gradleExecutionResult)

        then:
        message.replaceAll('\\r', '') == """Gradle build executed in $workingDir.absolutePath with tasks $tasks and arguments $arguments

Output:
This is some output
-----
Error:
This is some error
-----"""
    }
}
