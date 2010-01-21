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
import org.gradle.listener.remote.RemoteReceiver;
import org.gradle.listener.remote.RemoteSender;
import org.gradle.util.exec.ExecHandle;
import org.gradle.util.exec.ExecHandleBuilder;
import org.gradle.util.exec.ExecHandleState;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static junit.framework.Assert.*;

public class RemoteListenerIntegrationTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private static final String FIRST_MESSAGE_TEXT = "test message";
    private static final int    FIRST_MESSAGE_INT  = 1;
    private static final String SECOND_MESSAGE_TEXT = "test message (again)";
    private static final int    SECOND_MESSAGE_INT  = 2;

    @Test
    public void tryRemoteSenderAndReceiver() throws Throwable {
        final TestListenerInterface listenerMock = context.mock(TestListenerInterface.class);
        context.checking(new Expectations() {{
            one(listenerMock).send(FIRST_MESSAGE_TEXT, FIRST_MESSAGE_INT);
            one(listenerMock).send(SECOND_MESSAGE_TEXT, SECOND_MESSAGE_INT);
        }});

        ListenerBroadcast<TestListenerInterface> broadcast = new ListenerBroadcast<TestListenerInterface>(TestListenerInterface.class);
        broadcast.add(listenerMock);
        RemoteExceptionListener exceptionListener = new RemoteExceptionListener();
        RemoteReceiver receiver = new RemoteReceiver(broadcast, exceptionListener, getClass().getClassLoader());

        executeJava(RemoteProcess.class.getName(), receiver.getBoundPort());
        if (exceptionListener.ex != null) {
            throw exceptionListener.ex;
        }
        receiver.close();
        context.assertIsSatisfied();
    }

    public static class RemoteExceptionListener implements RemoteReceiver.ExceptionListener {
        Throwable ex;

        public void receiverThrewException(Throwable throwable) {
            ex = throwable;
        }
    }

    public static class RemoteProcess {
        public static void main(String[] args) throws IOException {
            int port = Integer.parseInt(args[0]);
            RemoteSender<TestListenerInterface> remoteSender = new RemoteSender<TestListenerInterface>(
                    TestListenerInterface.class, port);
            TestListenerInterface sender = remoteSender.getSource();
            sender.send(FIRST_MESSAGE_TEXT, FIRST_MESSAGE_INT);
            sender.send(SECOND_MESSAGE_TEXT, SECOND_MESSAGE_INT);
            remoteSender.close();
        }
    }

    private void executeJava(String mainClass, int port) {
        ExecHandleBuilder builder = new ExecHandleBuilder();
        builder.execDirectory(new File(System.getProperty("user.dir")));
        builder.execCommand(new File(System.getProperty("java.home")+"/bin/java").getPath());
        builder.arguments("-cp", System.getProperty("java.class.path"));

        builder.arguments(mainClass, String.valueOf(port));

        ExecHandle proc = builder.getExecHandle();
        ExecHandleState result = proc.startAndWaitForFinish();
        assertTrue(result == ExecHandleState.SUCCEEDED);
    }

    public interface TestListenerInterface {
        public void send(String message, int count);
    }
}

