/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.testing.execution.Pipeline;
import org.gradle.api.testing.execution.PipelineDispatcher;
import org.gradle.api.testing.execution.control.messages.TestControlMessageHandler;
import org.gradle.api.testing.execution.control.messages.TestControlMessageHandlerFactory;
import org.gradle.api.testing.execution.control.server.messagehandlers.ForkStartedMessageHandlerFactory;
import org.gradle.api.testing.execution.control.server.messagehandlers.ForkStoppedMessageHandlerFactory;
import org.gradle.api.testing.execution.control.server.messagehandlers.NextActionRequestMessageHandlerFactory;
import org.gradle.api.testing.execution.control.server.transport.ExternalIoAcceptorFactory;
import org.gradle.api.testing.execution.control.server.transport.IoAcceptorFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Tom Eyckmans
 */
public class ExternalControlServerFactory implements ControlServerFactory {
    private static List<TestControlMessageHandlerFactory> messageHandlerFactories = new ArrayList<TestControlMessageHandlerFactory>();

    static {
        messageHandlerFactories.add(new ForkStartedMessageHandlerFactory());
        messageHandlerFactories.add(new ForkStoppedMessageHandlerFactory());
        messageHandlerFactories.add(new NextActionRequestMessageHandlerFactory());
    }

    public TestControlServer createTestControlServer(Pipeline pipeline) {
        final IoAcceptorFactory ioAcceptorFactory = new ExternalIoAcceptorFactory();

        final PipelineDispatcher pipelineDispatcher = new PipelineDispatcher(pipeline);

        pipeline.setDispatcher(pipelineDispatcher);

        for (TestControlMessageHandlerFactory messageHandlerFactory : messageHandlerFactories) {
            TestControlMessageHandler messageHandler = messageHandlerFactory.createTestControlMessageHandler(pipelineDispatcher);

            pipelineDispatcher.addMessageHandler(messageHandlerFactory.getMessageClasses(), messageHandler);
        }

        return new DefaultTestControlServer(ioAcceptorFactory, pipelineDispatcher);
    }
}
