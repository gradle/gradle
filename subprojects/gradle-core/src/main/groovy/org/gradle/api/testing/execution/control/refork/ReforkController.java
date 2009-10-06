package org.gradle.api.testing.execution.control.refork;

import org.gradle.api.tasks.testing.NativeTest;
import org.gradle.api.testing.execution.PipelineConfig;

/**
 * @author Tom Eyckmans
 */
public interface ReforkController {

    void initialize(NativeTest testTask, PipelineConfig pipelineConfig);

    boolean reforkNeeded(ReforkDecisionContext reforkDecisionContext);
}
