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
import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.internal.logging.config.LoggingRouter
import org.gradle.internal.logging.config.LoggingSourceSystem
import org.gradle.internal.logging.config.LoggingSystem
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.util.internal.RedirectStdOutAndErr
import org.junit.Rule
import spock.lang.Specification

class DefaultLoggingManagerTest extends Specification {
    @Rule
    public final RedirectStdOutAndErr outputs = new RedirectStdOutAndErr();
    private final def slf4jLoggingSystem = Mock(LoggingSourceSystem)
    private final def javaUtilLoggingSystem = Mock(LoggingSourceSystem)
    private final def stdOutLoggingSystem = Mock(LoggingSourceSystem)
    private final def stdErrLoggingSystem = Mock(LoggingSourceSystem)
    private final def loggingRouter = Mock(LoggingRouter)
    private final DefaultLoggingManager loggingManager = new DefaultLoggingManager(slf4jLoggingSystem, javaUtilLoggingSystem, stdOutLoggingSystem, stdErrLoggingSystem, loggingRouter);

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
        1 * stdOutLoggingSystem.snapshot() >> stdOutSnapshot
        1 * stdErrLoggingSystem.snapshot() >> stdErrSnapshot
        1 * stdOutLoggingSystem.setLevel(LogLevel.DEBUG)
        1 * stdErrLoggingSystem.setLevel(LogLevel.INFO)
        0 * stdOutLoggingSystem._
        0 * stdErrLoggingSystem._

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

        when:
        loggingManager.start()

        then:
        1 * stdOutLoggingSystem.snapshot() >> stdOutSnapshot
        1 * stdErrLoggingSystem.snapshot() >> stdErrSnapshot
        1 * stdOutLoggingSystem.startCapture()
        1 * stdErrLoggingSystem.startCapture()
        0 * stdOutLoggingSystem._
        0 * stdErrLoggingSystem._

        when:
        loggingManager.stop()

