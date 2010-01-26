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

import org.gradle.listener.ListenerBroadcast;
import org.gradle.listener.remote.RemoteSender;
import org.gradle.messaging.MessagingServer;
import org.gradle.messaging.ObjectConnection;
import org.gradle.messaging.TcpMessagingServer;
import org.gradle.messaging.dispatch.Dispatch;
import org.gradle.messaging.dispatch.MethodInvocation;
import org.gradle.util.Jvm;
import org.gradle.util.exec.ExecHandle;
import org.gradle.util.exec.ExecHandleBuilder;
import org.gradle.util.exec.ExecHandleState;
import org.jmock.Expectations;
import org.jmock.Sequence;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.URI;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(JMock.class)
public class RemoteListenerIntegrationTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final TestListenerInterface listenerMock = context.mock(TestListenerInterface.class);
    private final Server server = new Server();

    @Test
    public void remoteProcessCanSendEventsToThisProcess() throws Throwable {
        context.checking(new Expectations() {{
            Sequence sequence = context.sequence("sequence");
            one(listenerMock).send("message 1", 1);
            inSequence(sequence);
            one(listenerMock).send("message 2", 2);
            inSequence(sequence);
        }});

        execute(mainClass(RemoteProcess.class));
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

        execute(mainClass(RemoteProcess.class), mainClass(OtherRemoteProcess.class));
    }

    @Test
    public void handlesRemoteProcessWhichCrashes() throws Throwable {
        context.checking(new Expectations() {{
            atMost(1).of(listenerMock).send("message 1", 1);
            atMost(1).of(listenerMock).send("message 2", 2);
        }});

        execute(mainClass(CrashingRemoteProcess.class).expectFailure());
    }

    @Test
    public void handlesRemoteProcessWhichNeverConnects() throws Throwable {
        execute(mainClass(NoConnectRemoteProcess.class));
    }

    @Test
    public void handlesRemoteProcessWhenJvmFailsToStart() throws Throwable {
        execute(mainClass("no-such-class").expectFailure());
    }

    private ChildProcess mainClass(Class<?> mainClass) {
        return mainClass(mainClass.getName());
    }

    private ChildProcess mainClass(String mainClass) {
        return new ChildProcess(mainClass);
    }

    void execute(ChildProcess... processes) throws Throwable {
        server.start();
        for (ChildProcess process : processes) {
            process.start();
        }
        for (ChildProcess process : processes) {
            process.waitForStop();
        }
        server.stop();
    }

    private class Server {
        private MessagingServer server;
        private RemoteExceptionListener exceptionListener;

        public void start() {
            ListenerBroadcast<TestListenerInterface> broadcast = new ListenerBroadcast<TestListenerInterface>(
                    TestListenerInterface.class);
            broadcast.add(listenerMock);
            exceptionListener = new RemoteExceptionListener(broadcast);

            server = new TcpMessagingServer(TestListenerInterface.class.getClassLoader());
        }

        public URI newIncomingConnection() {
            ObjectConnection connection = server.createUnicastConnection();
            connection.addIncoming(TestListenerInterface.class, exceptionListener);
            return connection.getLocalAddress();
        }

        public void stop() throws Throwable {
            server.stop();
            exceptionListener.rethrow();
        }
    }

    private class ChildProcess {
        private final String mainClass;
        private boolean fails;
        private ExecHandle proc;

        public ChildProcess(String mainClass) {
            this.mainClass = mainClass;
        }

        ChildProcess expectFailure() {
            fails = true;
            return this;
        }

        public void start() {
            ExecHandleBuilder builder = new ExecHandleBuilder();
            builder.execCommand(Jvm.current().getJavaExecutable());
            builder.arguments("-cp", System.getProperty("java.class.path"));
            builder.arguments(mainClass, String.valueOf(server.newIncomingConnection()));

            proc = builder.getExecHandle();
            proc.start();
        }

        public void waitForStop() {
            proc.waitForFinish();
            ExecHandleState result = proc.getState();
            if (!fails) {
                assertThat(result, equalTo(ExecHandleState.SUCCEEDED));
            } else {
                assertThat(result, not(equalTo(ExecHandleState.SUCCEEDED)));
            }
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

    public static class RemoteProcess {
        public static void main(String[] args) {
            try {
                RemoteSender<TestListenerInterface> remoteSender = new RemoteSender<TestListenerInterface>(
                        TestListenerInterface.class, new URI(args[0]));
                TestListenerInterface sender = remoteSender.getSource();
                sender.send("message 1", 1);
                sender.send("message 2", 2);
                remoteSender.close();
            } catch (Throwable t) {
                t.printStackTrace();
                System.exit(1);
            }
        }
    }

    public static class OtherRemoteProcess {
        public static void main(String[] args) {
            try {
                RemoteSender<TestListenerInterface> remoteSender = new RemoteSender<TestListenerInterface>(
                        TestListenerInterface.class, new URI(args[0]));
                TestListenerInterface sender = remoteSender.getSource();
                sender.send("other 1", 1);
                sender.send("other 2", 2);
                remoteSender.close();
            } catch (Throwable t) {
                t.printStackTrace();
                System.exit(1);
            }
        }
    }

    public static class CrashingRemoteProcess {
        public static void main(String[] args) {
            try {
                RemoteSender<TestListenerInterface> remoteSender = new RemoteSender<TestListenerInterface>(
                        TestListenerInterface.class, new URI(args[0]));
                TestListenerInterface sender = remoteSender.getSource();
                sender.send("message 1", 1);
                sender.send("message 2", 2);
                // crash
                Runtime.getRuntime().halt(1);
            } catch (Throwable t) {
                t.printStackTrace();
                System.exit(1);
            }
        }
    }

    public static class NoConnectRemoteProcess {
        public static void main(String[] args) {
        }
    }
    
    public interface TestListenerInterface {
        public void send(String message, int count);
    }
}

