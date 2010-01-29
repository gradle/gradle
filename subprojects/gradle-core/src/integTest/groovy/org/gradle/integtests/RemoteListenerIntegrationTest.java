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
import org.gradle.api.Action;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.DefaultClassPathRegistry;
import org.gradle.listener.ListenerBroadcast;
import org.gradle.messaging.TcpMessagingServer;
import org.gradle.messaging.dispatch.Dispatch;
import org.gradle.messaging.dispatch.MethodInvocation;
import org.gradle.process.DefaultWorkerProcessFactory;
import org.gradle.process.WorkerProcess;
import org.gradle.process.WorkerProcessBuilder;
import org.gradle.process.WorkerProcessContext;
import org.gradle.util.exec.ExecHandleState;
import org.jmock.Expectations;
import org.jmock.Sequence;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ObjectInputStream;
import java.io.Serializable;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(JMock.class)
public class RemoteListenerIntegrationTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final TestListenerInterface listenerMock = context.mock(TestListenerInterface.class);
    private final TcpMessagingServer server = new TcpMessagingServer(getClass().getClassLoader());
    private final ClassPathRegistry classPathRegistry = new DefaultClassPathRegistry();
    private final DefaultWorkerProcessFactory workerFactory = new DefaultWorkerProcessFactory(server, classPathRegistry);
    private final ListenerBroadcast<TestListenerInterface> broadcast = new ListenerBroadcast<TestListenerInterface>(
            TestListenerInterface.class);
    private final RemoteExceptionListener exceptionListener = new RemoteExceptionListener(broadcast);

    @Before
    public void setUp() {
        broadcast.add(listenerMock);
    }

    @Test
    public void remoteProcessCanSendEventsToThisProcess() throws Throwable {
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
    public void multipleProcessesCanSendEventsToThisProcess() throws Throwable {
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
    public void handlesRemoteProcessWhichCrashes() throws Throwable {
        context.checking(new Expectations() {{
            atMost(1).of(listenerMock).send("message 1", 1);
            atMost(1).of(listenerMock).send("message 2", 2);
        }});

        execute(worker(new CrashingRemoteProcess()).expectFailure());
    }

    @Test
    public void handlesRemoteProcessWhichNeverConnects() throws Throwable {
        execute(worker(new NoConnectRemoteProcess()));
    }

    @Test
    public void handlesRemoteProcessWhenJvmFailsToStart() throws Throwable {
        execute(mainClass("no-such-class").expectFailure());
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
        private boolean fails;
        private WorkerProcess proc;
        private Action<WorkerProcessContext> action;
        private String mainClass;

        public ChildProcess(Action<WorkerProcessContext> action) {
            this.action = action;
        }

        ChildProcess expectFailure() {
            fails = true;
            return this;
        }

        public void start() {
            WorkerProcessBuilder builder = workerFactory.newProcess();
            builder.applicationClasspath(classPathRegistry.getClassPathFiles("ANT"));
            builder.sharedPackages("org.apache.tools.ant");
            builder.worker(action);

            if (mainClass != null) {
                builder.getJavaCommand().mainClass(mainClass);
            }

            proc = builder.build();
            proc.getConnection().addIncoming(TestListenerInterface.class, exceptionListener);
            proc.start();
        }

        public void waitForStop() {
            proc.waitForStop();
            ExecHandleState result = proc.getState();
            if (!fails) {
                assertThat(result, equalTo(ExecHandleState.SUCCEEDED));
            } else {
                assertThat(result, not(equalTo(ExecHandleState.SUCCEEDED)));
            }
        }

        public ChildProcess mainClass(String mainClass) {
            this.mainClass = mainClass;
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
            // Check ClassLoaders
            ClassLoader antClassLoader = Project.class.getClassLoader();
            ClassLoader thisClassLoader = getClass().getClassLoader();
            ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();

            assertThat(antClassLoader, not(sameInstance(systemClassLoader)));
            assertThat(thisClassLoader, not(sameInstance(systemClassLoader)));
            assertThat(antClassLoader.getParent(), equalTo(systemClassLoader.getParent()));
            assertThat(thisClassLoader.getParent().getParent(), sameInstance(antClassLoader));

            // Send some messages
            TestListenerInterface sender = workerProcessContext.getServerConnection().addOutgoing(TestListenerInterface.class);
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

    public static class CrashingRemoteProcess implements Action<WorkerProcessContext>, Serializable {
        public void execute(WorkerProcessContext workerProcessContext) {
            TestListenerInterface sender = workerProcessContext.getServerConnection().addOutgoing(TestListenerInterface.class);
            sender.send("message 1", 1);
            sender.send("message 2", 2);
            // crash
            Runtime.getRuntime().halt(1);
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

