package org.gradle.api.testing.reporting;

import org.gradle.api.testing.execution.PipelineListener;
import org.gradle.api.testing.execution.Pipeline;

/**
 * @author Tom Eyckmans
 */
public class ReportStopPipelineListener implements PipelineListener {

    private final ReportsManager reportsManager;

    public ReportStopPipelineListener(ReportsManager reportsManager) {
        if ( reportsManager == null ) throw new IllegalArgumentException("reportsManager == null!");

        this.reportsManager = reportsManager;
    }

    public void pipelineStopped(Pipeline pipeline) {
        reportsManager.pipelineStopped(pipeline);
    }
}
