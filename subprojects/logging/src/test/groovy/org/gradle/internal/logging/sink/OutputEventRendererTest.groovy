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


package org.gradle.internal.logging.sink

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.StandardOutputListener
import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.internal.logging.OutputSpecification
import org.gradle.internal.logging.console.ConsoleStub
import org.gradle.internal.logging.events.EndOutputEvent
import org.gradle.internal.logging.events.LogEvent
import org.gradle.internal.logging.events.LogLevelChangeEvent
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.nativeintegration.console.ConsoleMetaData
import org.gradle.internal.operations.BuildOperationCategory
import org.gradle.internal.time.Time
import org.gradle.util.internal.RedirectStdOutAndErr
import org.junit.Rule
import spock.lang.Unroll

class OutputEventRendererTest extends OutputSpecification {
    @Rule
    public final RedirectStdOutAndErr outputs = new RedirectStdOutAndErr()
    private final ConsoleStub console = new ConsoleStub()
    private final ConsoleMetaData metaData = Mock()
    private OutputEventRenderer renderer

    def setup() {
        renderer = new OutputEventRenderer(Time.clock())
        renderer.configure(LogLevel.INFO)
    }

    def rendersLogEventsToStdOut() {
        when:
        renderer.attachSystemOutAndErr()
        renderer.onOutput(event('message', LogLevel.INFO))

        then:
        outputs.stdOut.readLines() == ['message']
        outputs.stdErr == ''
    }

    def rendersErrorLogEventsToStdErr() {
        when:
        renderer.attachSystemOutAndErr()
        renderer.onOutput(event('message', LogLevel.ERROR))

        then:
        outputs.stdOut == ''
        outputs.stdErr.readLines() == ['message']
    }

    def rendersLogEventsToStdOutAndStdErrWhenLogLevelIsDebug() {
        when:
        renderer.configure(LogLevel.DEBUG)
        renderer.attachSystemOutAndErr()
        renderer.onOutput(event(tenAm, 'info', LogLevel.INFO))
        renderer.onOutput(event(tenAm, 'error', LogLevel.ERROR))

        then:
        outputs.stdOut.readLines() == ["${tenAmFormatted} [INFO] [category] info"]
        outputs.stdErr.readLines() == ["${tenAmFormatted} [ERROR] [category] error"]
    }

    def rendersLogEventsToStdOutListener() {
        def listener = new TestListener()

        when:
        renderer.enableUserStandardOutputListeners()
        renderer.addStandardOutputListener(listener)
        renderer.onOutput(event('info', LogLevel.INFO))
        renderer.onOutput(event('error', LogLevel.ERROR))

        then:
        listener.value.readLines() == ['info']
    }

    def doesNotRenderLogEventsToRemovedStdOutListener() {
        def listener = new TestListener()

        when:
        renderer.enableUserStandardOutputListeners()
        renderer.addStandardOutputListener(listener)
        renderer.removeStandardOutputListener(listener)
        renderer.onOutput(event('info', LogLevel.INFO))
        renderer.onOutput(event('error', LogLevel.ERROR))

        then:
        listener.value == ''
    }

    def rendersLogEventsToStdOutListenerWhenLogLevelIsDebug() {
        def listener = new TestListener()

        when:
        renderer.configure(LogLevel.DEBUG)
        renderer.enableUserStandardOutputListeners()
        renderer.addStandardOutputListener(listener)
        renderer.onOutput(event(tenAm, 'message', LogLevel.INFO))

        then:
        listener.value.readLines() == ["${tenAmFormatted} [INFO] [category] message"]
    }

    def rendersErrorLogEventsToStdErrListener() {
        def listener = new TestListener()

        when:
        renderer.enableUserStandardOutputListeners()
        renderer.addStandardErrorListener(listener)
        renderer.onOutput(event('info', LogLevel.INFO))
        renderer.onOutput(event('error', LogLevel.ERROR))

        then:
        listener.value.readLines() == ['error']
    }

    def doesNotRenderLogEventsToRemovedStdErrListener() {
        def listener = new TestListener()

        when:
        renderer.enableUserStandardOutputListeners()
        renderer.addStandardErrorListener(listener)
        renderer.removeStandardErrorListener(listener)
        renderer.onOutput(event('info', LogLevel.INFO))
        renderer.onOutput(event('error', LogLevel.ERROR))

        then:
        listener.value == ''
    }

