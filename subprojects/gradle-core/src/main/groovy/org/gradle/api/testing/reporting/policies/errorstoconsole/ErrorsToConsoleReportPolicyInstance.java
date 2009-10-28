package org.gradle.api.testing.reporting.policies.errorstoconsole;

import org.gradle.api.testing.reporting.policies.ReportPolicyInstance;
import org.gradle.api.testing.reporting.ReportInfo;
import org.gradle.api.testing.reporting.Report;
import org.gradle.api.testing.reporting.TestClassProcessResultReportInfo;
import org.gradle.api.testing.execution.Pipeline;
import org.gradle.api.testing.fabric.TestClassProcessResult;
import org.gradle.api.testing.fabric.TestMethodProcessResult;
import org.gradle.util.queues.AbstractBlockingQueueItemConsumer;
import org.gradle.util.ThreadUtils;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.List;

/**
 * @author Tom Eyckmans
 */
public class ErrorsToConsoleReportPolicyInstance implements ReportPolicyInstance {

    private static Logger logger = LoggerFactory.getLogger(ErrorsToConsoleReportPolicyInstance.class);

    private Report report;
    private ReportInfoQueueItemConsumer consumer;
    private Thread consumerThread;

    public void start() {
        consumer = new ReportInfoQueueItemConsumer(report.getReportInfoQueue(), 100L, TimeUnit.MILLISECONDS, this);
        
        consumerThread = ThreadUtils.run(consumer);
    }

    public void process(ReportInfo reportInfo) {
        if (TestClassProcessResultReportInfo.class == reportInfo.getClass()) {
            final TestClassProcessResultReportInfo testClassInfo = (TestClassProcessResultReportInfo)reportInfo;

            final Pipeline pipeline = testClassInfo.getPipeline();
            final TestClassProcessResult classResult = testClassInfo.getTestClassProcessResult();
            final List<TestMethodProcessResult> methodResults = classResult.getMethodResults();
            long successCount = 0;
            long failureCount = 0;
            long errorCount = 0;
            long skippedCount = 0;
            for ( final TestMethodProcessResult methodResult : methodResults ) {
                switch(methodResult.getState()){
                    case SUCCESS:successCount++;break;
                    case FAILURE:failureCount++;break;
                    case ERROR:errorCount++;break;
                    case SKIPPED:skippedCount++;break;
                }
            }

            if ( failureCount > 0 || errorCount > 0 ) {
                logger.warn("Test {} on pipeline failed: success#{}, failure#{}, error#{}, skipped#{}", new Object[]{
                        classResult.getTestClassRunInfo().getTestClassName(),
                        pipeline.getConfig().getName(),
                        successCount, failureCount, errorCount, skippedCount});
            }
        }
        // else unsupported reportInfo -> warning ?
    }

    public void stop() {
        consumer.stopConsuming();
        
        ThreadUtils.join(consumerThread);
    }

    public void initialize(Report report) {
        this.report = report;
    }

    private class ReportInfoQueueItemConsumer extends AbstractBlockingQueueItemConsumer<ReportInfo> {

        private final ReportPolicyInstance reportPolicyInstance;

        public ReportInfoQueueItemConsumer(BlockingQueue<ReportInfo> toConsumeQueue, long pollTimeout, TimeUnit pollTimeoutTimeUnit, ReportPolicyInstance reportPolicyInstance) {
            super(toConsumeQueue, pollTimeout, pollTimeoutTimeUnit);
            this.reportPolicyInstance = reportPolicyInstance;
        }

        protected boolean consume(ReportInfo queueItem) {
            reportPolicyInstance.process(queueItem);
            
            return true;
        }
    }
}
