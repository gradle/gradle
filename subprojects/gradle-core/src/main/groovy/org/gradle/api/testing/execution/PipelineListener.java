package org.gradle.api.testing.execution;

/**
 * @author Tom Eyckmans
 */
public interface PipelineListener {
    void pipelineStopped(Pipeline pipeline);
}
