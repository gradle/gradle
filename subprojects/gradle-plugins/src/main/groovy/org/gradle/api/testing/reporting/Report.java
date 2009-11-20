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

import org.gradle.util.queues.BlockingQueueItemProducer;
import org.gradle.api.testing.reporting.policies.ReportPolicyInstance;
import org.gradle.api.testing.execution.Pipeline;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;

/**
 * @author Tom Eyckmans
 */
public class Report {
    private final ReportConfig config;
    private final ReportPolicyInstance reportPolicyInstance;
    private final BlockingQueue<ReportInfo> reportInfoQueue;
    private final BlockingQueueItemProducer<ReportInfo> reportInfoQueueProducer;
    private final List<Pipeline> pipelines;

    public Report(ReportConfig config, ReportPolicyInstance reportPolicyInstance) {
        if (config == null) {
            throw new IllegalArgumentException("config == null!");
        }
        if (reportPolicyInstance == null) {
            throw new IllegalArgumentException("reportPolicyInstance == null!");
        }
        this.config = config;
        this.reportPolicyInstance = reportPolicyInstance;

        reportInfoQueue = new ArrayBlockingQueue<ReportInfo>(1000);
        reportInfoQueueProducer = new BlockingQueueItemProducer<ReportInfo>(reportInfoQueue, 100L,
                TimeUnit.MILLISECONDS);
        pipelines = new ArrayList<Pipeline>();
    }

    public ReportConfig getConfig() {
        return config;
    }

    public BlockingQueue<ReportInfo> getReportInfoQueue() {
        return reportInfoQueue;
    }

    public ReportPolicyInstance getReportPolicyInstance() {
        return reportPolicyInstance;
    }

    public void addReportInfo(final ReportInfo reportInfo) {
        // TODO save to disk when full ?
        reportInfoQueueProducer.produce(reportInfo);
    }

    public List<Pipeline> getPipelines() {
        return pipelines;
    }

    public void addPipeline(Pipeline pipeline) {
        pipelines.add(pipeline);
    }

    public void removePipeline(Pipeline pipeline) {
        pipelines.remove(pipeline);
    }
}
