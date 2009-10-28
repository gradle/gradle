package org.gradle.api.testing.reporting;

import org.gradle.api.testing.execution.Pipeline;

/**
 * @author Tom Eyckmans
 */
public interface ReportInfo {
    Pipeline getPipeline();

}
