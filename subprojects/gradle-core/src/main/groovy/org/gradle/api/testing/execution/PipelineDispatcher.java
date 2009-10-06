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
package org.gradle.api.testing.execution;

import org.apache.mina.core.session.IoSession;
import org.gradle.api.tasks.testing.NativeTest;
import org.gradle.api.testing.execution.control.messages.TestControlMessageHandler;
import org.gradle.api.testing.execution.control.messages.server.InitializeActionMessage;
import org.gradle.api.testing.execution.control.messages.server.WaitActionMesssage;
import org.gradle.api.testing.execution.control.refork.ReforkController;
import org.gradle.api.testing.execution.control.refork.ReforkDecisionContext;
import org.gradle.api.testing.execution.control.server.TestServerClientHandle;
import org.gradle.api.testing.execution.fork.ForkControl;
import org.gradle.api.testing.execution.fork.ForkInfo;
import org.gradle.api.testing.fabric.TestClassRunInfo;
import org.gradle.util.ConditionWaitHandle;
import org.gradle.util.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Tom Eyckmans
 */
public class PipelineDispatcher {
    private static final Logger logger = LoggerFactory.getLogger(PipelineDispatcher.class);

    private final Pipeline pipeline;
    private final BlockingQueue<TestClassRunInfo> testsToDispatch;

    private final Map<Class<?>, TestControlMessageHandler> messageClassHandlers;

    private final Map<Integer, TestServerClientHandle> clientHandles;

    private final Lock doneLock;
    private final AtomicBoolean stopping;

    private final List<TestServerClientHandle> runningClients;
    private final Lock runningClientsLock;
    private final Condition allClientsStopped;

    public PipelineDispatcher(Pipeline pipeline) {
        this.pipeline = pipeline;
        this.messageClassHandlers = new HashMap<Class<?>, TestControlMessageHandler>();
        this.testsToDispatch = pipeline.getRunInfoQueue();
        this.clientHandles = new ConcurrentHashMap<Integer, TestServerClientHandle>();

        doneLock = new ReentrantLock();
        stopping = new AtomicBoolean(false);

        runningClients = new ArrayList<TestServerClientHandle>();
        runningClientsLock = new ReentrantLock();
        allClientsStopped = runningClientsLock.newCondition();
    }

    public void initialize(ForkControl forkControl) {
        for (ForkInfo forkInfo : forkControl.getForkInfos(pipeline.getId())) {
            clientHandles.put(forkInfo.getId(), new TestServerClientHandle(pipeline, forkInfo.getId(), forkControl));
            forkInfo.addListener(new PipelineDispatcherForkInfoListener(this));
        }
    }

    /**
     * Handle messages received from the fork.
     *
     * @param ioSession
     * @param message
     */
    public void messageReceived(IoSession ioSession, Object message) {
        if (message != null) {
//            System.out.println("[fork -> server] " + message);

            final Class<?> messageClass = message.getClass();
            final TestControlMessageHandler handler = messageClassHandlers.get(messageClass);

            if (handler == null) {
                logger.error("received unknown message of type {} on pipeline with id {} ", messageClass, pipeline.getId());
                ioSession.write(new WaitActionMesssage(pipeline.getId(), 100));
            } else {
                try {
                    handler.handle(ioSession, message);
                }
                catch (Throwable t) {
                    logger.error("failed to handle " + message, t);
                }
            }
        }
    }

    public Pipeline getPipeline() {
        return pipeline;
    }

    public boolean isPipelineSplittingEnded() {
        return pipeline.isPipelineSplittingEnded();
    }

    public boolean isAllTestsExecuted() {
        return testsToDispatch.isEmpty();
    }

    public void scheduleForkRestart(int forkId) {
        getClientHandle(forkId).scheduleForkRestart();
    }

    public void scheduleExecuteTest(int forkId) {
        getClientHandle(forkId).scheduleExecuteTest();
    }

