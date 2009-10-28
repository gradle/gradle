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
        if( config == null ) throw new IllegalArgumentException("config == null!");
        if( reportPolicyInstance == null ) throw new IllegalArgumentException("reportPolicyInstance == null!");
        this.config = config;
        this.reportPolicyInstance = reportPolicyInstance;

        reportInfoQueue = new ArrayBlockingQueue<ReportInfo>(1000);
        reportInfoQueueProducer = new BlockingQueueItemProducer<ReportInfo>(reportInfoQueue, 100L, TimeUnit.MILLISECONDS);
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
