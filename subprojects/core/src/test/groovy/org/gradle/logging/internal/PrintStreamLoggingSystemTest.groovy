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

package org.gradle.logging.internal

import org.gradle.api.logging.LogLevel
import spock.lang.Specification
import org.gradle.util.TimeProvider

class PrintStreamLoggingSystemTest extends Specification {
    private final OutputStream original = new ByteArrayOutputStream()
    private PrintStream stream = new PrintStream(original)
    private final OutputEventListener listener = Mock()
    private final TimeProvider timeProvider = { 1200L } as TimeProvider
    private final PrintStreamLoggingSystem loggingSystem = new PrintStreamLoggingSystem(listener, 'category', timeProvider) {
        protected PrintStream get() {
            stream
        }

        protected void set(PrintStream printStream) {
            stream = printStream
        }
    }

    def onStartsCapturingWhenNotAlreadyCapturing() {
        when:
        loggingSystem.on(LogLevel.INFO)
        stream.println('info')

        then:
        1 * listener.onOutput({it instanceof LogLevelChangeEvent && it.newLogLevel == LogLevel.INFO})
        1 * listener.onOutput({it instanceof StyledTextOutputEvent && it.spans[0].text == withEOL('info')})
        original.toString() == ''
        0 * listener._
    }

    def fillsInEventDetails() {
        when:
        loggingSystem.on(LogLevel.INFO)
        stream.println('info')

        then:
        1 * listener.onOutput({it instanceof StyledTextOutputEvent && it.category == 'category' && it.timestamp == 1200 && it.spans[0].text == withEOL('info')})
    }

    def onChangesLogLevelsWhenAlreadyCapturing() {
        loggingSystem.on(LogLevel.INFO)

        when:
        loggingSystem.on(LogLevel.DEBUG)
        stream.println('info')

        then:
        1 * listener.onOutput({it instanceof LogLevelChangeEvent && it.newLogLevel == LogLevel.DEBUG})
        1 * listener.onOutput({it instanceof StyledTextOutputEvent && it.spans[0].text == withEOL('info')})
        original.toString() == ''
        0 * listener._
    }

    def offDoesNothingWhenNotAlreadyCapturing() {
        when:
        loggingSystem.off()
        stream.println('info')

        then:
        original.toString() == withEOL('info')
        0 * listener._
    }

    def offStopsCapturingWhenAlreadyCapturing() {
        loggingSystem.on(LogLevel.WARN)

        when:
        loggingSystem.off()

        stream.println('info')

        then:
        original.toString() == withEOL('info')
        0 * listener._
    }

    def restoreStopsCapturingWhenCapturingWasNotInstalledWhenSnapshotTaken() {
        def snapshot = loggingSystem.snapshot()
        loggingSystem.on(LogLevel.ERROR)

        when:
        loggingSystem.restore(snapshot)
        stream.println('info')

        then:
        original.toString() == withEOL('info')
        0 * listener._
    }

    def restoreStopsCapturingWhenCapturingWasOffWhenSnapshotTaken() {
        loggingSystem.on(LogLevel.INFO)
        loggingSystem.off()
        def snapshot = loggingSystem.snapshot()
        loggingSystem.on(LogLevel.ERROR)

        when:
        loggingSystem.restore(snapshot)
        stream.println('info')

        then:
        original.toString() == withEOL('info')
        0 * listener._
    }

    def restoreStartsCapturingWhenCapturingWasOnWhenSnapshotTaken() {
        loggingSystem.on(LogLevel.WARN)
        def snapshot = loggingSystem.snapshot()
        loggingSystem.off()

        when:
        loggingSystem.restore(snapshot)
        stream.println('info')

        then:
        1 * listener.onOutput({it instanceof LogLevelChangeEvent && it.newLogLevel == LogLevel.WARN})
        1 * listener.onOutput({it.spans[0].text == withEOL('info')})
        original.toString() == ''
        0 * listener._
    }
    
    def restoreSetsLogLevelToTheLevelWhenSnapshotTaken() {
        loggingSystem.on(LogLevel.WARN)
        def snapshot = loggingSystem.snapshot()
        loggingSystem.on(LogLevel.INFO)

        when:
        loggingSystem.restore(snapshot)
        stream.println('info')

        then:
        1 * listener.onOutput({it instanceof LogLevelChangeEvent && it.newLogLevel == LogLevel.WARN})
        1 * listener.onOutput({it.spans[0].text == withEOL('info')})
        original.toString() == ''
        0 * listener._
    }

    private String withEOL(String value) {
        return String.format('%s%n', value)
    }
}