    def rendersLogEventsToStdErrListenerWhenLogLevelIsDebug() {
        def listener = new TestListener()

        when:
        renderer.configure(LogLevel.DEBUG)
        renderer.enableUserStandardOutputListeners()
        renderer.addStandardErrorListener(listener)
        renderer.onOutput(event(tenAm, 'message', LogLevel.ERROR))

        then:
        listener.value.readLines() == ["${tenAmFormatted} [ERROR] [category] message"]
    }

    def cannotAddStdOutListenerWhenNotEnabled() {
        when:
        renderer.addStandardOutputListener(Stub(StandardOutputListener))

        then:
        thrown(IllegalStateException)

        when:
        renderer.addStandardErrorListener(Stub(StandardOutputListener))

        then:
        thrown(IllegalStateException)
    }

    def forwardsOutputEventsToListener() {
        OutputEventListener listener = Mock()
        LogEvent ignored = event('ignored', LogLevel.DEBUG)
        LogEvent event = event('message', LogLevel.INFO)

        when:
        renderer.configure(LogLevel.INFO)
        renderer.addOutputEventListener(listener)
        renderer.onOutput(ignored)
        renderer.onOutput(event)

        then:
        1 * listener.onOutput({ it instanceof LogLevelChangeEvent && it.newLogLevel == LogLevel.INFO })
        1 * listener.onOutput(event)
        0 * listener._
    }

    @Unroll("forward progress events to listener for #logLevel log level")
    def forwardsProgressEventsToListenerRegardlessOfTheLogLevel() {
        OutputEventListener listener = Mock()
        def start = start('start')
        def progress = progress('progress')
        def complete = complete('complete')

        when:
        renderer.configure(logLevel)
        renderer.addOutputEventListener(listener)
        renderer.onOutput(start)
        renderer.onOutput(progress)
        renderer.onOutput(complete)

        then:
        1 * listener.onOutput({it instanceof LogLevelChangeEvent && it.newLogLevel == logLevel})
        1 * listener.onOutput(start)
        1 * listener.onOutput(progress)
        1 * listener.onOutput(complete)
        0 * listener._

        where:
        logLevel << [LogLevel.ERROR, LogLevel.QUIET, LogLevel.WARN, LogLevel.LIFECYCLE, LogLevel.INFO, LogLevel.DEBUG]
    }

    def doesNotForwardOutputEventsToRemovedListener() {
        OutputEventListener listener = Mock()
        LogEvent event = event('message', LogLevel.INFO)

        when:
        renderer.configure(LogLevel.INFO)
        renderer.addOutputEventListener(listener)
        renderer.removeOutputEventListener(listener)
        renderer.onOutput(event)

        then:
        1 * listener.onOutput({ it instanceof LogLevelChangeEvent && it.newLogLevel == LogLevel.INFO })
        1 * listener.onOutput({ it instanceof EndOutputEvent })
        0 * listener._
    }

    def restoresLogLevelWhenChangedSinceSnapshotWasTaken() {
        def listener = new TestListener()

        given:
        renderer.enableUserStandardOutputListeners()
        renderer.addStandardOutputListener(listener)
        renderer.configure(LogLevel.INFO)
        def snapshot = renderer.snapshot()
        renderer.configure(LogLevel.DEBUG)

        when:
        renderer.restore(snapshot)
        renderer.onOutput(event('info', LogLevel.INFO))
        renderer.onOutput(event('debug', LogLevel.DEBUG))

        then:
        listener.value.readLines() == ['info']
    }

    def rendersProgressEventsToStdOutListeners() {
        def listener = new TestListener()

        when:
        renderer.enableUserStandardOutputListeners()
        renderer.addStandardOutputListener(listener)
        renderer.onOutput(start(loggingHeader: 'description', buildOperationId: 1L, buildOperationCategory: BuildOperationCategory.TASK))
        renderer.onOutput(complete('status'))

        then:
        listener.value.readLines() == ['description status']
    }

    def doesNotRenderProgressEventsToStdoutAndStderr() {
        when:
        renderer.attachSystemOutAndErr()
        renderer.onOutput(start(loggingHeader: 'description', buildOperationId: 1L, buildOperationCategory: BuildOperationCategory.TASK))
        renderer.onOutput(complete('status'))

        then:
        outputs.stdOut == ''
        outputs.stdErr == ''
    }

