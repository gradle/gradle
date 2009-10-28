/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.testing.reporting;

import org.gradle.api.tasks.testing.NativeTest;
import org.gradle.api.testing.reporting.policies.*;
import org.gradle.api.testing.execution.PipelinesManager;
import org.gradle.api.testing.execution.PipelineConfig;
import org.gradle.api.testing.execution.Pipeline;
import org.gradle.api.testing.execution.PipelineListener;
import org.gradle.util.ThreadUtils;
import org.gradle.util.ConditionWaitHandle;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * @author Tom Eyckmans
 */
public class DefaultReportsManager implements ReportsManager {

    // String = report name
    private final Map<String, Report> reports;

    private final List<Report> notStopped;
    private final Lock allStoppedLock;
    private final Condition allStoppedCondition;

    public DefaultReportsManager() {
        reports = new HashMap<String, Report>();

        notStopped = new CopyOnWriteArrayList<Report>();
        allStoppedLock = new ReentrantLock();
        allStoppedCondition = allStoppedLock.newCondition();
    }

    public void initialize(NativeTest testTask, PipelinesManager pipelinesManager) {

        final Map<String, ReportConfig> reportConfigs = testTask.getReportConfigs();
        for (final ReportConfig reportConfig : reportConfigs.values() ) {
            final ReportPolicyConfig reportPolicyConfig = reportConfig.getPolicyConfig();
            final ReportPolicy reportPolicy = ReportPolicyRegister.getReportPolicy(reportPolicyConfig.getPolicyName());
            final ReportPolicyInstance reportPolicyInstance = reportPolicy.createReportPolicyInstance();

            final Report report = new Report(reportConfig, reportPolicyInstance);

            reports.put(reportConfig.getName(), report);
            notStopped.add(report);

            reportPolicyInstance.initialize(report);
        }

        final List<Pipeline> pipelines = pipelinesManager.getPipelines();
        for ( final Pipeline pipeline : pipelines ) {
            final List<ReportConfig> pipelineReportConfigs = pipeline.getConfig().getReports();

            for(final ReportConfig pipelineReportConfig : pipelineReportConfigs ) {
                final Report pipelineReport = reports.get(pipelineReportConfig.getName());

                if ( pipelineReport != null )
                    pipeline.addReport(pipelineReport);
                // TODO else -> warning or error ? 
            }

            pipeline.addListener(new ReportStopPipelineListener(this));
        }
    }

    public void startReporting() {
        for ( final Report report : reports.values() ) {
            report.getReportPolicyInstance().start();
        }
    }

    public void waitForReportEnd() {
        ThreadUtils.interleavedConditionWait(
                allStoppedLock,
                allStoppedCondition,
                100L, TimeUnit.MILLISECONDS,
                new ConditionWaitHandle() {
                    public boolean checkCondition() {
                        return notStopped.isEmpty();
                    }

                    public void conditionMatched() {
                        // nothing - just return
                    }
                }
        );

        for ( final Report report : reports.values() ) {
            report.getReportPolicyInstance().stop();
        }
    }

    public void pipelineStopped(Pipeline pipeline) {
        allStoppedLock.lock();
        try {
            final List<Report> reports = pipeline.getReports();

            for ( final Report report : reports ) {
                report.removePipeline(pipeline);

                if ( report.getPipelines().isEmpty() ) {
                    notStopped.remove(report);

                    if ( notStopped.isEmpty() )
                        allStoppedCondition.signal();
                }
            }
        }
        finally {
            allStoppedLock.unlock();
        }
    }
}
