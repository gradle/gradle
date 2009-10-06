package org.gradle.api.testing.execution.control.messages;

import org.gradle.api.testing.execution.PipelineDispatcher;

import java.util.List;

/**
 * @author Tom Eyckmans
 */
public interface TestControlMessageHandlerFactory {
    List<Class> getMessageClasses();

    TestControlMessageHandler createTestControlMessageHandler(PipelineDispatcher pipelineDispatcher);
}
