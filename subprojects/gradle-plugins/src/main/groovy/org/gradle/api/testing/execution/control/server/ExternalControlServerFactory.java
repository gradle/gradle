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

import org.gradle.api.testing.execution.PipelineDispatcher;
import org.gradle.api.testing.execution.control.server.messagehandlers.*;
import org.gradle.api.testing.execution.control.server.transport.ExternalIoAcceptorFactory;
import org.gradle.api.testing.execution.control.server.transport.IoAcceptorFactory;
import org.gradle.api.testing.execution.control.server.transport.TransportMessage;
import org.gradle.messaging.dispatch.Dispatch;

/**
 * @author Tom Eyckmans
 */
public class ExternalControlServerFactory implements ControlServerFactory {
    public TestControlServer createTestControlServer(final PipelineDispatcher pipelineDispatcher) {
        final IoAcceptorFactory ioAcceptorFactory = new ExternalIoAcceptorFactory();

        CompositeMessageHandler handler = new CompositeMessageHandler(pipelineDispatcher);
        handler.add(new ForkStartedMessageHandler(pipelineDispatcher));
        handler.add(new ForkStoppedMessageHandler(pipelineDispatcher));
        handler.add(new NextActionRequestMessageHandler(pipelineDispatcher));
        pipelineDispatcher.setMessageHandler(handler);

        return new DefaultTestControlServer(ioAcceptorFactory, new Dispatch<TransportMessage>() {
            public void dispatch(TransportMessage message) {
                pipelineDispatcher.messageReceived(message.getIoSession(), message.getMessage());
            }
        });
    }
}
