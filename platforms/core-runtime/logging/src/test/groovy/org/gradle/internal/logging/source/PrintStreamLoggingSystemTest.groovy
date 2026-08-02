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
import org.gradle.internal.time.FixedClock
import org.gradle.util.internal.TextUtil
import spock.lang.Specification

class PrintStreamLoggingSystemTest extends Specification {
    private static final long NOW = 1200L

    private final OutputStream original = new ByteArrayOutputStream()
    private final PrintStream originalStream = new PrintStream(original)
    private PrintStream stream = originalStream
    private final OutputEventListener listener = Mock()
    private final Clock timeProvider = FixedClock.createAt(NOW)

    private final PrintStreamLoggingSystem loggingSystem = new PrintStreamLoggingSystem(listener, 'category', timeProvider) {
        protected PrintStream get() {
            stream
        }

        protected void set(PrintStream printStream) {
            PrintStreamLoggingSystemTest.this.stream = printStream
        }
    }

    private final CurrentBuildOperationRef currentBuildOperationRef = CurrentBuildOperationRef.instance()

    def onReplacesOriginalStreamAndRemovesWhenRestored() {
        when:
        def snapshot = loggingSystem.startCapture()

        then:
        stream != originalStream

        when:
        loggingSystem.endCapture()
        loggingSystem.restore(snapshot)

        then:
        stream == originalStream
    }

    def originalStreamCanBeReplacedBetweenCapture() {
        def stream2 = new PrintStream(new ByteArrayOutputStream())

        given:
        endCapture(loggingSystem.startCapture())
        stream = stream2

        when:
        def snapshot = loggingSystem.startCapture()

        then:
        stream != stream2

        when:
        endCapture(snapshot)

        then:
        stream == stream2
    }

    def "capture stays installed until every scope that started it has ended it"() {
        given: 'two scopes start capture, as sibling projects configured in parallel do'
        def outer = loggingSystem.startCapture()
        def inner = loggingSystem.startCapture()
        def capturing = stream

        when: 'the inner scope finishes'
        endCapture(inner)

        then: 'capture is still installed, so the outer scope keeps being captured'
        stream == capturing
        stream != originalStream

        when:
        endCapture(outer)

        then: 'the last scope to leave tears capture down'
        stream == originalStream
    }

    def "a scope that never started capture does not tear down capture held by another scope"() {
        given:
        def capturingScope = loggingSystem.startCapture()
        def capturing = stream

        and: 'a second scope only snapshots and restores, without capturing'
        def nonCapturingScope = loggingSystem.snapshot()

        when:
        loggingSystem.restore(nonCapturingScope)

        then:
        stream == capturing
        stream != originalStream

        cleanup:
        endCapture(capturingScope)
    }

    def "concurrent scopes never lose output"() {
        given:
        def threads = 8
        def iterations = 200
        def start = new java.util.concurrent.CountDownLatch(1)
        def done = new java.util.concurrent.CountDownLatch(threads)
        def failure = new java.util.concurrent.atomic.AtomicReference<Throwable>()
        // Hold capture open for the whole run, as the build-level scope does.
        def buildScope = loggingSystem.startCapture()

        when:
        (1..threads).each { t ->
            Thread.start {
                try {
                    start.await()
                    iterations.times {
                        def scope = loggingSystem.startCapture()
                        stream.println("out")
                        loggingSystem.endCapture()
                        loggingSystem.restore(scope)
                    }
                } catch (Throwable e) {
                    failure.compareAndSet(null, e)
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        done.await()

        then:
        failure.get() == null
        and: 'capture was never torn down while the build scope held it, so nothing reached the original stream'
        original.toString() == ''

        cleanup:
        endCapture(buildScope)
    }

    private void endCapture(snapshot) {
        loggingSystem.endCapture()
        loggingSystem.restore(snapshot)
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
                it.timestamp == NOW &&
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
        endCapture(snapshot)

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
        endCapture(snapshot)
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
        endCapture(off)
        def snapshot = loggingSystem.snapshot()
        loggingSystem.setLevel(LogLevel.ERROR)
        loggingSystem.startCapture()
        def capturing = stream

        when:
        endCapture(snapshot)
        capturing.println("info-1")
        stream.println('info-2')

        then:
        stream == originalStream
        original.toString() == TextUtil.toPlatformLineSeparators('''info-1
info-2
''')
        0 * listener._
    }

    def restoreKeepsCapturingWhileTheScopeStillHoldsCapture() {
        def off = loggingSystem.snapshot()
        loggingSystem.setLevel(LogLevel.WARN)
        loggingSystem.startCapture()
        def snapshot = loggingSystem.snapshot()
        // Restoring a snapshot taken before capture started must not disable capture, because this scope has
        // not released it yet.
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
