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

package org.gradle.process.internal

import org.gradle.api.Action
import org.gradle.api.internal.file.temp.GradleUserHomeTemporaryFileProvider
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logging
import org.gradle.internal.Actions
import org.gradle.internal.id.LongIdGenerator
import org.gradle.internal.jvm.inspection.CachingJvmMetadataDetector
import org.gradle.internal.jvm.inspection.DefaultJvmVersionDetector
import org.gradle.internal.remote.ObjectConnectionBuilder
import org.gradle.process.internal.health.memory.MemoryManager
import org.gradle.process.internal.worker.WorkerProcess
import org.gradle.process.internal.worker.WorkerProcessBuilder
import org.gradle.process.internal.worker.WorkerProcessContext
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.internal.TextUtil
import org.gradle.workers.internal.DefaultWorkerProcessFactory
import spock.lang.Timeout

import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue

@Timeout(120)
class WorkerProcessIntegrationTest extends AbstractWorkerProcessIntegrationSpec {
    private final TestListenerInterface listenerMock = Mock(TestListenerInterface.class)
    private final RemoteExceptionListener exceptionListener = new RemoteExceptionListener(listenerMock)

    ChildProcess worker(Action<? super WorkerProcessContext> action) {
        return new ChildProcess(action)
    }

    void execute(ChildProcess... processes) throws Throwable {
        for (ChildProcess process : processes) {
            process.start()
        }
        for (ChildProcess process : processes) {
            process.waitForStop()
        }
        exceptionListener.rethrow()
    }

    def workerProcessStdoutAndStderrIsForwardedToThisProcess() {
        when:
        execute(worker(new LoggingProcess(new StdOutSerializableLogAction("this is stdout"), new StdErrSerializableLogAction("this is stderr"))))

        then:
        outputEventListener.toString().contains(TextUtil.toPlatformLineSeparators("[ERROR] [system.err] <Normal>this is stderr\n</Normal>]"))
        outputEventListener.toString().contains(TextUtil.toPlatformLineSeparators("[QUIET] [system.out] <Normal>this is stdout\n</Normal>]"))
    }

    def "log level and categories are preserved when forwarded to main process"() {
        when:
        execute(worker(new LoggingProcess(action)))

        then:
        outputEventListener.toString().contains(TextUtil.toPlatformLineSeparators("[[$loglevel] [$category] $expectedMessage]"))

        where:
        loglevel | category                                               | action                                                               | expectedMessage
        "QUIET"  | "system.out"                                           | new StdOutSerializableLogAction("this is stdout")                    | "<Normal>this is stdout\n</Normal>"
        "ERROR"  | "system.err"                                           | new StdErrSerializableLogAction("this is stderr")                    | "<Normal>this is stderr\n</Normal>"
        "QUIET"  | "org.gradle.process.internal.LogSerializableLogAction" | new LogSerializableLogAction(LogLevel.QUIET, "quiet log statement")  | "quiet log statement"
        "INFO"   | "org.gradle.process.internal.LogSerializableLogAction" | new LogSerializableLogAction(LogLevel.INFO, "info log statement")    | "info log statement"
        "WARN"   | "org.gradle.process.internal.LogSerializableLogAction" | new LogSerializableLogAction(LogLevel.WARN, "warning log statement") | "warning log statement"
        "ERROR"  | "org.gradle.process.internal.LogSerializableLogAction" | new LogSerializableLogAction(LogLevel.ERROR, "error log statement")  | "error log statement"
    }

