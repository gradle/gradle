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

package org.gradle.testkit.runner.internal

import org.gradle.api.GradleException
import org.gradle.testkit.runner.BuildResult
import org.gradle.util.TextUtil
import spock.lang.Specification
import spock.lang.Unroll

class DefaultGradleRunnerTest extends Specification {
    DefaultGradleRunner defaultGradleRunner = new DefaultGradleRunner(Mock(File))
    File workingDir = new File('my/tests')
    List<String> arguments = ['compile', 'test', '--parallel', '-Pfoo=bar']

    def setup() {
        defaultGradleRunner.withProjectDir(workingDir).withArguments(arguments)
    }

    def "creates diagnostic message for execution result without thrown exception"() {
        given:
        GradleExecutionResult gradleExecutionResult = createGradleExecutionResult()

        when:
        String message = defaultGradleRunner.createDiagnosticsMessage('Gradle build executed', gradleExecutionResult)

        then:
        TextUtil.normaliseLineSeparators(message) == basicDiagnosticsMessage
    }

    @Unroll
    def "creates diagnostic message for execution result for thrown #description"() {
        given:
        GradleExecutionResult gradleExecutionResult = createGradleExecutionResult(exception)

        when:
        String message = defaultGradleRunner.createDiagnosticsMessage('Gradle build executed', gradleExecutionResult)

        then:
        TextUtil.normaliseLineSeparators(message) == """$basicDiagnosticsMessage
Reason:
$expectedReason
-----"""

        where:
        exception                                                                                                                     | expectedReason                | description
        new RuntimeException('Something went wrong')                                                                                  | 'Something went wrong'        | 'exception having no parent cause'
        new RuntimeException('Something went wrong', new GradleException('Unknown command line option'))                              | 'Unknown command line option' | 'exception having single parent cause'
        new RuntimeException('Something went wrong', new GradleException('Unknown command line option', new Exception('Total fail'))) | 'Total fail'                  | 'exception having multiple parent causes'
    }

    private GradleExecutionResult createGradleExecutionResult(Throwable throwable = null) {
        ByteArrayOutputStream standardOutput = new ByteArrayOutputStream()
        standardOutput.write('This is some output'.bytes)
        ByteArrayOutputStream standardError = new ByteArrayOutputStream()
        standardError.write('This is some error'.bytes)
        List<BuildResult> tasks = new ArrayList<BuildResult>();
        new GradleExecutionResult(standardOutput, standardError, tasks, throwable)
    }

    private String getBasicDiagnosticsMessage() {
        """Gradle build executed in $workingDir.absolutePath with arguments $arguments

Output:
This is some output
-----
Error:
This is some error
-----"""
    }
}
