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

package org.gradle.internal.logging.source

import org.gradle.api.logging.LogLevel
import org.gradle.internal.logging.events.LogLevelChangeEvent
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.StyledTextOutputEvent
import org.gradle.internal.operations.CurrentBuildOperationRef
import org.gradle.internal.operations.DefaultBuildOperationRef
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.time.Clock
import org.gradle.util.internal.TextUtil
import spock.lang.Specification

class PrintStreamLoggingSystemTest extends Specification {

    private final OutputStream original = new ByteArrayOutputStream()
    private final PrintStream originalStream = new PrintStream(original)
    private PrintStream stream = originalStream
    private final OutputEventListener listener = Mock()
    private final Clock timeProvider = { 1200L } as Clock
    private final PrintStreamLoggingSystem loggingSystem = new PrintStreamLoggingSystem(listener, 'category', timeProvider) {
        protected PrintStream get() {
            stream
        }

        protected void set(PrintStream printStream) {
            stream = printStream
        }
    }

    private final CurrentBuildOperationRef currentBuildOperationRef = CurrentBuildOperationRef.instance()

    def onReplacesOriginalStreamAndRemovesWhenRestored() {
        when:
        def snapshot = loggingSystem.startCapture()

        then:
        stream != originalStream

        when:
        loggingSystem.restore(snapshot)

        then:
        stream == originalStream
    }

    def originalStreamCanBeReplacedBetweenCapture() {
        def stream2 = new PrintStream(new ByteArrayOutputStream())

        given:
        loggingSystem.restore(loggingSystem.startCapture())
        stream = stream2

        when:
        def snapshot = loggingSystem.startCapture()

        then:
        stream != stream2

        when:
        loggingSystem.restore(snapshot)

        then:
        stream == stream2
    }

    def onStartsCapturingWhenNotAlreadyCapturing() {
        when:
        loggingSystem.setLevel(LogLevel.INFO)
        loggingSystem.startCapture()
        stream.println('info')

        then:
        1 * listener.onOutput({ it instanceof LogLevelChangeEvent && it.newLogLevel == LogLevel.INFO })
        1 * listener.onOutput({ it instanceof StyledTextOutputEvent && it.spans[0].text == withEOL('info') })
        original.toString() == ''
        0 * listener._
    }

    def fillsInEventDetails() {
        given:
        currentBuildOperationRef.set(new DefaultBuildOperationRef(
            new OperationIdentifier(42),
            new OperationIdentifier(1)
        ))

        when:
        loggingSystem.startCapture()
        stream.println('info')

        then:
        1 * listener.onOutput({
            it instanceof StyledTextOutputEvent &&
                it.category == 'category' &&
                it.timestamp == 1200 &&
                it.spans[0].text == withEOL('info') &&
                it.buildOperationId.id == 42L
        })

        cleanup:
        currentBuildOperationRef.clear()
    }

    def onChangesLogLevelsWhenAlreadyCapturing() {
        loggingSystem.setLevel(LogLevel.INFO)
        loggingSystem.startCapture()

        when:
        loggingSystem.setLevel(LogLevel.DEBUG)
        stream.println('info')

        then:
        1 * listener.onOutput({ it instanceof LogLevelChangeEvent && it.newLogLevel == LogLevel.DEBUG })
        1 * listener.onOutput({ it instanceof StyledTextOutputEvent && it.spans[0].text == withEOL('info') })
        original.toString() == ''
        0 * listener._
    }

    def restoreDoesNothingWhenNotAlreadyCapturing() {
        given:
        def snapshot = loggingSystem.snapshot()

        when:
        loggingSystem.restore(snapshot)
        stream.println('info')

        then:
        stream == originalStream
        original.toString() == withEOL('info')
        0 * listener._
    }

    def restoreFlushesPartialLine() {
        def snapshot = loggingSystem.snapshot()
        loggingSystem.setLevel(LogLevel.INFO)
        loggingSystem.startCapture()

        when:
        stream.print("info")
        loggingSystem.restore(snapshot)

        then:
        1 * listener.onOutput({ it instanceof StyledTextOutputEvent && it.spans[0].text == 'info' })
        original.toString() == ''
        0 * listener._
    }

    def restoreStopsCapturingWhenCapturingWasNotInstalledWhenSnapshotTaken() {
        def snapshot = loggingSystem.snapshot()
        loggingSystem.setLevel(LogLevel.ERROR)
        loggingSystem.startCapture()
        def capturing = stream

        when:
        loggingSystem.restore(snapshot)
        capturing.println("info-1")
        stream.println('info-2')

        then:
        stream == originalStream
        original.toString() == TextUtil.toPlatformLineSeparators('''info-1
info-2
''')
        0 * listener._
    }

    def restoreStopsCapturingWhenCapturingWasOffWhenSnapshotTaken() {
        def off = loggingSystem.snapshot()
        loggingSystem.setLevel(LogLevel.INFO)
        loggingSystem.startCapture()
        loggingSystem.restore(off)
        def snapshot = loggingSystem.snapshot()
        loggingSystem.setLevel(LogLevel.ERROR)
        loggingSystem.startCapture()
        def capturing = stream

        when:
        loggingSystem.restore(snapshot)
        capturing.println("info-1")
        stream.println('info-2')

        then:
        stream == originalStream
        original.toString() == TextUtil.toPlatformLineSeparators('''info-1
info-2
''')
        0 * listener._
    }

    def restoreStartsCapturingWhenCapturingWasOnWhenSnapshotTaken() {
        def off = loggingSystem.snapshot()
        loggingSystem.setLevel(LogLevel.WARN)
        loggingSystem.startCapture()
        def snapshot = loggingSystem.snapshot()
        loggingSystem.restore(off)

        when:
        loggingSystem.restore(snapshot)
        stream.println('info')

        then:
        1 * listener.onOutput({ it instanceof LogLevelChangeEvent && it.newLogLevel == LogLevel.WARN })
        1 * listener.onOutput({ it.spans[0].text == withEOL('info') })
        original.toString() == ''
        0 * listener._
    }

    def restoreSetsLogLevelToTheLevelWhenSnapshotTaken() {
        loggingSystem.setLevel(LogLevel.WARN)
        loggingSystem.startCapture()
        def snapshot = loggingSystem.snapshot()
        loggingSystem.setLevel(LogLevel.INFO)

        when:
        loggingSystem.restore(snapshot)
        stream.println('info')

        then:
        1 * listener.onOutput({ it instanceof LogLevelChangeEvent && it.newLogLevel == LogLevel.WARN })
        1 * listener.onOutput({ it.spans[0].text == withEOL('info') })
        original.toString() == ''
        0 * listener._
    }

    private String withEOL(String value) {
        return String.format('%s%n', value)
    }
}
