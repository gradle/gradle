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
import org.gradle.logging.StyledTextOutput

class PrintStreamLoggingSystemTest extends Specification {
    private final OutputStream original = new ByteArrayOutputStream()
    private PrintStream stream = new PrintStream(original)
    private final StyledTextOutput output = Mock()
    private final PrintStreamLoggingSystem loggingSystem = new PrintStreamLoggingSystem(output) {
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
        1 * output.text(LogLevel.INFO, 'info')
        original.toString() == ''
        0 * output._
    }

    def onChangesLogLevelsWhenAlreadyCapturing() {
        loggingSystem.on(LogLevel.INFO)

        when:
        loggingSystem.on(LogLevel.DEBUG)
        stream.println('info')

        then:
        1 * output.text(LogLevel.DEBUG, 'info')
        original.toString() == ''
        0 * output._
    }

    def offDoesNothingWhenNotAlreadyCapturing() {
        when:
        loggingSystem.off()
        stream.println('info')

        then:
        original.toString() == String.format('info%n')
        0 * output._
    }

    def offStopsCapturingWhenAlreadyCapturing() {
        loggingSystem.on(LogLevel.WARN)

        when:
        loggingSystem.off()

        stream.println('info')

        then:
        original.toString() == String.format('info%n')
        0 * output._
    }

    def restoreStopsCapturingWhenCapturingWasNotInstalledWhenSnapshotTaken() {
        def snapshot = loggingSystem.snapshot()
        loggingSystem.on(LogLevel.ERROR)

        when:
        loggingSystem.restore(snapshot)
        stream.println('info')

        then:
        original.toString() == String.format('info%n')
        0 * output._
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
        original.toString() == String.format('info%n')
        0 * output._
    }

    def restoreStartsCapturingWhenCapturingWasOnWhenSnapshotTaken() {
        loggingSystem.on(LogLevel.WARN)
        def snapshot = loggingSystem.snapshot()
        loggingSystem.off()

        when:
        loggingSystem.restore(snapshot)
        stream.println('info')

        then:
        1 * output.text(LogLevel.WARN, 'info')
        original.toString() == ''
        0 * output._
    }
}
