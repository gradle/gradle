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
package org.gradle.api.testing.execution.control.client;

import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.service.IoConnector;
import org.apache.mina.core.session.IoSession;
import org.gradle.api.GradleException;
import org.gradle.api.testing.execution.control.client.transport.IoConnectorFactory;
import org.gradle.api.testing.execution.control.client.transport.TestClientIoHandler;
import org.gradle.api.testing.execution.control.messages.TestControlMessage;
import org.gradle.api.testing.execution.control.messages.client.ForkStartedMessage;
import org.gradle.api.testing.execution.control.messages.client.ForkStoppedMessage;
import org.gradle.api.testing.execution.control.messages.client.NextActionRequestMessage;
import org.gradle.api.testing.execution.control.refork.ReforkContextData;
import org.gradle.api.testing.fabric.TestClassProcessResult;
import org.gradle.messaging.dispatch.Dispatch;

/**
 * @author Tom Eyckmans
 */
public class DefaultTestControlClient implements TestControlClient {
    private final int forkId;
    private final IoConnectorFactory ioConnectorFactory;
    private final Dispatch<TestControlMessage> dispatch;

    private IoConnector ioConnector;
    private IoSession ioSession;

    public DefaultTestControlClient(int forkId, IoConnectorFactory ioConnectorFactory,
                                    Dispatch<TestControlMessage> dispatch) {
        this.forkId = forkId;
        this.ioConnectorFactory = ioConnectorFactory;
        this.dispatch = dispatch;
    }

    public void open() {
        try {
            ioConnector = ioConnectorFactory.getIoConnector(new TestClientIoHandler(dispatch));

            final ConnectFuture connectFuture = ioConnector.connect(ioConnectorFactory.getSocketAddress());

            connectFuture.awaitUninterruptibly();

            ioSession = connectFuture.getSession();
        } catch (Throwable t) {
            throw new GradleException("failed to open test run progress client", t);
        }
    }

    public void close() {
        if (ioSession != null) {
            ioSession.close(false); // false = all requests are flushed.

            ioSession.getCloseFuture().awaitUninterruptibly();
        }
        if (ioConnector != null) {
            ioConnector.dispose();
        }
    }

    public void reportStarted() {
        ioSession.write(new ForkStartedMessage(forkId));
    }

    public void reportStopped() {
        ioSession.write(new ForkStoppedMessage(forkId));
    }

    public void requestNextControlMessage(TestClassProcessResult previousProcessTestResult,
                                          ReforkContextData reforkContextData) {
        final NextActionRequestMessage nextActionRequestMessage = new NextActionRequestMessage(forkId);

        nextActionRequestMessage.setPreviousProcessedTestResult(previousProcessTestResult);
        
        if ( !reforkContextData.isEmpty() ) {
            nextActionRequestMessage.setReforkDecisionContext(reforkContextData);
        }

        ioSession.write(nextActionRequestMessage);
    }
}