    def "worker process respects log level setting"() {
        given:
        LoggingProcess loggingProcess = new LoggingProcess(new LogSerializableLogAction(LogLevel.INFO, "info log statement"))
        String expectedLogStatement = "[[INFO] [org.gradle.process.internal.LogSerializableLogAction] info log statement]"

        when:
        workerFactory = new DefaultWorkerProcessFactory(
            loggingManager(LogLevel.LIFECYCLE),
            server,
            classPathRegistry,
            new LongIdGenerator(),
            gradleUserHome(),
            new GradleUserHomeTemporaryFileProvider({ gradleUserHome() }),
            execHandleFactory,
            new DefaultJvmVersionDetector(new CachingJvmMetadataDetector(defaultJvmMetadataDetector)),
            outputEventListener,
            Stub(MemoryManager)
        )
        and:
        execute(worker(loggingProcess))

        then:
        !outputEventListener.toString().contains(TextUtil.toPlatformLineSeparators(expectedLogStatement))

        when:
        workerFactory = new DefaultWorkerProcessFactory(
            loggingManager(LogLevel.INFO),
            server,
            classPathRegistry,
            new LongIdGenerator(),
            gradleUserHome(),
            new GradleUserHomeTemporaryFileProvider({ gradleUserHome() }),
            execHandleFactory,
            new DefaultJvmVersionDetector(new CachingJvmMetadataDetector(defaultJvmMetadataDetector)),
            outputEventListener,
            Stub(MemoryManager)
        )
        and:
        execute(worker(loggingProcess))

        then:
        outputEventListener.toString().contains(TextUtil.toPlatformLineSeparators(expectedLogStatement))
    }

    def workerProcessCanSendMessagesToThisProcess() {
        when:
        execute(worker(new RemoteProcess()))

        then:
        1 * listenerMock.send("message 1", 1)

        then:
        1 * listenerMock.send("message 2", 2)
        0 * listenerMock._
    }

    def thisProcessCanSendEventsToWorkerProcess() {
        when:
        execute(worker(new PingRemoteProcess()).onServer { objectConnection ->
            TestListenerInterface listener = objectConnection.addOutgoing(TestListenerInterface.class)
            listener.send("1", 0)
            listener.send("1", 1)
            listener.send("1", 2)
            listener.send("stop", 3)
        })

        then:
        noExceptionThrown()
    }

    def multipleWorkerProcessesCanSendMessagesToThisProcess() {
        when:
        execute(worker(new RemoteProcess()), worker(new OtherRemoteProcess()))

        then:
        1 * listenerMock.send("message 1", 1)
        1 * listenerMock.send("message 2", 2)
        1 * listenerMock.send("other 1", 1)
        1 * listenerMock.send("other 2", 2)
        0 * listenerMock._
    }

    def handlesWorkerProcessThatCrashes() {
        when:
        execute(worker(new CrashingRemoteProcess()).expectStopFailure())

        then:
        (0..1) * listenerMock.send("message 1", 1)
        (0..1) * listenerMock.send("message 2", 2)
        0 * listenerMock._

        and:
        stdout.stdOut == ""
        stdout.stdErr == ""
    }

    def handlesWorkerActionThatThrowsException() {
        when:
        execute(worker(new BrokenRemoteProcess()).expectStopFailure())

        then:
        stdout.stdOut == ""
        stdout.stdErr.contains("java.lang.RuntimeException: broken")
    }

    def handlesWorkerActionThatThrowsExceptionAndWhenMessagesAreSentToWorker() {
        when:
        execute(worker(new BrokenRemoteProcess()).expectStopFailure().onServer { objectConnection ->
            TestListenerInterface listener = objectConnection.addOutgoing(TestListenerInterface.class)
            listener.send("1", 0)
            listener.send("1", 1)
            listener.send("1", 2)
            listener.send("stop", 3)
        })

        then:
        stdout.stdOut == ""
        stdout.stdErr.contains("java.lang.RuntimeException: broken")
    }

    def handlesWorkerActionThatLeavesThreadsRunning() {
        when:
        execute(worker(new NoCleanUpRemoteProcess()))

        then:
        1 * listenerMock.send("message 1", 1)
        1 * listenerMock.send("message 2", 2)
        0 * listenerMock._
    }

    def handlesWorkerProcessThatNeverConnects() {
        when:
        workerFactory.setConnectTimeoutSeconds(3)
        execute(worker(Actions.doNothing()).jvmArgs("-Dorg.gradle.worker.test.stuck").expectStartFailure())

        then:
        stdout.stdOut == ""
        stdout.stdErr == ""
    }

