package org.gradle.api.testing.execution.control.server.messagehandlers;

import org.gradle.api.testing.execution.PipelineDispatcher;
import org.gradle.api.testing.execution.control.messages.TestControlMessageHandler;
import org.gradle.api.testing.execution.control.messages.TestControlMessageHandlerFactory;
import org.gradle.api.testing.execution.control.messages.client.ForkStoppedMessage;

import java.util.Arrays;
import java.util.List;

/**
 * @author Tom Eyckmans
 */
public class ForkStoppedMessageHandlerFactory implements TestControlMessageHandlerFactory {
    private static final List<Class> supportedMessageClasses = Arrays.asList((Class) ForkStoppedMessage.class);

    public List<Class> getMessageClasses() {
        return supportedMessageClasses;
    }

    public TestControlMessageHandler createTestControlMessageHandler(PipelineDispatcher pipelineDispatcher) {
        return new ForkStoppedMessageHandler(pipelineDispatcher);
    }
}
