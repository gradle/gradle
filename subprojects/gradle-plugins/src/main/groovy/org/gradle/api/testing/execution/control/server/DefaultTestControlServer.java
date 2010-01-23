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
package org.gradle.api.testing.execution.control.server;

import org.apache.mina.core.service.IoAcceptor;
import org.gradle.api.GradleException;
import org.gradle.api.testing.execution.control.server.transport.IoAcceptorFactory;
import org.gradle.api.testing.execution.control.server.transport.TestServerIoHandler;
import org.gradle.api.testing.execution.control.server.transport.TransportMessage;
import org.gradle.messaging.dispatch.Dispatch;

import java.io.IOException;

/**
 * @author Tom Eyckmans
 */
public class DefaultTestControlServer implements TestControlServer {
    private final IoAcceptorFactory ioAcceptorFactory;
    private final TestServerIoHandler testServerIoHandler;

    private IoAcceptor ioAcceptor;
    private int port;

    public DefaultTestControlServer(IoAcceptorFactory ioAcceptorFactory, Dispatch<TransportMessage> dispatch) {
        this.ioAcceptorFactory = ioAcceptorFactory;
        this.testServerIoHandler = new TestServerIoHandler(dispatch);
    }

    public int start() {
        try {
            ioAcceptor = ioAcceptorFactory.getIoAcceptor(testServerIoHandler);
            port = ioAcceptorFactory.getLocalPort(ioAcceptor);
        } catch (IOException e) {
            throw new GradleException("failed to start test control server", e);
        }
        return port;
    }

    public void stop() {
        ioAcceptor.unbind();
    }

    public int getPort() {
        return port;
    }
}
