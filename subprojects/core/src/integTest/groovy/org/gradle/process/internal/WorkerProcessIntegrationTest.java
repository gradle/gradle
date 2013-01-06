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

package org.gradle.process.internal;

import org.apache.tools.ant.Project;
import org.gradle.CacheUsage;
import org.gradle.api.Action;
import org.gradle.api.internal.Actions;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.DefaultClassPathProvider;
import org.gradle.api.internal.DefaultClassPathRegistry;
import org.gradle.api.internal.classpath.DefaultModuleRegistry;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.file.TestFiles;
import org.gradle.api.logging.LogLevel;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.internal.*;
import org.gradle.internal.id.LongIdGenerator;
import org.gradle.internal.nativeplatform.ProcessEnvironment;
import org.gradle.internal.nativeplatform.services.NativeServices;
import org.gradle.listener.ListenerBroadcast;
import org.gradle.messaging.dispatch.Dispatch;
import org.gradle.messaging.dispatch.MethodInvocation;
import org.gradle.messaging.remote.MessagingServer;
import org.gradle.messaging.remote.ObjectConnection;
import org.gradle.messaging.remote.internal.MessagingServices;
import org.gradle.process.internal.child.WorkerProcessClassPathProvider;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.jmock.Expectations;
import org.jmock.Sequence;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(JMock.class)
public class WorkerProcessIntegrationTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final TestListenerInterface listenerMock = context.mock(TestListenerInterface.class);
    private final MessagingServices messagingServices = new MessagingServices(getClass().getClassLoader());
    private final MessagingServer server = messagingServices.get(MessagingServer.class);
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();
    private final ProcessMetaDataProvider metaDataProvider = new DefaultProcessMetaDataProvider(NativeServices.getInstance().get(ProcessEnvironment.class));
    private final CacheFactory factory = new DefaultCacheFactory(new DefaultFileLockManager(metaDataProvider)).create();
    private final CacheRepository cacheRepository = new DefaultCacheRepository(tmpDir.getTestDirectory(), null, CacheUsage.ON, factory);
    private final ModuleRegistry moduleRegistry = new DefaultModuleRegistry();
    private final ClassPathRegistry classPathRegistry = new DefaultClassPathRegistry(new DefaultClassPathProvider(moduleRegistry), new WorkerProcessClassPathProvider(cacheRepository, moduleRegistry));
    private final DefaultWorkerProcessFactory workerFactory = new DefaultWorkerProcessFactory(LogLevel.INFO, server, classPathRegistry, TestFiles.resolver(tmpDir.getTestDirectory()), new LongIdGenerator());
    private final ListenerBroadcast<TestListenerInterface> broadcast = new ListenerBroadcast<TestListenerInterface>(
            TestListenerInterface.class);
    private final RemoteExceptionListener exceptionListener = new RemoteExceptionListener(broadcast);

    @Before
    public void setUp() {
        broadcast.add(listenerMock);
    }

    @After
    public void tearDown() {
        messagingServices.stop();
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
        execute(worker(Actions.doNothing()).jvmArgs("--broken").expectStartFailure());
    }

    private ChildProcess worker(Action<? super WorkerProcessContext> action) {
        return new ChildProcess(action);
    }

    void execute(ChildProcess... processes) throws Throwable {
        for (ChildProcess process : processes) {
            process.start();
        }
        for (ChildProcess process : processes) {
            process.waitForStop();
        }
        messagingServices.stop();
        exceptionListener.rethrow();
    }

    private class ChildProcess {
        private boolean stopFails;
        private boolean startFails;
        private WorkerProcess proc;
        private Action<? super WorkerProcessContext> action;
        private List<String> jvmArgs = Collections.emptyList();
        private Action<ObjectConnection> serverAction;

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
            WorkerProcessBuilder builder = workerFactory.create();
            builder.applicationClasspath(classPathRegistry.getClassPath("ANT").getAsFiles());
            builder.sharedPackages("org.apache.tools.ant");
            builder.getJavaCommand().systemProperty("test.system.property", "value");
            builder.getJavaCommand().environment("TEST_ENV_VAR", "value");
            builder.worker(action);

            builder.getJavaCommand().jvmArgs(jvmArgs);

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

        public ChildProcess onServer(Action<ObjectConnection> action) {
            this.serverAction = action;
            return this;
        }

        public ChildProcess jvmArgs(String... jvmArgs) {
            this.jvmArgs = Arrays.asList(jvmArgs);
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
            try {
                assertThat(thisClassLoader.loadClass(Project.class.getName()), sameInstance((Object) Project.class));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }

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

