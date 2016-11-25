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
import org.gradle.internal.Actions
import org.gradle.internal.event.ListenerBroadcast
import org.gradle.internal.remote.ObjectConnectionBuilder
import org.gradle.process.internal.worker.WorkerProcess
import org.gradle.process.internal.worker.WorkerProcessBuilder
import org.gradle.process.internal.worker.WorkerProcessContext
import org.gradle.util.TextUtil
import spock.lang.Timeout

import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue

@Timeout(120)
class WorkerProcessIntegrationTest extends AbstractWorkerProcessIntegrationSpec {
    private final TestListenerInterface listenerMock = Mock(TestListenerInterface.class)
    private final ListenerBroadcast<TestListenerInterface> broadcast = new ListenerBroadcast<TestListenerInterface>(TestListenerInterface.class)
    private final RemoteExceptionListener exceptionListener = new RemoteExceptionListener(broadcast.source)

    def setup() {
        broadcast.add(listenerMock)
    }

    ChildProcess worker(Action<? super WorkerProcessContext> action) {
        return new ChildProcess(action)
    }

    void execute(ChildProcess... processes) throws Throwable {
        for (ChildProcess process : processes) {
            process.start();
        }
        for (ChildProcess process : processes) {
            process.waitForStop();
        }
        exceptionListener.rethrow();
    }

    def workerProcessStdoutAndStderrIsForwardedToThisProcess() {
        when:
        execute(worker(new LoggingProcess()))

        then:
        stdout.stdOut == TextUtil.toPlatformLineSeparators("this is stdout\n")
        stdout.stdErr.endsWith(TextUtil.toPlatformLineSeparators("this is stderr\n"))
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
        execute(worker(new PingRemoteProcess()).onServer(new Action<ObjectConnectionBuilder>() {
            public void execute(ObjectConnectionBuilder objectConnection) {
                TestListenerInterface listener = objectConnection.addOutgoing(TestListenerInterface.class)
                listener.send("1", 0)
                listener.send("1", 1)
                listener.send("1", 2)
                listener.send("stop", 3)
            }
        }))

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

    def handlesWorkerProcessWhichCrashes() {
        when:
        execute(worker(new CrashingRemoteProcess()).expectStopFailure())

        then:
        (0..1) * listenerMock.send("message 1", 1)
        (0..1) * listenerMock.send("message 2", 2)
        0 * listenerMock._
    }

    def handlesWorkerActionWhichThrowsException() {
        when:
        execute(worker(new BrokenRemoteProcess()).expectStopFailure())

        then:
        noExceptionThrown()
    }

    def handlesWorkerActionThatLeavesThreadsRunning() {
        when:
        execute(worker(new NoCleanUpRemoteProcess()))

        then:
        1 * listenerMock.send("message 1", 1)
        1 * listenerMock.send("message 2", 2)
        0 * listenerMock._
    }

    def handlesWorkerProcessWhichNeverConnects() {
        when:
        workerFactory.setConnectTimeoutSeconds(3)
        execute(worker(Actions.doNothing()).jvmArgs("-Dorg.gradle.worker.test.stuck").expectStartFailure())

        then:
        noExceptionThrown()
    }

    def handlesWorkerActionThatCannotBeDeserialized() {
        when:
        execute(worker(new NotDeserializable()).expectStopFailure())

        then:
        noExceptionThrown()
    }

    def handlesWorkerProcessWhenJvmFailsToStart() {
        when:
        execute(worker(Actions.doNothing()).jvmArgs("--broken").expectStartFailure())

        then:
        noExceptionThrown()
    }

    private class ChildProcess {
        private boolean stopFails;
        private boolean startFails;
        private WorkerProcess proc;
        private Action<? super WorkerProcessContext> action;
        private List<String> jvmArgs = Collections.emptyList();
        private Action<ObjectConnectionBuilder> serverAction;

        public ChildProcess(Action<? super WorkerProcessContext> action) {
            this.action = action;
        }

        ChildProcess expectStopFailure() {
            stopFails = true;
            return this;
        }

        ChildProcess expectStartFailure() {
            startFails = true;
            return this;
        }

        public void start() {
            WorkerProcessBuilder builder = workerFactory.create(action)
            builder.applicationClasspath(classPathRegistry.getClassPath("ANT").getAsFiles())
            builder.sharedPackages("org.apache.tools.ant")
            builder.javaCommand.systemProperty("test.system.property", "value")
            builder.javaCommand.environment("TEST_ENV_VAR", "value")

            builder.javaCommand.jvmArgs(jvmArgs);

            proc = builder.build();
            try {
                proc.start()
                assertFalse(startFails)
            } catch (ExecException e) {
                assertTrue(startFails)
                return;
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
