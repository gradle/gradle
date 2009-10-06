package org.gradle.api.testing.execution.control.client;

import org.gradle.api.testing.execution.control.refork.DataGatherMoment;
import org.gradle.api.testing.execution.control.refork.ReforkDataGatherControl;
import org.gradle.api.testing.execution.control.refork.ReforkDecisionContext;
import org.gradle.api.testing.fabric.TestClassProcessResult;
import org.gradle.api.testing.fabric.TestClassRunInfo;
import org.gradle.api.testing.fabric.TestProcessResultFactory;
import org.gradle.api.testing.fabric.TestProcessor;

/**
 * @author Tom Eyckmans
 */
public class TestProcessorRunnable implements Runnable {

    private final TestControlMessageDispatcher messageDispatcher;
    private final TestProcessor testProcessor;
    private final TestClassRunInfo testClassRunInfo;
    private final ReforkDataGatherControl reforkDataGatherControl;
    private final TestProcessResultFactory testProcessResultFactory;

    public TestProcessorRunnable(TestControlMessageDispatcher messageDispatcher, TestProcessor testProcessor, TestClassRunInfo testClassRunInfo, ReforkDataGatherControl reforkDataGatherControl, TestProcessResultFactory testProcessResultFactory) {
        this.messageDispatcher = messageDispatcher;
        this.testProcessor = testProcessor;
        this.testClassRunInfo = testClassRunInfo;
        this.reforkDataGatherControl = reforkDataGatherControl;
        this.testProcessResultFactory = testProcessResultFactory;
    }

    public void run() {
//        System.out.println("[fork] running test " + testClassRunInfo.getTestClassName());
        TestClassProcessResult testProcessResult = null;

        // TODO add control listeners
        try {
            testProcessResult = testProcessor.process(testClassRunInfo);
        }
        catch (Throwable t) {
            testProcessResult = testProcessResultFactory.createClassExecutionErrorResult(testClassRunInfo, t);
        }

//        System.out.println("[fork] test " + testClassRunInfo.getTestClassName() + " run, gathering refork data");
        final ReforkDecisionContext reforkDecisionContext = reforkDataGatherControl.gatherData(DataGatherMoment.AFTER_TEST_EXECUTION, testProcessResult);

//        System.out.println("[fork] requesting next action");
        messageDispatcher.actionExecuted(testProcessResult, reforkDecisionContext);
    }
}
