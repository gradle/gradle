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
package org.gradle.internal.logging.services

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.StandardOutputListener
import org.gradle.internal.logging.LoggingOutputInternal
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.source.LoggingSystem
import org.gradle.util.RedirectStdOutAndErr
import org.junit.Ignore
import org.junit.Rule
import spock.lang.Specification

public class DefaultLoggingManagerTest extends Specification {
    @Rule
    public final RedirectStdOutAndErr outputs = new RedirectStdOutAndErr();
    private final LoggingSystem loggingSystem = Mock(LoggingSystem.class);
    private final LoggingSystem javaUtilLoggingSystem = Mock(LoggingSystem.class);
    private final LoggingSystem stdOutLoggingSystem = Mock(LoggingSystem.class);
    private final LoggingSystem stdErrLoggingSystem = Mock(LoggingSystem.class);
    private final LoggingOutputInternal loggingOutput = Mock(LoggingOutputInternal.class);
    private final DefaultLoggingManager loggingManager = new DefaultLoggingManager(loggingSystem, javaUtilLoggingSystem, stdOutLoggingSystem, stdErrLoggingSystem, loggingOutput);

    public void "default values are set"() {
        expect:
        loggingManager.getStandardOutputCaptureLevel() == null
        loggingManager.getStandardErrorCaptureLevel() == null
        loggingManager.getLevel() == null
    }

    public void "can change standard out capture log level"() {
        when:
        loggingManager.captureStandardOutput(LogLevel.ERROR)

        then:
        loggingManager.getStandardOutputCaptureLevel() == LogLevel.ERROR
    }

    public void "can change standard error capture log level"() {
        when:
        loggingManager.captureStandardError(LogLevel.WARN)

        then:
        loggingManager.getStandardErrorCaptureLevel() == LogLevel.WARN
    }

    public void "can change log level using internal method"() {
        when:
        loggingManager.setLevelInternal(LogLevel.ERROR)

        then:
        loggingManager.getLevel() == LogLevel.ERROR
    }

    public void "can start and stop with capture level set"() {
        loggingManager.captureStandardOutput(LogLevel.DEBUG)
        loggingManager.captureStandardError(LogLevel.INFO)

        final LoggingSystem.Snapshot stdOutSnapshot = Mock(LoggingSystem.Snapshot.class)
        final LoggingSystem.Snapshot stdErrSnapshot = Mock(LoggingSystem.Snapshot.class)

        when:
        loggingManager.start()

        then:
        1 * stdOutLoggingSystem.on(LogLevel.DEBUG, LogLevel.DEBUG) >> stdOutSnapshot
        1 * stdErrLoggingSystem.on(LogLevel.INFO, LogLevel.INFO) >> stdErrSnapshot

        when:
        loggingManager.stop()

        then:
        1 * stdOutLoggingSystem.restore(stdOutSnapshot)
        1 * stdErrLoggingSystem.restore(stdErrSnapshot)
    }

    public void "can start and stop with system capture enabled"() {
        loggingManager.captureSystemSources()

        final LoggingSystem.Snapshot stdOutSnapshot = Mock(LoggingSystem.Snapshot.class)
        final LoggingSystem.Snapshot stdErrSnapshot = Mock(LoggingSystem.Snapshot.class)
        final LoggingSystem.Snapshot javaUtilSnapshot = Mock(LoggingSystem.Snapshot.class)

        when:
        loggingManager.start()

        then:
        1 * javaUtilLoggingSystem.on(LogLevel.DEBUG, LogLevel.DEBUG) >> javaUtilSnapshot
        1 * stdOutLoggingSystem.on(LogLevel.QUIET, LogLevel.QUIET) >> stdOutSnapshot
        1 * stdErrLoggingSystem.on(LogLevel.ERROR, LogLevel.ERROR) >> stdErrSnapshot

        when:
        loggingManager.stop()

        then:
        1 * javaUtilLoggingSystem.restore(javaUtilSnapshot)
        1 * stdOutLoggingSystem.restore(stdOutSnapshot)
        1 * stdErrLoggingSystem.restore(stdErrSnapshot)
    }

