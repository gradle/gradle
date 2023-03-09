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

import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.util.internal.RedirectStdOutAndErr
import org.junit.Rule
import spock.lang.Specification

@Requires(IntegTestPreconditions.IsEmbeddedExecutor)
class InProcessGradleExecuterIntegrationTest extends Specification {
    @Rule
    RedirectStdOutAndErr outputs = new RedirectStdOutAndErr()
    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())
    def distribution = new UnderDevelopmentGradleDistribution(IntegrationTestBuildContext.INSTANCE)
    def executer = new GradleContextualExecuter(distribution, temporaryFolder, IntegrationTestBuildContext.INSTANCE)

    @ToBeFixedForConfigurationCache
    def "can write to System.out and System.err around build invocation with #console console when errors are redirected to stdout"() {
        given:
        temporaryFolder.file("settings.gradle") << '''
            // Use System.out and System.err in the build
            println("settings out 1")
            System.out.println("settings out 2")
            System.err.println("settings err 1")
        '''

        when:
        System.out.println("BEFORE OUT")
        System.err.println("BEFORE ERR")

        def result1 = executer
            .inDirectory(temporaryFolder.testDirectory)
            .withTasks("help")
            .withTestConsoleAttached()
            .withConsole(console)
            .run()

        System.out.println("AFTER OUT")
        System.err.println("AFTER ERR")

        def result2 = executer
            .inDirectory(temporaryFolder.testDirectory)
            .withTasks("help")
            .withTestConsoleAttached()
            .withConsole(console)
            .run()

        then:
        [result1, result2].each {
            it.assertNotOutput("BEFORE")
            it.assertNotOutput("AFTER")
            it.assertOutputContains("settings out 1")
            it.assertOutputContains("settings out 2")
            it.assertHasErrorOutput("settings err 1")
        }

        and:
        outputs.stdOut.contains("BEFORE OUT")
        stripped(outputs.stdOut).contains(result1.output)
        stripped(outputs.stdOut).contains(result2.output)
        outputs.stdOut.contains("AFTER OUT")

        and:
        outputs.stdErr.contains("BEFORE ERR")
        stripped(outputs.stdOut).contains(result1.error)
        stripped(outputs.stdOut).contains(result2.error)
        outputs.stdErr.contains("AFTER ERR")

        where:
        console << [ConsoleOutput.Plain, ConsoleOutput.Rich, ConsoleOutput.Verbose]
    }

    @ToBeFixedForConfigurationCache
    def "can write to System.out and System.err around build invocation with #console console when errors are written to stderr"() {
        given:
        temporaryFolder.file("settings.gradle") << '''
            // Use System.out and System.err in the build
            println("settings out 1")
            System.out.println("settings out 2")
            System.err.println("settings err 1")
        '''

        when:
        System.out.println("BEFORE OUT")
        System.err.println("BEFORE ERR")

        def result1 = executer
            .inDirectory(temporaryFolder.testDirectory)
            .withTasks("help")
            .withTestConsoleAttached()
            .withConsole(console)
            .run()

        System.out.println("AFTER OUT")
        System.err.println("AFTER ERR")

        def result2 = executer
            .inDirectory(temporaryFolder.testDirectory)
            .withTasks("help")
            .withTestConsoleAttached()
            .withConsole(console)
            .run()

        then:
        [result1, result2].each {
            it.assertNotOutput("BEFORE")
            it.assertNotOutput("AFTER")
            it.assertOutputContains("settings out 1")
            it.assertOutputContains("settings out 2")
            it.assertHasErrorOutput("settings err 1")
        }

        and:
        outputs.stdOut.contains("BEFORE OUT")
        stripped(outputs.stdOut).contains(result1.output)
        stripped(outputs.stdOut).contains(result2.output)
        outputs.stdOut.contains("AFTER OUT")

        and:
        outputs.stdErr.contains("BEFORE ERR")
        stripped(outputs.stdErr).contains(result1.error)
        stripped(outputs.stdErr).contains(result2.error)
        outputs.stdErr.contains("AFTER ERR")

        where:
        console << [ConsoleOutput.Plain, ConsoleOutput.Rich, ConsoleOutput.Verbose]
    }

    def stripped(String output) {
        return LogContent.of(output).withNormalizedEol()
    }

    def cleanup() {
        executer.cleanup()
    }
}