    def doesNotRendersProgressEventsForLogLevelQuiet() {
        when:
        renderer.attachSystemOutAndErr()
        renderer.configure(LogLevel.QUIET)
        renderer.onOutput(start('description'))
        renderer.onOutput(complete('status'))

        then:
        outputs.stdOut == ''
        outputs.stdErr == ''
    }

    def rendersLogEventsWhenStdOutAndStdErrAreConsole() {
        def snapshot = renderer.snapshot()
        renderer.addRichConsoleWithErrorOutputOnStdout(console, metaData, true)

        when:
        renderer.onOutput(start(description: 'description', buildOperationStart: true, id: 1L, buildOperationId: 1L, buildOperationCategory: BuildOperationCategory.TASK))
        renderer.onOutput(event('info', LogLevel.INFO, 1L))
        renderer.onOutput(event('error', LogLevel.ERROR, 1L))
        renderer.onOutput(complete('status'))
        renderer.restore(snapshot) // close console to flush

        then:
        console.buildOutputArea.toString().readLines() == ['', '{header}> description{info} status{normal}', 'info', '{error}error', '{normal}']
        outputs.stdOut == ''
        outputs.stdErr == ''
    }

    def rendersLogEventsWhenStdOutAndStdErrAreSeparateConsoles() {
        def snapshot = renderer.snapshot()
        def stderrConsole = new ConsoleStub()
        renderer.addRichConsole(console, stderrConsole, metaData, true)

        when:
        renderer.onOutput(start(description: 'description', buildOperationStart: true, id: 1L, buildOperationId: 1L, buildOperationCategory: BuildOperationCategory.TASK))
        renderer.onOutput(event('info', LogLevel.INFO, 1L))
        renderer.onOutput(event('error', LogLevel.ERROR, 1L))
        renderer.onOutput(complete('status'))
        renderer.restore(snapshot) // close console to flush

        then:
        console.buildOutputArea.toString().readLines() == ['', '{header}> description{info} status{normal}', 'info']
        stderrConsole.buildOutputArea.toString().readLines() == ['{error}error', '{normal}']
        outputs.stdOut == ''
        outputs.stdErr == ''
    }

    def rendersLogEventsWhenOnlyStdOutIsConsole() {
        def snapshot = renderer.snapshot()
        renderer.attachSystemOutAndErr()
        renderer.addRichConsole(console, outputs.stdErrPrintStream, metaData, true)

        when:
        renderer.onOutput(start(description: 'description', buildOperationStart: true, id: 1L, buildOperationId: 1L, buildOperationCategory: BuildOperationCategory.TASK))
        renderer.onOutput(event('info', LogLevel.INFO, 1L))
        renderer.onOutput(event('error', LogLevel.ERROR, 1L))
        renderer.onOutput(complete('status'))
        renderer.restore(snapshot) // close console to flush

        then:
        console.buildOutputArea.toString().readLines() == ['', '{header}> description{info} status{normal}', 'info']
        outputs.stdOut == ''
        outputs.stdErr.readLines() == ['error']
    }

    def rendersLogEventsWhenOnlyStdErrIsConsole() {
        def snapshot = renderer.snapshot()
        renderer.attachSystemOutAndErr()
        renderer.addRichConsole(outputs.stdOutPrintStream, console, true)

        when:
        renderer.onOutput(start(description: 'description', buildOperationStart: true, id: 1L, buildOperationId: 1L, buildOperationCategory: BuildOperationCategory.TASK))
        renderer.onOutput(event('info', LogLevel.INFO))
        renderer.onOutput(event('error', LogLevel.ERROR))
        renderer.onOutput(complete('status'))
        renderer.restore(snapshot) // close console to flush

        then:
        console.buildOutputArea.toString().readLines() == ['{error}error', '{normal}']
        outputs.stdOut.readLines() == ['info', '> description status']
        outputs.stdErr == ''
    }

    def rendersLogEventsInConsoleWhenLogLevelIsDebug() {
        renderer.configure(LogLevel.DEBUG)
        def snapshot = renderer.snapshot()
        renderer.addRichConsoleWithErrorOutputOnStdout(console, metaData, false)

        when:
        renderer.onOutput(event(tenAm, 'info', LogLevel.INFO))
        renderer.onOutput(event(tenAm, 'error', LogLevel.ERROR))
        renderer.restore(snapshot) // close console to flush

        then:
        console.buildOutputArea.toString().readLines() == ["${tenAmFormatted} [INFO] [category] info",
                                                           "{error}${tenAmFormatted} [ERROR] [category] error",
                                                           '{normal}']
        outputs.stdOut == ''
        outputs.stdErr == ''
    }