        then:
        1 * stdOutLoggingSystem.restore(stdOutSnapshot)
        1 * stdErrLoggingSystem.restore(stdErrSnapshot)
    }

    public void "can start and stop with log level set"() {
        loggingManager.setLevelInternal(LogLevel.DEBUG)

        final def routerSnapshot = Mock(LoggingSystem.Snapshot)
        final def slf4jSnapshot = Mock(LoggingSystem.Snapshot)
        final def javaUtilSnapshot = Mock(LoggingSystem.Snapshot)

        when:
        loggingManager.start()

        then:
        1 * slf4jLoggingSystem.snapshot() >> slf4jSnapshot
        1 * javaUtilLoggingSystem.snapshot() >> javaUtilSnapshot
        1 * slf4jLoggingSystem.startCapture()
        1 * slf4jLoggingSystem.setLevel(LogLevel.DEBUG)
        1 * javaUtilLoggingSystem.setLevel(LogLevel.DEBUG)
        1 * loggingRouter.snapshot() >> routerSnapshot
        1 * loggingRouter.configure(LogLevel.DEBUG)
        0 * loggingRouter._
        0 * slf4jLoggingSystem._
        0 * javaUtilLoggingSystem._

        when:
        loggingManager.stop()

        then:
        1 * slf4jLoggingSystem.restore(slf4jSnapshot)
        1 * javaUtilLoggingSystem.restore(javaUtilSnapshot)
        1 * loggingRouter.restore(routerSnapshot)
        0 * loggingRouter._
        0 * slf4jLoggingSystem._
        0 * javaUtilLoggingSystem._
    }

    public void "can start and stop with log level not set"() {
        final slf4jSnapshot = Mock(LoggingSystem.Snapshot.class);
        final routerSnapshot = Mock(LoggingSystem.Snapshot.class);
        final javaUtilSnapshot = Mock(LoggingSystem.Snapshot.class);

        when:
        loggingManager.start()

        then:
        1 * loggingRouter.snapshot() >> routerSnapshot
        1 * slf4jLoggingSystem.snapshot() >> slf4jSnapshot
        1 * javaUtilLoggingSystem.snapshot() >> javaUtilSnapshot
        1 * slf4jLoggingSystem.startCapture()
        0 * loggingRouter._
        0 * slf4jLoggingSystem._
        0 * javaUtilLoggingSystem._

        when:
        loggingManager.stop()

        then:
        1 * loggingRouter.restore(routerSnapshot)
        1 * slf4jLoggingSystem.restore(slf4jSnapshot)
        1 * javaUtilLoggingSystem.restore(javaUtilSnapshot)
        0 * loggingRouter._
        0 * slf4jLoggingSystem._
        0 * javaUtilLoggingSystem._
    }

    public void "can change capture level while started"() {
        final LoggingSystem.Snapshot stdOutSnapshot = Mock(LoggingSystem.Snapshot.class)
        final LoggingSystem.Snapshot stdErrSnapshot = Mock(LoggingSystem.Snapshot.class)

        loggingManager.captureStandardOutput(LogLevel.DEBUG)
        loggingManager.captureStandardError(LogLevel.DEBUG)

        when:
        loggingManager.start()

        then:
        1 * stdOutLoggingSystem.snapshot() >> stdOutSnapshot
        1 * stdErrLoggingSystem.snapshot() >> stdErrSnapshot
        1 * stdOutLoggingSystem.setLevel(LogLevel.DEBUG)
        1 * stdErrLoggingSystem.setLevel(LogLevel.DEBUG)

        when:
        loggingManager.captureStandardOutput(LogLevel.WARN)

        then:
        1 * stdOutLoggingSystem.setLevel(LogLevel.WARN)

        when:
        loggingManager.stop()

        then:
        1 * stdOutLoggingSystem.restore(stdOutSnapshot)
        1 * stdErrLoggingSystem.restore(stdErrSnapshot)
    }

    public void "can change log level while started"() {
        final slf4jSnapshot = Mock(LoggingSystem.Snapshot.class)
        final routerSnapshot = Mock(LoggingSystem.Snapshot.class)
        final javaUtilSnapshot = Mock(LoggingSystem.Snapshot.class);

        when:
        loggingManager.start()

        then:
        1 * loggingRouter.snapshot() >> routerSnapshot
        1 * slf4jLoggingSystem.snapshot() >> slf4jSnapshot
        1 * javaUtilLoggingSystem.snapshot() >> javaUtilSnapshot
        1 * slf4jLoggingSystem.startCapture()
        0 * loggingRouter._
        0 * slf4jLoggingSystem._
        0 * javaUtilLoggingSystem._

        when:
        loggingManager.setLevelInternal(LogLevel.LIFECYCLE)

        then:
        1 * slf4jLoggingSystem.setLevel(LogLevel.LIFECYCLE)
        1 * javaUtilLoggingSystem.setLevel(LogLevel.LIFECYCLE)
        1 * loggingRouter.configure(LogLevel.LIFECYCLE)
        0 * loggingRouter._
        0 * slf4jLoggingSystem._
        0 * javaUtilLoggingSystem._

        when:
        loggingManager.stop()

        then:
        1 * slf4jLoggingSystem.restore(slf4jSnapshot)
        1 * javaUtilLoggingSystem.restore(javaUtilSnapshot)
        1 * loggingRouter.restore(routerSnapshot)
        0 * loggingRouter._
        0 * slf4jLoggingSystem._
        0 * javaUtilLoggingSystem._
    }

    public void "adds standard out listener on start and removes on stop"() {
        final StandardOutputListener stdoutListener = Mock(StandardOutputListener.class)

        loggingManager.addStandardOutputListener(stdoutListener)

        when:
        loggingManager.start()

        then:
        1 * loggingRouter.addStandardOutputListener(stdoutListener)

        when:
        loggingManager.stop()

        then:
        1 * loggingRouter.removeStandardOutputListener(stdoutListener)
    }

    public void "adds standard error listener on start and removes on stop"() {
        final StandardOutputListener stderrListener = Mock(StandardOutputListener.class)

        loggingManager.addStandardErrorListener(stderrListener)

        when:
        loggingManager.start()

        then:
        1 * loggingRouter.addStandardErrorListener(stderrListener)

        when:
        loggingManager.stop()

        then:
        1 * loggingRouter.removeStandardErrorListener(stderrListener)
    }

    public void "adds output event listener on start and removes on stop"() {
        final OutputEventListener listener = Mock(OutputEventListener.class)

        loggingManager.addOutputEventListener(listener)

        when:
        loggingManager.start();

        then:
        1 * loggingRouter.addOutputEventListener(listener)

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
        loggingRouter.addStandardOutputListener(stdoutListener)

        when:
        loggingManager.stop()

        then:
        1 * loggingRouter.removeStandardOutputListener(stdoutListener)
    }

    public void "can add standard error listener while started"() {
        final StandardOutputListener stderrListener = Mock(StandardOutputListener.class)

        loggingManager.start()

        when:
        loggingManager.addStandardErrorListener(stderrListener)

        then:
        1 * loggingRouter.addStandardErrorListener(stderrListener)

        when:
        loggingManager.stop()

        then:
        loggingRouter.removeStandardErrorListener(stderrListener)
    }

    public void "can add output event listener while started"() {
        final OutputEventListener listener = Mock(OutputEventListener.class)

        loggingManager.start()

        when:
        loggingManager.addOutputEventListener(listener)

        then:
        1 * loggingRouter.addOutputEventListener(listener)

        when:
        loggingManager.stop()

        then:
        1 * loggingRouter.removeOutputEventListener(listener)
    }

    public void "can remove standard output listener while started"() {
        final StandardOutputListener stdoutListener = Mock(StandardOutputListener.class)

        loggingManager.addStandardOutputListener(stdoutListener)

        when:
        loggingManager.start()

        then:
        1 * loggingRouter.addStandardOutputListener(stdoutListener)

        when:
        loggingManager.removeStandardOutputListener(stdoutListener)

        then:
        1 * loggingRouter.removeStandardOutputListener(stdoutListener)

        when:
        loggingManager.stop()

        then:
        0 * loggingRouter.removeStandardOutputListener(stdoutListener)
    }

    public void "can remove standard error listener while started"() {
        final StandardOutputListener stderrListener = Mock(StandardOutputListener.class);

        loggingManager.addStandardErrorListener(stderrListener)

        when:
        loggingManager.start()

        then:
        1 * loggingRouter.addStandardErrorListener(stderrListener)

        when:
        loggingManager.removeStandardErrorListener(stderrListener)

        then:
        1 * loggingRouter.removeStandardErrorListener(stderrListener)

        when:
        loggingManager.stop()

        then:
        0 * loggingRouter.removeStandardErrorListener(stderrListener)
    }

    public void "can remove output event listener while started"() {
        final OutputEventListener listener = Mock(OutputEventListener.class)

        loggingManager.addOutputEventListener(listener)

        when:
        loggingManager.start()

        then:
        1 * loggingRouter.addOutputEventListener(listener)

        when:
        loggingManager.removeOutputEventListener(listener)

        then:
        1 * loggingRouter.removeOutputEventListener(listener)

        when:
        loggingManager.stop()

        then:
        0 * loggingRouter.removeOutputEventListener(listener)
    }

    def "attaches process console on start and restores on stop"() {
        def snapshot = Stub(LoggingSystem.Snapshot)

        loggingManager.attachProcessConsole(ConsoleOutput.Auto)

        when:
        loggingManager.start()

        then:
        1 * loggingRouter.snapshot() >> snapshot
        1 * loggingRouter.attachProcessConsole(ConsoleOutput.Auto)
        0 * loggingRouter._

        when:
        loggingManager.stop()

        then:
        1 * loggingRouter.restore(snapshot)
        0 * loggingRouter._
    }

    def "can attach process console while started"() {
        def snapshot = Stub(LoggingSystem.Snapshot)

        when:
        loggingManager.start()

        then:
        1 * loggingRouter.snapshot() >> snapshot
        0 * loggingRouter._

        when:
        loggingManager.attachProcessConsole(ConsoleOutput.Auto)

        then:
        1 * loggingRouter.attachProcessConsole(ConsoleOutput.Auto)
        0 * loggingRouter._

        when:
        loggingManager.stop()

        then:
        1 * loggingRouter.restore(snapshot)
        0 * loggingRouter._
    }

    def "attaches console output on start and restores on stop"() {
        def snapshot = Stub(LoggingSystem.Snapshot)
        def output = Stub(OutputStream)
        def error = Stub(OutputStream)

        loggingManager.attachConsole(output, error, ConsoleOutput.Verbose)

        when:
        loggingManager.start()

        then:
        1 * loggingRouter.snapshot() >> snapshot
        1 * loggingRouter.attachConsole(output, error, ConsoleOutput.Verbose, null)
        0 * loggingRouter._

        when:
        loggingManager.stop()

        then:
        1 * loggingRouter.restore(snapshot)
        0 * loggingRouter._
    }

    def "can attach console output while started"() {
        def snapshot = Stub(LoggingSystem.Snapshot)
        def output = Stub(OutputStream)
        def error = Stub(OutputStream)

        when:
        loggingManager.start()

        then:
        1 * loggingRouter.snapshot() >> snapshot
        0 * loggingRouter._

        when:
        loggingManager.attachConsole(output, error, ConsoleOutput.Verbose)

        then:
        1 * loggingRouter.attachConsole(output, error, ConsoleOutput.Verbose, null)
        0 * loggingRouter._

        when:
        loggingManager.stop()

        then:
        1 * loggingRouter.restore(snapshot)
        0 * loggingRouter._
    }
}
