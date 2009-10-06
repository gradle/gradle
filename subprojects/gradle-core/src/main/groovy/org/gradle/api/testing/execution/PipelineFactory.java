package org.gradle.api.testing.execution;

import org.gradle.api.tasks.testing.NativeTest;

/**
 * @author Tom Eyckmans
 */
public class PipelineFactory {
    private final NativeTest testTask;

    public PipelineFactory(NativeTest testTask) {
        this.testTask = testTask;
    }

    public Pipeline createPipeline(PipelinesManager manager, int id, PipelineConfig pipelineConfig) {
        return new Pipeline(manager, id, testTask, pipelineConfig);
    }
}