    public void "can start and stop with log level set"() {
        loggingManager.setLevelInternal(LogLevel.DEBUG)

        final LoggingSystem.Snapshot snapshot = Mock(LoggingSystem.Snapshot.class)

        when:
        loggingManager.start()

        then:
        1 * loggingSystem.on(LogLevel.DEBUG, LogLevel.DEBUG) >> snapshot

        when:
        loggingManager.stop()

        then:
        1 * loggingSystem.restore(snapshot)
    }

    public void "can start and stop with log level set using public method"() {
        loggingManager.setLevel(LogLevel.DEBUG)

        final LoggingSystem.Snapshot snapshot = Mock(LoggingSystem.Snapshot.class)

        when:
        loggingManager.start()

        then:
        1 * loggingSystem.on(LogLevel.DEBUG, LogLevel.DEBUG) >> snapshot

        when:
        loggingManager.stop()

        then:
        1 * loggingSystem.restore(snapshot)
    }

    public void "can start and stop with log level not set"() {
        final LoggingSystem.Snapshot snapshot = Mock(LoggingSystem.Snapshot.class);

        when:
        loggingManager.start()

        then:
        1 * loggingSystem.snapshot() >> snapshot

        when:
        loggingManager.stop()

        then:
        loggingSystem.restore(snapshot)
    }

    public void "can change capture level while started"() {
        final LoggingSystem.Snapshot stdOutSnapshot = Mock(LoggingSystem.Snapshot.class)
        final LoggingSystem.Snapshot stdErrSnapshot = Mock(LoggingSystem.Snapshot.class)

        loggingManager.captureStandardOutput(LogLevel.DEBUG)
        loggingManager.captureStandardError(LogLevel.DEBUG)

        when:
        loggingManager.start()

        then:
        1 * stdOutLoggingSystem.on(LogLevel.DEBUG, LogLevel.DEBUG) >> stdOutSnapshot
        1 * stdErrLoggingSystem.on(LogLevel.DEBUG, LogLevel.DEBUG) >> stdErrSnapshot

        when:
        loggingManager.captureStandardOutput(LogLevel.WARN)

        then:
        1 * stdOutLoggingSystem.on(LogLevel.WARN, LogLevel.WARN)

        when:
        loggingManager.stop()

        then:
        1 * stdOutLoggingSystem.restore(stdOutSnapshot)
        1 * stdErrLoggingSystem.restore(stdErrSnapshot)
    }

    public void "can change log level while started"() {
        final LoggingSystem.Snapshot snapshot = Mock(LoggingSystem.Snapshot.class)

        when:
        loggingManager.start()

        then:
        1 * loggingSystem.snapshot() >> snapshot

        when:
        loggingManager.setLevelInternal(LogLevel.LIFECYCLE)

        then:
        1 * loggingSystem.on(LogLevel.LIFECYCLE, LogLevel.LIFECYCLE) >> Mock(LoggingSystem.Snapshot.class)

        when:
        loggingManager.stop()

        then:
        1 * loggingSystem.restore(snapshot)
    }

    def "can change log level using public method while started"() {
        final LoggingSystem.Snapshot snapshot = Mock(LoggingSystem.Snapshot.class)

        when:
        loggingManager.start()

        then:
        1 * loggingSystem.snapshot() >> snapshot

        when:
        loggingManager.setLevel(LogLevel.LIFECYCLE)

        then:
        1 * loggingSystem.on(LogLevel.LIFECYCLE, LogLevel.LIFECYCLE) >> Mock(LoggingSystem.Snapshot.class)

        when:
        loggingManager.stop()

        then:
        1 * loggingSystem.restore(snapshot)
    }

    public void "adds standard out listener on start and removes on stop"() {
        final StandardOutputListener stdoutListener = Mock(StandardOutputListener.class)

        loggingManager.addStandardOutputListener(stdoutListener)

        when:
        loggingManager.start()

        then:
        1 * loggingOutput.addStandardOutputListener(stdoutListener)

        when:
        loggingManager.stop()

        then:
        1 * loggingOutput.removeStandardOutputListener(stdoutListener)
    }

