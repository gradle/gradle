/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.logging

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.UsesSample
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.test.fixtures.file.TestFile
import org.junit.Rule

class LoggingIntegrationTest extends AbstractIntegrationSpec {

    @Rule public final TestResources resources = new TestResources(testDirectoryProvider)
    @Rule public final Sample sampleResources = new Sample(testDirectoryProvider)

    final LogOutput logOutput = new LogOutput() {{
        quiet(
                'An info log message which is always logged.',
                'A message which is logged at QUIET level',
                'Text which is logged at QUIET level',
                'A task message which is logged at QUIET level',
                'quietProject2ScriptClassPathOut',
                'quietProject2CallbackOut',
                'settings quiet out',
                'init : QUIET out',
                'init :buildSrc QUIET out',
                'init callback quiet out',
                'main buildSrc quiet',
                'nestedBuild buildSrc quiet',
                'nestedBuild quiet',
                'nestedBuild task quiet',
                'external QUIET message')
        error(
                'An error log message.',
                'An error message which is logged at ERROR level',
                'external ERROR error message',
                '[ant:echo] An error message logged from Ant',
                'A severe log message logged using JUL',
                'init : ERROR err',
                'init :buildSrc ERROR err'
        )
        warning(
                'A warning log message.',
                'A task error message which is logged at WARN level',
                '[ant:echo] A warn message logged from Ant',
                'A warning log message logged using JUL'
        )
        lifecycle(
                'A lifecycle info log message.',
                'An error message which is logged at LIFECYCLE level',
                'A task message which is logged at LIFECYCLE level',
                'settings lifecycle log',
                'init : lifecycle log',
                'init :buildSrc lifecycle log',
                'external LIFECYCLE error message',
                'external LIFECYCLE log message',
                'LOGGER: evaluating :',
                'LOGGER: evaluating :project1',
                'LOGGER: evaluating :project2',
                'LOGGER: executing :project1:logInfo',
                'LOGGER: executing :project1:logLifecycle',
                'LOGGER: executing :project1:nestedBuildLog',
                'LOGGER: executing :project1:log'
        )
        info(
                'An info log message.',
                'A message which is logged at INFO level',
                'Text which is logged at INFO level',
                'A task message which is logged at INFO level',
                '[ant:echo] An info message logged from Ant',
                'An info log message logged using SLF4j',
                'An info log message logged using JCL',
                'An info log message logged using Log4j',
                'An info log message logged using JUL',
                'A config log message logged using JUL',
                'infoProject2Out',
                'infoProject2ScriptClassPathOut',
                'settings info out',
                'settings info log',
                'init : INFO out',
                'init :buildSrc INFO out',
                'init :buildSrc INFO err',
                'init :buildSrc info log',
                'LOGGER: build finished',
                'LOGGER: evaluated project :',
                'LOGGER: evaluated project :project1',
                'LOGGER: evaluated project :project2',
                'LOGGER: executed task :project1:log',
                'LOGGER: executed task :project1:logInfo',
                'LOGGER: executed task :project1:logLifecycle',
                'LOGGER: task :project1:log starting work',
                'LOGGER: task :project1:log completed work',
                'main buildSrc info',
                'nestedBuild buildSrc info',
                'nestedBuild info',
                'external INFO message'
        )
        debug(
                'A debug log message.',
                '[ant:echo] A debug message logged from Ant',
                'A fine log message logged using JUL'
        )
        trace(
                'A trace log message.'
        )
        forbidden(
                // the default message generated by JUL
                'INFO: An info log message logged using JUL',
                // the custom logger should override this
                'BUILD SUCCESSFUL'
        )
    }}

    final LogOutput sample = new LogOutput() {{
        error('An error log message.')
        quiet('An info log message which is always logged.')
        quiet('A message which is logged at QUIET level')
        warning('A warning log message.')
        lifecycle('A lifecycle info log message.')
        info('An info log message.')
        info('A message which is logged at INFO level')
        info('A task message which is logged at INFO level')
        info('An info log message logged using SLF4j')
        debug('A debug log message.')
        forbidden('A trace log message.')
    }}

