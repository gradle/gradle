package org.gradle.api.testing.reporting.policies;

import org.gradle.api.testing.reporting.ReportInfo;
import org.gradle.api.testing.reporting.Report;

/**
 * @author Tom Eyckmans
 */
public interface ReportPolicyInstance {

    void start();

    void process(ReportInfo reportInfo);

    void stop();

    void initialize(Report report);
}
