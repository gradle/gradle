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

package org.gradle.testkit.runner

import org.gradle.testkit.runner.fixtures.GradleRunnerCoverage
import org.gradle.testkit.runner.fixtures.IgnoreTarget

class GradleRunnerCaptureOutputIntegrationTest extends AbstractGradleRunnerIntegrationTest {

    def "can specify System.out and System.err as output"() {
        given:
        Writer standardOutput = new OutputStreamWriter(System.out)
        Writer standardError = new OutputStreamWriter(System.err)
        buildFile << helloWorldWithStandardOutputAndError()

        when:
        GradleRunner gradleRunner = runner('helloWorld')
        gradleRunner.withStandardOutput(standardOutput)
        gradleRunner.withStandardError(standardError)
        BuildResult result = gradleRunner.build()

        then:
        noExceptionThrown()
        result.standardOutput.contains(':helloWorld')
        result.standardOutput.contains('Hello world!')
        result.standardError.contains('Some failure')
    }

    def "build result standard output and error capture the same output as output provided by user"() {
        given:
        Writer standardOutput = new StringWriter()
        Writer standardError = new StringWriter()
        buildFile << helloWorldWithStandardOutputAndError()

        when:
        GradleRunner gradleRunner = runner('helloWorld')
        gradleRunner.withStandardOutput(standardOutput)
        gradleRunner.withStandardError(standardError)
        BuildResult result = gradleRunner.build()

        then:
        noExceptionThrown()
        result.standardOutput.contains(':helloWorld')
        result.standardOutput.contains('Hello world!')
        result.standardError.contains('Some failure')
        result.standardOutput == standardOutput.toString()
        result.standardError == standardError.toString()

        cleanup:
        standardOutput.close()
        standardError.close()
    }

    def "output is captured if unexpected build exception is thrown"() {
        given:
        Writer standardOutput = new StringWriter()
        Writer standardError = new StringWriter()
        buildFile << helloWorldWithStandardOutputAndError()

        when:
        GradleRunner gradleRunner = runner('helloWorld')
        gradleRunner.withStandardOutput(standardOutput)
        gradleRunner.withStandardError(standardError)
        gradleRunner.buildAndFail()

        then:
        UnexpectedBuildSuccess t = thrown UnexpectedBuildSuccess
        BuildResult result = t.buildResult
        result.standardOutput.contains(':helloWorld')
        result.standardOutput.contains('Hello world!')
        result.standardError.contains('Some failure')
        result.standardOutput == standardOutput.toString()
        result.standardError == standardError.toString()

        cleanup:
        standardOutput.close()
        standardError.close()
    }

    @IgnoreTarget({ GradleRunnerCoverage.DEBUG })
    def "output is captured if mechanical failure occurs"() {
        given:
        Writer standardOutput = new StringWriter()
        Writer standardError = new StringWriter()
        buildFile << """
            task helloWorld {
                doLast {
                    println 'Hello world!'
                    System.err.println 'Some failure'
                    Runtime.runtime.halt(0)
                    println 'Bye world!'
                }
            }
        """

        when:
        GradleRunner gradleRunner = runner('helloWorld')
        gradleRunner.withStandardOutput(standardOutput)
        gradleRunner.withStandardError(standardError)
        gradleRunner.build()

        then:
        UnexpectedBuildFailure t = thrown UnexpectedBuildFailure
        BuildResult result = t.buildResult
        result.standardOutput.contains(':helloWorld')
        result.standardOutput.contains('Hello world!')
        !result.standardOutput.contains('Bye world!')
        result.standardError.contains('Some failure')
        result.standardOutput == standardOutput.toString()
        result.standardError == standardError.toString()

        cleanup:
        standardOutput.close()
        standardError.close()
    }

    private String helloWorldWithStandardOutputAndError() {
        """
            task helloWorld {
                doLast {
                    println 'Hello world!'
                    System.err.println 'Some failure'
                }
            }
        """
    }
}