    def handlesWorkerActionThatCannotBeDeserialized() {
        when:
        execute(worker(new NotDeserializable()).expectStartFailure())

        then:
        stdout.stdOut == ""
        stdout.stdErr.contains("java.io.IOException: Broken")
    }

    def handlesWorkerProcessWhenJvmFailsToStart() {
        when:
        execute(worker(Actions.doNothing()).jvmArgs("--broken").expectStartFailure())

        then:
        stdout.stdOut == ""
        stdout.stdErr.contains("--broken")
    }

    def "handles output after worker messaging services are stopped"() {
        when:
        execute(worker(new OutputOnShutdownHookProcess()))

        then:
        noExceptionThrown()
        stdout.stdOut.contains("Goodbye, world!")
        stdout.stdErr == ""
    }

    def "handles output during worker shutdown"() {
        when:
        execute(worker(new MessageProducingProcess()))

        then:
        stdout.stdErr == ""
    }

    @Requires(UnitTestPreconditions.NotWindows)
    def "handles output when worker fails before logging is started"() {
        when:
        execute(worker(new RemoteProcess()).jvmArgs("-Dorg.gradle.native.dir=/dev/null").expectStartFailure())

        then:
        stdout.stdOut == ""
        stdout.stdErr.contains("net.rubygrapefruit.platform.NativeException: Failed to load native library")
    }

    private class ChildProcess {
        private boolean stopFails
        private boolean startFails
        private WorkerProcess proc
        private Action<? super WorkerProcessContext> action
        private List<String> jvmArgs = Collections.emptyList()
        private Action<ObjectConnectionBuilder> serverAction

        public ChildProcess(Action<? super WorkerProcessContext> action) {
            this.action = action
        }

        ChildProcess expectStopFailure() {
            stopFails = true
            return this
        }

        ChildProcess expectStartFailure() {
            startFails = true
            return this
        }

        public void start() {
            WorkerProcessBuilder builder = workerFactory.create(action)
            builder.applicationClasspath(classPathRegistry.getClassPath("ANT").getAsFiles())
            builder.sharedPackages("org.apache.tools.ant")
            builder.javaCommand.systemProperty("test.system.property", "value")
            builder.javaCommand.environment("TEST_ENV_VAR", "value")

            builder.javaCommand.jvmArgs(jvmArgs)

            proc = builder.build()
            try {
                proc.start()
                assertFalse(startFails)
            } catch (ExecException e) {
                if (!startFails) {
                    throw new AssertionError(e)
                }
                return
            }
            proc.connection.addIncoming(TestListenerInterface.class, exceptionListener)
            if (serverAction != null) {
                serverAction.execute(proc.connection)
            }
            proc.connection.connect()
        }

        public void waitForStop() {
            if (startFails) {
                return
            }
            try {
                proc.waitForStop()
                assertFalse("Expected process to fail", stopFails)
            } catch (ExecException e) {
                assertTrue("Unexpected failure in worker process", stopFails)
            }
        }

        public ChildProcess onServer(Action<ObjectConnectionBuilder> action) {
            this.serverAction = action
            return this
        }

        public ChildProcess jvmArgs(String... jvmArgs) {
            this.jvmArgs = Arrays.asList(jvmArgs)
            return this
        }
    }
}

class StdOutSerializableLogAction extends SerializableLogAction {

    StdOutSerializableLogAction(String message) {
        super(message)
    }

    void execute() {
        System.out.println(message)
    }
}

class StdErrSerializableLogAction extends SerializableLogAction {
    StdErrSerializableLogAction(String message) {
        super(message)
    }

    void execute() {
        System.err.println(message)
    }
}

class LogSerializableLogAction extends SerializableLogAction {
    LogLevel logLevel

    LogSerializableLogAction(LogLevel logLevel, String message) {
        super(message)
        this.logLevel = logLevel
    }

    void execute() {
        Logging.getLogger(getClass()).log(logLevel, message)
    }
}


abstract class SerializableLogAction implements Serializable {
    final String message

    SerializableLogAction(String message) {
        this.message = message
    }

    abstract void execute()
}