    public TestServerClientHandle getClientHandle(int forkId) {
        return clientHandles.get(forkId);
    }

    public void setCurrentForkTest(int forkId, TestClassRunInfo currentTestClassRunInfo) {
        getClientHandle(forkId).setCurrentTest(currentTestClassRunInfo);
    }

    public TestClassRunInfo getCurrentForkTest(int forkId) {
        return getClientHandle(forkId).getCurrentTest();
    }

    public void addMessageHandler(List<Class> supportedMessageClasses, TestControlMessageHandler messageHandler) {
        for (final Class supportedMessageClass : supportedMessageClasses) {
            final TestControlMessageHandler replacedMessageHandler = messageClassHandlers.put(supportedMessageClass, messageHandler);

            logger.info("Message handler {} for {} has been replaced by {}", new Object[]{replacedMessageHandler, supportedMessageClass, messageHandler});
        }
    }

    public void forkStopped(int forkId) {
        getClientHandle(forkId).forkStopped();
    }

    public TestClassRunInfo getNextTest() {
        TestClassRunInfo testClassRunInfo = null;

        try {
            testClassRunInfo = testsToDispatch.poll(100L, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e) {
            // ignore
        }

        return testClassRunInfo;
    }

    public boolean determineReforkNeeded(int forkId, ReforkDecisionContext reforkDecisionContext) {
        final ReforkController reforkController = pipeline.getReforkController();

        boolean reforkNeeded = reforkController.reforkNeeded(reforkDecisionContext);

        if (reforkNeeded) {
            scheduleForkRestart(forkId);
        }

        return reforkNeeded;
    }

    public void initializeFork(int forkId, IoSession ioSession) {
        final InitializeActionMessage initializeForkMessage = new InitializeActionMessage(pipeline.getId());
        final NativeTest testTask = pipeline.getTestTask();

        initializeForkMessage.setTestFrameworkId(testTask.getTestFramework().getTestFramework().getId());
        initializeForkMessage.setReforkItemConfigs(pipeline.getConfig().getReforkItemConfigs());

        ioSession.write(initializeForkMessage);
    }

    public boolean isStopping() {
        return stopping.get();
    }

    public void stop() {
        doneLock.lock();
        try {
            if (!stopping.get()) {
                stopping.set(true);

                ThreadUtils.run(new Runnable() {
                    public void run() {
                        ThreadUtils.interleavedConditionWait(
                                runningClientsLock, allClientsStopped,
                                100L, TimeUnit.MILLISECONDS,
                                new ConditionWaitHandle() {
                                    public boolean checkCondition() {
                                        return runningClients.isEmpty();
                                    }

                                    public void conditionMatched() {
                                        pipeline.stopped();
                                    }
                                }
                        );
                    }
                });
            }
        }
        finally {
            doneLock.unlock();
        }
    }

    public void clientStarted(int forkId) {
        runningClientsLock.lock();
        try {
            final TestServerClientHandle client = clientHandles.get(forkId);
            if (client != null) {
                runningClients.add(client);
            } else {
                logger.error("clientStarted called for forkId(" + forkId + ") that is unrelated to test server off pipeline (" + pipeline.getId() + ")");
            }
        }
        finally {
            runningClientsLock.unlock();
        }
    }

    public void clientStopped(int forkId) {
        runningClientsLock.lock();
        try {
            final TestServerClientHandle client = clientHandles.get(forkId);
            if (client != null) {
                final boolean removed = runningClients.remove(client);

                if (!removed)
                    logger.error("clientStopped called for forkId(" + forkId + ") that was not in running clients for test server off pipeline (" + pipeline.getId() + ")");

                if (runningClients.isEmpty())
                    allClientsStopped.signal();
            } else {
                logger.error("clientStopped called for forkId(" + forkId + ") that is unrelated to test server off pipeline (" + pipeline.getId() + ")");
            }
        }
        finally {
            runningClientsLock.unlock();
        }
    }
}
