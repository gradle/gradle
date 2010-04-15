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

import org.gradle.api.logging.StandardOutputLogging
import org.gradle.util.RedirectStdOutAndErr
import org.junit.Rule
import spock.lang.Specification
import org.gradle.api.logging.LogLevel

class StandardOutputLoggingSystemTest extends Specification {
    @Rule
    public final RedirectStdOutAndErr outputs = new RedirectStdOutAndErr();
    private final StandardOutputLoggingSystem loggingSystem = new StandardOutputLoggingSystem()

    def teardown() {
        StandardOutputLogging.off()
    }

    def onStartsCapturingWhenNotAlreadyCapturing() {
        when:
        loggingSystem.on(LogLevel.INFO)

        then:
        System.out == StandardOutputLogging.out
        System.out.standardOutputLoggingAdapter.level == LogLevel.INFO
        System.err == StandardOutputLogging.err
        System.err.standardOutputLoggingAdapter.level == LogLevel.ERROR
    }

    def onChangesLogLevelsWhenAlreadyCapturing() {
        StandardOutputLogging.on(LogLevel.INFO)

        when:
        loggingSystem.on(LogLevel.DEBUG)

        then:
        System.out == StandardOutputLogging.out
        System.out.standardOutputLoggingAdapter.level == LogLevel.DEBUG
        System.err == StandardOutputLogging.err
        System.err.standardOutputLoggingAdapter.level == LogLevel.ERROR
    }

    def offDoesNothingWhenNotAlreadyCapturing() {
        when:
        loggingSystem.off()

        then:
        System.out == StandardOutputLogging.DEFAULT_OUT
        System.err == StandardOutputLogging.DEFAULT_ERR
    }

    def offStopsCapturingWhenAlreadyCapturing() {
        StandardOutputLogging.on(LogLevel.WARN)

        when:
        loggingSystem.off()

        then:
        System.out == StandardOutputLogging.DEFAULT_OUT
        System.err == StandardOutputLogging.DEFAULT_ERR
    }
    
    def restoreStopsCapturingWhenCapturingWasOffWhenSnapshotTaken() {
        def stdOutSnapshot = StandardOutputLogging.stateSnapshot

        when:
        def snapshot = loggingSystem.snapshot()
        loggingSystem.on(LogLevel.ERROR)
        loggingSystem.restore(snapshot)

        then:
        StandardOutputLogging.stateSnapshot == stdOutSnapshot
    }

    def restoreStartsCapturingWhenCapturingWasOnWhenSnapshotTaken() {
        StandardOutputLogging.on(LogLevel.WARN)
        def stdOutSnapshot = StandardOutputLogging.stateSnapshot

        when:
        def snapshot = loggingSystem.snapshot()
        loggingSystem.off()
        loggingSystem.restore(snapshot)

        then:
        StandardOutputLogging.stateSnapshot == stdOutSnapshot
    }
}