    final LogOutput multiThreaded = new LogOutput() {{
        (1..10).each { thread ->
            (1..100).each { iteration ->
                lifecycle("log message from thread $thread iteration $iteration")
                quiet("stdout message from thread $thread iteration $iteration")
                quiet("styled text message from thread $thread iteration $iteration")
            }
        }
    }}

    @ToBeFixedForConfigurationCache(because = "https://github.com/gradle/gradle/issues/25483")
    def "build emits #level logging"() {
        LogLevel logLevel = logOutput."$level"
        resources.maybeCopy('LoggingIntegrationTest/logging')
        TestFile loggingDir = testDirectory
        loggingDir.file("buildSrc/build/.gradle").deleteDir()
        loggingDir.file("nestedBuild/buildSrc/.gradle").deleteDir()

        String initScript = new File(loggingDir, 'init.gradle').absolutePath
        List<String> allArgs = logLevel.args + ['-I', initScript]

        when:
        executer.noExtraLogging().inDirectory(loggingDir).withArguments(allArgs)
        run "log"
        then:
        logLevel.checkOuts(result)

        where:
        level << ['quiet', 'lifecycle', 'info', 'debug']
    }

    @ToBeFixedForConfigurationCache(because = "https://github.com/gradle/gradle/issues/25483", iterationMatchers = 'sample emits (quiet|lifecycle) logging')
    @UsesSample('tutorial/logging/groovy')
    def "sample emits #level logging"() {
        LogLevel logLevel = sample."$level"

        when:
        executer.noExtraLogging().inDirectory(sampleResources.dir).withArguments(logLevel.args)
        run 'log'
        then:
        logLevel.checkOuts(result)

        where:
        level << ['quiet', 'lifecycle', 'info', 'debug']
    }

    def "multi threaded #level logging works"() {
        LogLevel logLevel = multiThreaded."$level"
        resources.maybeCopy('LoggingIntegrationTest/multiThreaded')

        when:
        executer.noExtraLogging().withArguments(logLevel.args)
        run 'log'
        then:
        logLevel.checkOuts(result)

        where:
        level << ['quiet', 'lifecycle', 'info', 'debug']
    }
}

class LogLevel {
    List<String> args
    List<String> infoMessages
    List<String> errorMessages
    List<String> allMessages
    Closure validator = {OutputOccurrence occurrence ->
        occurrence.assertIsAtEndOfLine()
        occurrence.assertIsAtStartOfLine()
    }

    def getForbiddenMessages() {
        allMessages - (infoMessages + errorMessages)
    }

    void checkOuts(ExecutionResult result) {
        infoMessages.each {List<String> messages ->
            checkOuts(true, result.output, messages, validator)
        }
        errorMessages.each {List<String> messages ->
            checkOuts(true, result.error, messages, validator)
        }
        forbiddenMessages.each {List<String> messages ->
            checkOuts(false, result.output, messages) {occurrence -> }
            checkOuts(false, result.error, messages) {occurrence -> }
        }
    }

    def checkOuts(boolean shouldContain, String result, List<String> outs, Closure validator) {
        outs.each {String expectedOut ->
            def filters = outs.findAll { other -> other != expectedOut && other.startsWith(expectedOut) }

            // Find all locations of the expected string in the output
            List<Integer> matches = []
            int pos = 0;
            while (pos < result.length()) {
                int match = result.indexOf(expectedOut, pos)
                if (match < 0) {
                    break
                }

                // Filter matches with other expected strings that have this string as a prefix
                boolean filter = filters.find { other -> result.substring(match).startsWith(other)} != null
                if (!filter) {
                    matches << match
                }
                pos = match + expectedOut.length()
            }

            // Check we found the expected number of occurrences of the expected string
            if (!shouldContain) {
                if (!matches.empty) {
                    throw new AssertionError("Found unexpected content '$expectedOut' in output:\n$result")
                }
            } else {
                if (matches.empty) {
                    throw new AssertionError("Could not find expected content '$expectedOut' in output:\n$result")
                }
                if (matches.size() > 1) {
                    throw new AssertionError("Expected content '$expectedOut' should occur exactly once but found ${matches.size()} times in output:\n$result")
                }

                // Validate each occurrence
                matches.each {
                    validator.call(new OutputOccurrence(expectedOut, result, it))
                }
            }
        }
    }
}

