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

import org.gradle.api.testing.TestFrameworkRegister;
import org.gradle.api.testing.execution.control.messages.TestControlMessage;
import org.gradle.api.testing.execution.control.messages.server.ExecuteTestActionMessage;
import org.gradle.api.testing.execution.control.messages.server.InitializeActionMessage;
import org.gradle.api.testing.execution.control.messages.server.StopForkActionMessage;
import org.gradle.api.testing.execution.control.messages.server.WaitActionMesssage;
import org.gradle.api.testing.execution.control.refork.ReforkDataGatherControl;
import org.gradle.api.testing.execution.control.refork.ReforkDecisionContext;
import org.gradle.api.testing.execution.control.refork.ReforkItemConfigs;
import org.gradle.api.testing.fabric.*;
import org.gradle.util.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Tom Eyckmans
 */
public class TestControlMessageDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestControlMessageDispatcher.class);

    private final TestControlClient testControlClient;
    private final ClassLoader sandboxClassLoader;
    private final AtomicBoolean exitReceived;

    private final ExecutorService threadPool;
    private TestProcessorFactory testProcessorFactory;
    private ReforkDataGatherControl reforkDataGatherControl;
    private TestProcessResultFactory testProcessResultFactory;

    public TestControlMessageDispatcher(TestControlClient testControlClient, ClassLoader sandboxClassLoader) {
        this.testControlClient = testControlClient;
        this.sandboxClassLoader = sandboxClassLoader;
        this.exitReceived = new AtomicBoolean(false);
        this.threadPool = Executors.newFixedThreadPool(1); // TODO future - multithreaded test execution.
    }

    public boolean dispatch(TestControlMessage testControlMessage) {
        if (testControlMessage instanceof ExecuteTestActionMessage) {
            final ExecuteTestActionMessage runTestResponse = (ExecuteTestActionMessage) testControlMessage;
            final TestClassRunInfo testInfo = runTestResponse.getTestClassRunInfo();

            final TestProcessor testProcessor = testProcessorFactory.createProcessor();

            final TestProcessorRunnable testProcessorRunnable = new TestProcessorRunnable(this, testProcessor, testInfo,
                    reforkDataGatherControl, testProcessResultFactory);

            threadPool.submit(testProcessorRunnable);
        } else if (testControlMessage instanceof WaitActionMesssage) {
            final WaitActionMesssage waitMessage = (WaitActionMesssage) testControlMessage;

            LOGGER.debug("waiting for {} ms - for tests to become available", waitMessage.getTimeToWait());

            try {
                Thread.sleep(waitMessage.getTimeToWait());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            actionExecuted(null, null);
        } else if (testControlMessage instanceof StopForkActionMessage) {
            exitReceived.set(true);

            ThreadUtils.shutdown(threadPool);
        } else if (testControlMessage instanceof InitializeActionMessage) {
            final InitializeActionMessage initMessage = (InitializeActionMessage) testControlMessage;

            final String testFrameworkId = initMessage.getTestFrameworkId();
            final ReforkItemConfigs reforkItemConfigs = initMessage.getReforkItemConfigs();

            reforkDataGatherControl = new ReforkDataGatherControl();

            reforkDataGatherControl.initialize(reforkItemConfigs);

            final TestFramework testFramework = TestFrameworkRegister.getTestFramework(testFrameworkId);
            testProcessorFactory = testFramework.getProcessorFactory();
            testProcessResultFactory = new TestProcessResultFactory();

            testProcessorFactory.initialize(sandboxClassLoader, testProcessResultFactory);

            actionExecuted(null, null);
        }

        return exitReceived.get();
    }

    public void actionExecuted(TestClassProcessResult previousProcessTestResult,
                               ReforkDecisionContext reforkDecisionContext) {
        if (!exitReceived.get()) {
            testControlClient.requestNextControlMessage(previousProcessTestResult, reforkDecisionContext);
        }
    }
}
