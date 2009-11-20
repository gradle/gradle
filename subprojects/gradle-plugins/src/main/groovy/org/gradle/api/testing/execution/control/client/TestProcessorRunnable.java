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

    public TestProcessorRunnable(TestControlMessageDispatcher messageDispatcher, TestProcessor testProcessor,
                                 TestClassRunInfo testClassRunInfo, ReforkDataGatherControl reforkDataGatherControl,
                                 TestProcessResultFactory testProcessResultFactory) {
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
        } catch (Throwable t) {
            testProcessResult = testProcessResultFactory.createEmptyClassResult(testClassRunInfo);
            testProcessResult.setProcessorErrorReason(t);
        }

//        System.out.println("[fork] test " + testClassRunInfo.getTestClassName() + " run, gathering refork data");
        final ReforkDecisionContext reforkDecisionContext = reforkDataGatherControl.gatherData(
                DataGatherMoment.AFTER_TEST_EXECUTION, testProcessResult);

//        System.out.println("[fork] requesting next action");
        messageDispatcher.actionExecuted(testProcessResult, reforkDecisionContext);
    }
}
