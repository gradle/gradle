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

package org.gradle.integtests;

import org.apache.tools.ant.Project;
import org.gradle.CacheUsage;
import org.gradle.api.Action;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.DefaultClassPathRegistry;
import org.gradle.api.internal.file.BaseDirConverter;
import org.gradle.api.logging.LogLevel;
import org.gradle.cache.DefaultCacheFactory;
import org.gradle.cache.DefaultCacheRepository;
import org.gradle.listener.ListenerBroadcast;
import org.gradle.messaging.remote.ObjectConnection;
import org.gradle.messaging.remote.internal.TcpMessagingServer;
import org.gradle.messaging.dispatch.Dispatch;
import org.gradle.messaging.dispatch.MethodInvocation;
import org.gradle.process.internal.*;
import org.gradle.process.internal.child.WorkerProcessClassPathProvider;
import org.gradle.util.LongIdGenerator;
import org.gradle.util.TemporaryFolder;
import org.jmock.Expectations;
import org.jmock.Sequence;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(JMock.class)
public class WorkerProcessIntegrationTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final TestListenerInterface listenerMock = context.mock(TestListenerInterface.class);
    private final TcpMessagingServer server = new TcpMessagingServer(getClass().getClassLoader());
    @Rule public final TemporaryFolder tmpDir = new TemporaryFolder();
    private final ClassPathRegistry classPathRegistry = new DefaultClassPathRegistry(new WorkerProcessClassPathProvider(new DefaultCacheRepository(tmpDir.getDir(), CacheUsage.ON, new DefaultCacheFactory())));
    private final DefaultWorkerProcessFactory workerFactory = new DefaultWorkerProcessFactory(LogLevel.INFO, server, classPathRegistry, new BaseDirConverter(tmpDir.getTestDir()), new LongIdGenerator());
    private final ListenerBroadcast<TestListenerInterface> broadcast = new ListenerBroadcast<TestListenerInterface>(
            TestListenerInterface.class);
    private final RemoteExceptionListener exceptionListener = new RemoteExceptionListener(broadcast);

    @Before
    public void setUp() {
        broadcast.add(listenerMock);
    }

    @Test
    public void workerProcessCanSendMessagesToThisProcess() throws Throwable {
        context.checking(new Expectations() {{
            Sequence sequence = context.sequence("sequence");
            one(listenerMock).send("message 1", 1);
            inSequence(sequence);
            one(listenerMock).send("message 2", 2);
            inSequence(sequence);
        }});

        execute(worker(new RemoteProcess()));
    }

    @Test
    public void thisProcessCanSendEventsToWorkerProcess() throws Throwable {
        execute(worker(new PingRemoteProcess()).onServer(new Action<ObjectConnection>() {
            public void execute(ObjectConnection objectConnection) {
                TestListenerInterface listener = objectConnection.addOutgoing(TestListenerInterface.class);
                listener.send("1", 0);
                listener.send("1", 1);
                listener.send("1", 2);
                listener.send("stop", 3);
            }
        }));
    }

    @Test
    public void multipleWorkerProcessesCanSendMessagesToThisProcess() throws Throwable {
        context.checking(new Expectations() {{
            Sequence process1 = context.sequence("sequence1");
            one(listenerMock).send("message 1", 1);
            inSequence(process1);
            one(listenerMock).send("message 2", 2);
            inSequence(process1);
            Sequence process2 = context.sequence("sequence2");
            one(listenerMock).send("other 1", 1);
            inSequence(process2);
            one(listenerMock).send("other 2", 2);
            inSequence(process2);
        }});

        execute(worker(new RemoteProcess()), worker(new OtherRemoteProcess()));
    }

    @Test
    public void handlesWorkerProcessWhichCrashes() throws Throwable {
        context.checking(new Expectations() {{
            atMost(1).of(listenerMock).send("message 1", 1);
            atMost(1).of(listenerMock).send("message 2", 2);
        }});

        execute(worker(new CrashingRemoteProcess()).expectStopFailure());
    }

    @Test
    public void handlesWorkerActionWhichThrowsException() throws Throwable {
        execute(worker(new BrokenRemoteProcess()).expectStopFailure());
    }

    @Test
    public void handlesWorkerActionThatLeavesThreadsRunning() throws Throwable {
        context.checking(new Expectations() {{
            one(listenerMock).send("message 1", 1);
            one(listenerMock).send("message 2", 2);
        }});

        execute(worker(new NoCleanUpRemoteProcess()));
    }

    @Test
    public void handlesWorkerProcessWhichNeverConnects() throws Throwable {
        execute(worker(new NoConnectRemoteProcess()).expectStartFailure());
    }

    @Test
    public void handlesWorkerProcessWhenJvmFailsToStart() throws Throwable {
        execute(mainClass("no-such-class").expectStartFailure());
    }

    private ChildProcess worker(Action<WorkerProcessContext> action) {
        return new ChildProcess(action);
    }

    private ChildProcess mainClass(String mainClass) {
        return new ChildProcess(new NoOpAction()).mainClass(mainClass);
    }

    void execute(ChildProcess... processes) throws Throwable {
        for (ChildProcess process : processes) {
            process.start();
        }
        for (ChildProcess process : processes) {
            process.waitForStop();
        }
        server.stop();
        exceptionListener.rethrow();
    }

    private class ChildProcess {
        private boolean stopFails;
        private boolean startFails;
        private WorkerProcess proc;
        private Action<WorkerProcessContext> action;
        private String mainClass;
        private Action<ObjectConnection> serverAction;

        public ChildProcess(Action<WorkerProcessContext> action) {
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
            WorkerProcessBuilder builder = workerFactory.create();
            builder.applicationClasspath(classPathRegistry.getClassPathFiles("ANT"));
            builder.sharedPackages("org.apache.tools.ant");
            builder.getJavaCommand().systemProperty("test.system.property", "value");
            builder.getJavaCommand().environment("TEST_ENV_VAR", "value");
            builder.worker(action);

            if (mainClass != null) {
                builder.getJavaCommand().setMain(mainClass);
            }

            proc = builder.build();
            try {
                proc.start();
                assertFalse(startFails);
            } catch (ExecException e) {
                assertTrue(startFails);
                return;
            }
            proc.getConnection().addIncoming(TestListenerInterface.class, exceptionListener);
            if (serverAction != null) {
                serverAction.execute(proc.getConnection());
            }
        }

        public void waitForStop() {
            if (startFails) {
                return;
            }
            try {
                proc.waitForStop();
                assertFalse("Expected process to fail", stopFails);
            } catch (ExecException e) {
                assertTrue("Unexpected failure in worker process", stopFails);
            }
        }

        public ChildProcess mainClass(String mainClass) {
            this.mainClass = mainClass;
            return this;
        }

        public ChildProcess onServer(Action<ObjectConnection> action) {
            this.serverAction = action;
            return this;
        }
    }

    public static class RemoteExceptionListener implements Dispatch<MethodInvocation> {
        Throwable ex;
        final Dispatch<MethodInvocation> dispatch;

        public RemoteExceptionListener(Dispatch<MethodInvocation> dispatch) {
            this.dispatch = dispatch;
        }

        public void dispatch(MethodInvocation message) {
            try {
                dispatch.dispatch(message);
            } catch (Throwable e) {
                ex = e;
            }
        }

        public void rethrow() throws Throwable {
            if (ex != null) {
                throw ex;
            }
        }
    }

    public static class RemoteProcess implements Action<WorkerProcessContext>, Serializable {
        public void execute(WorkerProcessContext workerProcessContext) {
            // Check environment
            assertThat(System.getProperty("test.system.property"), equalTo("value"));
            assertThat(System.getenv().get("TEST_ENV_VAR"), equalTo("value"));

            // Check ClassLoaders
            ClassLoader antClassLoader = Project.class.getClassLoader();
            ClassLoader thisClassLoader = getClass().getClassLoader();
            ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();

            assertThat(antClassLoader, not(sameInstance(systemClassLoader)));
            assertThat(thisClassLoader, not(sameInstance(systemClassLoader)));
            assertThat(antClassLoader.getParent(), equalTo(systemClassLoader.getParent()));
            assertThat(thisClassLoader.getParent().getParent().getParent(), sameInstance(antClassLoader));

            // Send some messages
            TestListenerInterface sender = workerProcessContext.getServerConnection().addOutgoing(
                    TestListenerInterface.class);
            sender.send("message 1", 1);
            sender.send("message 2", 2);
        }
    }

    public static class OtherRemoteProcess implements Action<WorkerProcessContext>, Serializable {
        public void execute(WorkerProcessContext workerProcessContext) {
            TestListenerInterface sender = workerProcessContext.getServerConnection().addOutgoing(TestListenerInterface.class);
            sender.send("other 1", 1);
            sender.send("other 2", 2);
        }
    }

    public static class NoCleanUpRemoteProcess implements Action<WorkerProcessContext>, Serializable {
        public void execute(WorkerProcessContext workerProcessContext) {
            final Lock lock = new ReentrantLock();
            lock.lock();
            new Thread(new Runnable() {
                public void run() {
                    lock.lock();
                }
            }).start();

            TestListenerInterface sender = workerProcessContext.getServerConnection().addOutgoing(
                    TestListenerInterface.class);
            sender.send("message 1", 1);
            sender.send("message 2", 2);
        }
    }

    public static class PingRemoteProcess implements Action<WorkerProcessContext>, Serializable, TestListenerInterface {
        CountDownLatch stopReceived;
        int count;

        public void send(String message, int count) {
            assertEquals(this.count, count);
            this.count++;
            if (message.equals("stop")) {
                assertEquals(4, this.count);
                stopReceived.countDown();
            }
        }

        public void execute(WorkerProcessContext workerProcessContext) {
            stopReceived = new CountDownLatch(1);
            workerProcessContext.getServerConnection().addIncoming(TestListenerInterface.class, this);
            try {
                stopReceived.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class CrashingRemoteProcess implements Action<WorkerProcessContext>, Serializable {
        public void execute(WorkerProcessContext workerProcessContext) {
            TestListenerInterface sender = workerProcessContext.getServerConnection().addOutgoing(TestListenerInterface.class);
            sender.send("message 1", 1);
            sender.send("message 2", 2);
            // crash
            Runtime.getRuntime().halt(1);
        }
    }

    public static class BrokenRemoteProcess implements Action<WorkerProcessContext>, Serializable {
        public void execute(WorkerProcessContext workerProcessContext) {
            throw new RuntimeException("broken");
        }
    }

    public static class NoOpAction implements Action<WorkerProcessContext>, Serializable {
        public void execute(WorkerProcessContext workerProcessContext) {
        }
    }

    public static class NoConnectRemoteProcess implements Action<WorkerProcessContext>, Serializable {
        private void readObject(ObjectInputStream instr) {
            System.exit(0);
        }
        
        public void execute(WorkerProcessContext workerProcessContext) {
            throw new UnsupportedOperationException();
        }
    }
    
    public interface TestListenerInterface {
        public void send(String message, int count);
    }
}

