/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.GradleException;
import org.gradle.api.testing.TestFrameworkRegister;
import org.gradle.api.testing.execution.control.messages.TestControlMessage;
import org.gradle.api.testing.execution.control.messages.server.ExecuteTestActionMessage;
import org.gradle.api.testing.execution.control.messages.server.InitializeActionMessage;
import org.gradle.api.testing.execution.control.messages.server.StopForkActionMessage;
import org.gradle.api.testing.execution.control.messages.server.WaitActionMesssage;
import org.gradle.api.testing.execution.control.refork.DataGatherControl;
import org.gradle.api.testing.execution.control.refork.DefaultDataGatherControl;
import org.gradle.api.testing.execution.control.refork.ReforkContextData;
import org.gradle.api.testing.execution.control.refork.ReforkReasonConfigs;
import org.gradle.api.testing.fabric.*;
import org.gradle.listener.dispatch.Dispatch;
import org.gradle.util.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Tom Eyckmans
 */
public class TestControlMessageDispatcher implements Dispatch<TestControlMessage> {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestControlMessageDispatcher.class);

    private final TestControlClient testControlClient;
    private final ClassLoader sandboxClassLoader;
    private final CountDownLatch exitReceived;

    private final ExecutorService threadPool;
    private TestProcessorFactory testProcessorFactory;
    private DataGatherControl dataGatherControl;
    private TestProcessResultFactory testProcessResultFactory;

    public TestControlMessageDispatcher(TestControlClient testControlClient, ClassLoader sandboxClassLoader) {
        this.testControlClient = testControlClient;
        this.sandboxClassLoader = sandboxClassLoader;
        this.exitReceived = new CountDownLatch(1);
        this.threadPool = Executors.newFixedThreadPool(1); // TODO future - multithreaded test execution.
    }

    public void dispatch(TestControlMessage testControlMessage) {
        if (testControlMessage instanceof ExecuteTestActionMessage) {
            final ExecuteTestActionMessage runTestResponse = (ExecuteTestActionMessage) testControlMessage;
            final TestClassRunInfo testInfo = runTestResponse.getTestClassRunInfo();

            final TestProcessor testProcessor = testProcessorFactory.createProcessor();

            final TestProcessorRunnable testProcessorRunnable = new TestProcessorRunnable(this, testProcessor, testInfo,
                    dataGatherControl, testProcessResultFactory);

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
            exitReceived.countDown();

            ThreadUtils.shutdown(threadPool);
        } else if (testControlMessage instanceof InitializeActionMessage) {
            final InitializeActionMessage initMessage = (InitializeActionMessage) testControlMessage;

            final String testFrameworkId = initMessage.getTestFrameworkId();
            final ReforkReasonConfigs reforkReasonConfigs = initMessage.getReforkItemConfigs();

            dataGatherControl = new DefaultDataGatherControl();

            dataGatherControl.initialize(reforkReasonConfigs);

            final TestFramework testFramework = TestFrameworkRegister.getTestFramework(testFrameworkId);
            testProcessorFactory = testFramework.getProcessorFactory();
            testProcessResultFactory = new TestProcessResultFactory();

            testProcessorFactory.initialize(sandboxClassLoader, testProcessResultFactory);

            actionExecuted(null, null);
        }
    }

    public void actionExecuted(TestClassProcessResult previousProcessTestResult,
                               ReforkContextData reforkContextData) {
        if (exitReceived.getCount() > 0) {
            testControlClient.requestNextControlMessage(previousProcessTestResult, reforkContextData);
        }
    }

    public void waitForExitReceived() {
        try {
            exitReceived.await();
        } catch (InterruptedException e) {
            throw new GradleException(e);
        }
    }
}
