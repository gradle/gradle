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

package org.gradle.logging

import org.gradle.util.RedirectStdOutAndErr
import org.junit.Rule
import spock.lang.Specification
import org.gradle.api.logging.LogLevel
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import ch.qos.logback.classic.Level

class StandardOutputLoggingSystemTest extends Specification {
    @Rule
    public final RedirectStdOutAndErr outputs = new RedirectStdOutAndErr();
    private final Appender<ILoggingEvent> appender = Mock()
    private final LoggingTestHelper helper = new LoggingTestHelper(appender)
    private final StandardOutputLoggingSystem loggingSystem = new StandardOutputLoggingSystem()

    def setup() {
        helper.attachAppender()
    }
    
    def teardown() {
        helper.detachAppender()
    }

    def onStartsCapturingWhenNotAlreadyCapturing() {
        when:
        loggingSystem.on(LogLevel.INFO)
        System.out.println('info')
        System.err.println('err')

        then:
        1 * appender.doAppend({ILoggingEvent event -> event.level == Level.INFO && event.message == 'info'})
        1 * appender.doAppend({ILoggingEvent event -> event.level == Level.ERROR && event.message == 'err'})
        outputs.stdOut == ''
        outputs.stdErr == ''
        0 * appender._
    }

    def onChangesLogLevelsWhenAlreadyCapturing() {
        loggingSystem.on(LogLevel.INFO)

        when:
        loggingSystem.on(LogLevel.DEBUG)
        System.out.println('info')
        System.err.println('err')

        then:
        1 * appender.doAppend({ILoggingEvent event -> event.level == Level.DEBUG && event.message == 'info'})
        1 * appender.doAppend({ILoggingEvent event -> event.level == Level.ERROR && event.message == 'err'})
        outputs.stdOut == ''
        outputs.stdErr == ''
        0 * appender._
    }

    def offDoesNothingWhenNotAlreadyCapturing() {
        when:
        loggingSystem.off()
        System.out.println('info')
        System.err.println('err')

        then:
        outputs.stdOut == String.format('info%n')
        outputs.stdErr == String.format('err%n')
        0 * appender._
    }

    def offStopsCapturingWhenAlreadyCapturing() {
        loggingSystem.on(LogLevel.WARN)

        when:
        loggingSystem.off()

        System.out.println('info')
        System.err.println('err')

        then:
        outputs.stdOut == String.format('info%n')
        outputs.stdErr == String.format('err%n')
        0 * appender._
    }

    def restoreStopsCapturingWhenCapturingWasNotInstalledWhenSnapshotTaken() {
        def snapshot = loggingSystem.snapshot()
        loggingSystem.on(LogLevel.ERROR)

        when:
        loggingSystem.restore(snapshot)
        System.out.println('info')
        System.err.println('err')

        then:
        outputs.stdOut == String.format('info%n')
        outputs.stdErr == String.format('err%n')
        0 * appender._
    }

    def restoreStopsCapturingWhenCapturingWasOffWhenSnapshotTaken() {
        loggingSystem.on(LogLevel.INFO)
        loggingSystem.off()
        def snapshot = loggingSystem.snapshot()
        loggingSystem.on(LogLevel.ERROR)

        when:
        loggingSystem.restore(snapshot)
        System.out.println('info')
        System.err.println('err')

        then:
        outputs.stdOut == String.format('info%n')
        outputs.stdErr == String.format('err%n')
        0 * appender._
    }

    def restoreStartsCapturingWhenCapturingWasOnWhenSnapshotTaken() {
        loggingSystem.on(LogLevel.WARN)
        def snapshot = loggingSystem.snapshot()
        loggingSystem.off()

        when:
        loggingSystem.restore(snapshot)
        System.out.println('info')
        System.err.println('err')

        then:
        1 * appender.doAppend({ILoggingEvent event -> event.level == Level.WARN && event.message == 'info'})
        1 * appender.doAppend({ILoggingEvent event -> event.level == Level.ERROR && event.message == 'err'})
        outputs.stdOut == ''
        outputs.stdErr == ''
        0 * appender._
    }
}