    def attachesConsoleWhenStdOutAndStdErrAreAttachedToConsole() {
        when:
        renderer.attachSystemOutAndErr()
        def snapshot = renderer.snapshot()
        renderer.addRichConsoleWithErrorOutputOnStdout(console, metaData, false)
        renderer.onOutput(event('info', LogLevel.INFO))
        renderer.onOutput(event('error', LogLevel.ERROR))
        renderer.restore(snapshot) // close console to flush

        then:
        console.buildOutputArea.toString().readLines() == ['info', '{error}error', '{normal}']
        outputs.stdOut == ''
        outputs.stdErr == ''
    }

    def attachesConsoleWhenOnlyStdOutIsAttachedToConsole() {
        when:
        renderer.attachSystemOutAndErr()
        def snapshot = renderer.snapshot()
        renderer.addRichConsole(console, outputs.stdErrPrintStream, metaData, false)
        renderer.onOutput(event('info', LogLevel.INFO))
        renderer.onOutput(event('error', LogLevel.ERROR))
        renderer.restore(snapshot) // close console to flush

        then:
        console.buildOutputArea.toString().readLines() == ['info']
        outputs.stdOut == ''
        outputs.stdErr.readLines() == ['error']
    }

    def attachesConsoleWhenOnlyStdErrIsAttachedToConsole() {
        when:
        renderer.attachSystemOutAndErr()
        def snapshot = renderer.snapshot()
        renderer.addRichConsole(outputs.stdOutPrintStream, console, false)
        renderer.onOutput(event('info', LogLevel.INFO))
        renderer.onOutput(event('error', LogLevel.ERROR))
        renderer.restore(snapshot) // close console to flush

        then:
        console.buildOutputArea.toString().readLines() == ['{error}error', '{normal}']
        outputs.stdOut.readLines() == ['info']
        outputs.stdErr == ''
    }

    def "renders log events when plain console is attached"() {
        def snapshot = renderer.snapshot()
        def output = new ByteArrayOutputStream()
        def error = new ByteArrayOutputStream()
        renderer.attachConsole(output, error, ConsoleOutput.Plain)

        when:
        renderer.onOutput(start(description: 'description', buildOperationStart: true, id: 1L, buildOperationId: 1L, buildOperationCategory: BuildOperationCategory.TASK))
        renderer.onOutput(event('info', LogLevel.INFO, 1L))
        renderer.onOutput(event('error', LogLevel.ERROR, 1L))
        renderer.onOutput(event('un-grouped error', LogLevel.ERROR))
        renderer.onOutput(complete('status'))
        renderer.restore(snapshot) // close console to flush

        then:
        output.toString().readLines() == ['', '> description status', 'info']
        error.toString().readLines() == ['un-grouped error', 'error']
    }

    def "renders log events in plain console when log level is debug"() {
        renderer.configure(LogLevel.DEBUG)
        def snapshot = renderer.snapshot()
        def output = new ByteArrayOutputStream()
        def error = new ByteArrayOutputStream()
        renderer.attachConsole(output, error, ConsoleOutput.Plain)

        when:
        renderer.onOutput(event(tenAm, 'info', LogLevel.INFO))
        renderer.onOutput(event(tenAm, 'error', LogLevel.ERROR))
        renderer.restore(snapshot) // close console to flush

        then:
        output.toString().readLines() == ["${tenAmFormatted} [INFO] [category] info"]
        error.toString().readLines() == ["${tenAmFormatted} [ERROR] [category] error"]
    }

    def "attaches plain console when stdout and stderr are attached"() {
        when:
        def output = new ByteArrayOutputStream()
        def error = new ByteArrayOutputStream()
        renderer.attachSystemOutAndErr()
        def snapshot = renderer.snapshot()
        renderer.attachConsole(output, error, ConsoleOutput.Plain)
        renderer.onOutput(event('info', LogLevel.INFO))
        renderer.onOutput(event('error', LogLevel.ERROR))
        renderer.restore(snapshot) // close console to flush

        then:
        output.toString().readLines() == ['info']
        error.toString().readLines() == ['error']
        outputs.stdOut == ''
        outputs.stdErr == ''
    }
}

class TestListener implements StandardOutputListener {
    private final StringWriter writer = new StringWriter();

    def getValue() {
        return writer.toString()
    }

    public void onOutput(CharSequence output) {
        writer.append(output);
    }
}