class LogOutput {
    final List<String> quietMessages = []
    final List<String> errorMessages = []
    final List<String> warningMessages = []
    final List<String> lifecycleMessages = []
    final List<String> infoMessages = []
    final List<String> debugMessages = []
    final List<String> traceMessages = []
    final List<String> forbiddenMessages = []
    final List<String> allOuts = [
            errorMessages,
            quietMessages,
            warningMessages,
            lifecycleMessages,
            infoMessages,
            debugMessages,
            traceMessages,
            forbiddenMessages
    ]

    def quiet(String... msgs) {
        quietMessages.addAll(msgs)
    }
    def error(String... msgs) {
        errorMessages.addAll(msgs)
    }
    def warning(String... msgs) {
        warningMessages.addAll(msgs)
    }
    def lifecycle(String... msgs) {
        warningMessages.addAll(msgs)
    }
    def info(String... msgs) {
        infoMessages.addAll(msgs)
    }
    def debug(String... msgs) {
        debugMessages.addAll(msgs)
    }
    def trace(String... msgs) {
        traceMessages.addAll(msgs)
    }
    def forbidden(String... msgs) {
        forbiddenMessages.addAll(msgs)
    }

    final LogLevel quiet = new LogLevel(
            args: ['-q'],
            infoMessages: [quietMessages],
            errorMessages: [errorMessages],
            allMessages: allOuts
    )
    final LogLevel lifecycle = new LogLevel(
            args: [],
            infoMessages: [quietMessages, warningMessages, lifecycleMessages],
            errorMessages: [errorMessages],
            allMessages: allOuts
    )
    final LogLevel info = new LogLevel(
            args: ['-i'],
            infoMessages: [quietMessages, warningMessages, lifecycleMessages, infoMessages],
            errorMessages: [errorMessages],
            allMessages: allOuts
    )
    final LogLevel debug = new LogLevel(
            args: ['-d'],
            infoMessages: [quietMessages, warningMessages, lifecycleMessages, infoMessages, debugMessages],
            errorMessages: [errorMessages],
            allMessages: allOuts,
            validator: {OutputOccurrence occurrence ->
                occurrence.assertIsAtEndOfLine()
                occurrence.assertHasPrefix(/.+ \[.+\] \[.+\] /)
            }
    )
}

class OutputOccurrence {
    final String expected
    final String actual
    final int index

    OutputOccurrence(String expected, String actual, int index) {
        this.expected = expected
        this.actual = actual
        this.index = index
    }

    void assertIsAtStartOfLine() {
        if (index == 0) {
            return
        }
        int startLine = index - 1
        if (startLine < 0 || !actual.substring(startLine).startsWith('\n')) {
            throw new AssertionError("Expected content '$expected' is not at the start of a line in output $actual.")
        }
    }

    void assertIsAtEndOfLine() {
        int endLine = index + expected.length()
        if (endLine == actual.length()) {
            return
        }
        if (!actual.substring(endLine).startsWith('\n')) {
            throw new AssertionError("Expected content '$expected' is not at the end of a line in output $actual.")
        }
    }

    void assertHasPrefix(String pattern) {
        int startLine = actual.lastIndexOf('\n', index)
        if (startLine < 0) {
            startLine = 0
        } else {
            startLine += 1
        }

        String actualPrefix = actual.substring(startLine, index)
        assert actualPrefix.matches(pattern): "Unexpected prefix '$actualPrefix' found for line containing content '$expected' in output $actual"
    }
}
