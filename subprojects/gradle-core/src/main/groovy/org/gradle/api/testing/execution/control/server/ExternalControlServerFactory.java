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
