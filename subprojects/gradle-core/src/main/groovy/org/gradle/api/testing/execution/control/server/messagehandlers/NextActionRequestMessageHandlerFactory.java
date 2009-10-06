package org.gradle.api.testing.execution.control.server.messagehandlers;

import org.gradle.api.testing.execution.PipelineDispatcher;
import org.gradle.api.testing.execution.control.messages.TestControlMessageHandler;
import org.gradle.api.testing.execution.control.messages.TestControlMessageHandlerFactory;
import org.gradle.api.testing.execution.control.messages.client.NextActionRequestMessage;

import java.util.Arrays;
import java.util.List;

/**
 * @author Tom Eyckmans
 */
public class NextActionRequestMessageHandlerFactory implements TestControlMessageHandlerFactory {
    private static final List<Class> supportedMessageClasses = Arrays.asList((Class) NextActionRequestMessage.class);

    public List<Class> getMessageClasses() {
        return supportedMessageClasses;
    }

    public TestControlMessageHandler createTestControlMessageHandler(PipelineDispatcher pipelineDispatcher) {
        return new NextActionRequestMessageHandler(pipelineDispatcher);
    }
}
