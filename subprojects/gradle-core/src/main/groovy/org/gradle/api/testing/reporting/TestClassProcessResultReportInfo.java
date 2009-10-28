package org.gradle.api.testing.reporting;

import org.gradle.api.testing.execution.Pipeline;
import org.gradle.api.testing.fabric.TestClassProcessResult;

/**
 * @author Tom Eyckmans
 */
public class TestClassProcessResultReportInfo implements ReportInfo {
    private final Pipeline pipeline;
    private final TestClassProcessResult testClassProcessResult;

    public TestClassProcessResultReportInfo(Pipeline pipeline, TestClassProcessResult testClassProcessResult) {
        this.pipeline = pipeline;
        this.testClassProcessResult = testClassProcessResult;
    }

    public Pipeline getPipeline() {
        return pipeline;
    }

    public TestClassProcessResult getTestClassProcessResult() {
        return testClassProcessResult;
    }
}
