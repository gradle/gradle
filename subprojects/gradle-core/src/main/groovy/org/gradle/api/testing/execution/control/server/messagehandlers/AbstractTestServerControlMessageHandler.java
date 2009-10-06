package org.gradle.api.testing.execution.control.server.messagehandlers;

import org.gradle.api.testing.execution.Pipeline;
import org.gradle.api.testing.execution.PipelineDispatcher;
import org.gradle.api.testing.execution.control.messages.TestControlMessageHandler;

/**
 * @author Tom Eyckmans
 */
public abstract class AbstractTestServerControlMessageHandler implements TestControlMessageHandler {
    protected final PipelineDispatcher pipelineDispatcher;
    protected final Pipeline pipeline;

    protected AbstractTestServerControlMessageHandler(PipelineDispatcher pipelineDispatcher) {
        if (pipelineDispatcher == null) throw new IllegalArgumentException("pipelineDispatcher == null!");

        this.pipelineDispatcher = pipelineDispatcher;
        this.pipeline = pipelineDispatcher.getPipeline();
    }
}