    public void "adds standard error listener on start and removes on stop"() {
        final StandardOutputListener stderrListener = Mock(StandardOutputListener.class)

        loggingManager.addStandardErrorListener(stderrListener)

        when:
        loggingManager.start()

        then:
        1 * loggingOutput.addStandardErrorListener(stderrListener)

        when:
        loggingManager.stop()

        then:
        1 * loggingOutput.removeStandardErrorListener(stderrListener)
    }

    public void "adds output event listener on start and removes on stop"() {
        final OutputEventListener listener = Mock(OutputEventListener.class)

        loggingManager.addOutputEventListener(listener)

        when:
        loggingManager.start();

        then:
        1 * loggingOutput.addOutputEventListener(listener)

        when:
        loggingManager.stop()

        then:
        loggingManager.removeOutputEventListener(listener)
    }

    public void "can add standard out listener while started"() {
        final StandardOutputListener stdoutListener = Mock(StandardOutputListener.class)

        loggingManager.start()

        when:
        loggingManager.addStandardOutputListener(stdoutListener)

        then:
        loggingOutput.addStandardOutputListener(stdoutListener)

        when:
        loggingManager.stop()

        then:
        1 * loggingOutput.removeStandardOutputListener(stdoutListener)
    }

    public void "can add standard error listener while started"() {
        final StandardOutputListener stderrListener = Mock(StandardOutputListener.class)

        loggingManager.start()

        when:
        loggingManager.addStandardErrorListener(stderrListener)

        then:
        1 * loggingOutput.addStandardErrorListener(stderrListener)

        when:
        loggingManager.stop()

        then:
        loggingOutput.removeStandardErrorListener(stderrListener)
    }

    public void "can add output event listener while started"() {
        final OutputEventListener listener = Mock(OutputEventListener.class)

        loggingManager.start()

        when:
        loggingManager.addOutputEventListener(listener)

        then:
        1 * loggingOutput.addOutputEventListener(listener)

        when:
        loggingManager.stop()

        then:
        1 * loggingOutput.removeOutputEventListener(listener)
    }

    public void "can remove standard output listener while started"() {
        final StandardOutputListener stdoutListener = Mock(StandardOutputListener.class)

        loggingManager.addStandardOutputListener(stdoutListener)

        when:
        loggingManager.start()

        then:
        1 * loggingOutput.addStandardOutputListener(stdoutListener)

        when:
        loggingManager.removeStandardOutputListener(stdoutListener)

        then:
        1 * loggingOutput.removeStandardOutputListener(stdoutListener)

        when:
        loggingManager.stop()

        then:
        0 * loggingOutput.removeStandardOutputListener(stdoutListener)
    }

    public void "can remove standard error listener while started"() {
        final StandardOutputListener stderrListener = Mock(StandardOutputListener.class);

        loggingManager.addStandardErrorListener(stderrListener)

        when:
        loggingManager.start()

        then:
        1 * loggingOutput.addStandardErrorListener(stderrListener)

        when:
        loggingManager.removeStandardErrorListener(stderrListener)

        then:
        1 * loggingOutput.removeStandardErrorListener(stderrListener)

        when:
        loggingManager.stop()

        then:
        0 * loggingOutput.removeStandardErrorListener(stderrListener)
    }

    public void "can remove output event listener while started"() {
        final OutputEventListener listener = Mock(OutputEventListener.class)

        loggingManager.addOutputEventListener(listener)

        when:
        loggingManager.start()

        then:
        1 * loggingOutput.addOutputEventListener(listener)

        when:
        loggingManager.removeOutputEventListener(listener)

        then:
        1 * loggingOutput.removeOutputEventListener(listener)

        when:
        loggingManager.stop()

        then:
        0 * loggingOutput.removeOutputEventListener(listener)
    }
}
