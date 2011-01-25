/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.integtests

import junit.framework.AssertionFailedError
import org.gradle.integtests.fixtures.ExecutionResult
import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.GradleDistributionExecuter
import org.gradle.util.TestFile
import org.junit.Rule
import org.junit.Test
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample

/**
 * @author Hans Dockter
 */
class LoggingIntegrationTest {
    @Rule public final GradleDistribution dist = new GradleDistribution()
    @Rule public final GradleDistributionExecuter executer = new GradleDistributionExecuter()
    @Rule public final TestResources resources = new TestResources()
    @Rule public final Sample sampleResources = new Sample()

    private final LogOutput logOutput = new LogOutput() {{
        quiet(
                'An info log message which is always logged.',
                'A message which is logged at QUIET level',
                'Text which is logged at QUIET level',
                'A task message which is logged at QUIET level',
                'quietProject2ScriptClassPathOut',
                'quietProject2CallbackOut',
                'settings quiet out',
                'init QUIET out',
                'init callback quiet out',
                'buildSrc quiet',
                'nestedBuild/buildSrc quiet',
                'nestedBuild quiet',
                'nestedBuild task quiet',
                'external QUIET message')
        error(
                'An error log message.',
                'An error message which is logged at ERROR level',
                'external ERROR error message',
                '[ant:echo] An error message logged from Ant',
                'A severe log message logged using JUL',
                'init ERROR err'
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
                'init lifecycle log',
                'external LIFECYCLE error message',
                'external LIFECYCLE log message',
                'LOGGER: evaluating :',
                'LOGGER: evaluating :project1',
                'LOGGER: evaluating :project2',
                'LOGGER: executing :project1:logInfo',
                'LOGGER: executing :project1:logLifecycle',
                'LOGGER: executing :project1:nestedBuildLog',
                'LOGGER: executing :project1:log',
                ':buildSrc:classes',
                ':nestedBuild:log'
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
                'init INFO out',
                'init INFO err',
                'init info log',
                'LOGGER: build finished',
                'LOGGER: evaluated project',
                'LOGGER: executed task',
                'LOGGER: task starting work',
                'LOGGER: task completed work',
                'buildSrc info',
                'nestedBuild/buildSrc info',
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

    private final LogOutput sample = new LogOutput() {{
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

    private final LogOutput multiThreaded = new LogOutput() {{
        (1..10).each { thread ->
            (1..100).each { iteration ->
                lifecycle("log message from thread $thread iteration $iteration")
                quiet("stdout message from thread $thread iteration $iteration")
                quiet("styled text message from thread $thread iteration $iteration")
            }
        }
    }}

    @Test
    public void quietLogging() {
        checkOutput(this.&run, logOutput.quiet)
    }

    @Test
    public void lifecycleLogging() {
        checkOutput(this.&run, logOutput.lifecycle)
    }

    @Test
    public void infoLogging() {
        checkOutput(this.&run, logOutput.info)
    }

    @Test
    public void debugLogging() {
        checkOutput(this.&run, logOutput.debug)
    }

    @Test @UsesSample('userguide/tutorial/logging')
    public void sampleQuietLogging() {
        checkOutput(this.&runSample, sample.quiet)
    }

    @Test @UsesSample('userguide/tutorial/logging')
    public void sampleLifecycleLogging() {
        checkOutput(this.&runSample, sample.lifecycle)
    }

    @Test @UsesSample('userguide/tutorial/logging')
    public void sampleInfoLogging() {
        checkOutput(this.&runSample, sample.info)
    }

    @Test @UsesSample('userguide/tutorial/logging')
    public void sampleDebugLogging() {
        checkOutput(this.&runSample, sample.debug)
    }

    @Test
    public void multiThreadedQuietLogging() {
        checkOutput(this.&runMultiThreaded, multiThreaded.quiet)
    }

    @Test
    public void multiThreadedlifecycleLogging() {
        checkOutput(this.&runMultiThreaded, multiThreaded.lifecycle)
    }

    @Test
    public void multiThreadedDebugLogging() {
        checkOutput(this.&runMultiThreaded, multiThreaded.debug)
    }

    def run(LogLevel level) {
        resources.maybeCopy('LoggingIntegrationTest/logging')
        TestFile loggingDir = dist.testDir
        loggingDir.file("buildSrc/build/.gradle").deleteDir()
        loggingDir.file("nestedBuild/buildSrc/.gradle").deleteDir()

        String initScript = new File(loggingDir, 'init.gradle').absolutePath
        String[] allArgs = level.args + ['-I', initScript]
        return executer.inDirectory(loggingDir).withArguments(allArgs).withTasks('log').run()
    }

    def runMultiThreaded(LogLevel level) {
        resources.maybeCopy('LoggingIntegrationTest/multiThreaded')
        return executer.withArguments(level.args).withTasks('log').run()
    }
    
    def runSample(LogLevel level) {
        return executer.inDirectory(sampleResources.dir).withArguments(level.args).withTasks('log').run()
    }

    void checkOutput(Closure run, LogLevel level) {
        ExecutionResult result = run.call(level)
        level.checkOuts(result)
    }
}

class LogLevel {
    List args
    List infoMessages
    List errorMessages
    List allMessages
    Closure matchPartialLine = {expected, actual -> expected == actual }

    def getForbiddenMessages() {
        allMessages - (infoMessages + errorMessages)
    }

    def checkOuts(ExecutionResult result) {
        infoMessages.each {List messages ->
            checkOuts(true, result.output, messages, matchPartialLine)
        }
        errorMessages.each {List messages ->
            checkOuts(true, result.error, messages, matchPartialLine)
        }
        forbiddenMessages.each {List messages ->
            checkOuts(false, result.output, messages) {expected, actual-> actual.contains(expected)}
            checkOuts(false, result.error, messages) {expected, actual-> actual.contains(expected)}
        }
    }

    def checkOuts(boolean shouldContain, String result, List outs, Closure partialLine) {
        outs.each {String expectedOut ->
            boolean found = result.readLines().find {partialLine.call(expectedOut, it)}
            if (!found && shouldContain) {
                throw new AssertionFailedError("Could not find expected line '$expectedOut' in output:\n$result")
            }
            if (found && !shouldContain) {
                throw new AssertionFailedError("Found unexpected line '$expectedOut' in output:\n$result")
            }
        }
    }
}

class LogOutput {
    final List quietMessages = []
    final List errorMessages = []
    final List warningMessages = []
    final List lifecycleMessages = []
    final List infoMessages = []
    final List debugMessages = []
    final List traceMessages = []
    final List forbiddenMessages = []
    final List allOuts = [
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
            matchPartialLine: {expected, actual -> actual.endsWith(expected) /*&& actual =~ /\[.+?\] \[.+?\] .+/ */}
    )
}