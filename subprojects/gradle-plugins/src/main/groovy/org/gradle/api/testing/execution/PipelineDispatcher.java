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
package org.gradle.api.testing.execution;

import org.apache.mina.core.session.IoSession;
import org.gradle.api.testing.execution.control.messages.TestControlMessageHandler;
import org.gradle.api.testing.execution.control.messages.client.TestClientControlMessage;
import org.gradle.api.testing.execution.control.messages.server.StopForkActionMessage;
import org.gradle.api.testing.execution.control.messages.server.WaitActionMesssage;
import org.gradle.api.testing.execution.control.server.TestServerClientHandle;
import org.gradle.api.testing.execution.control.server.TestServerClientHandleFactory;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineDispatcher.class);

    private final QueueingPipeline pipeline;
    private final BlockingQueue<TestClassRunInfo> testsToDispatch;

    private final Map<Class<?>, TestControlMessageHandler> messageClassHandlers;

    private final Map<Integer, TestServerClientHandle> clientHandles;

    private final Lock doneLock;
    private final AtomicBoolean stopping;

    private final TestServerClientHandleFactory clientHandleFactory;
    private final List<TestServerClientHandle> runningClients;
    private final Lock runningClientsLock;
    private final Condition allClientsStopped;

    public PipelineDispatcher(QueueingPipeline pipeline, TestServerClientHandleFactory clientHandleFactory) {
        this.pipeline = pipeline;
        this.clientHandleFactory = clientHandleFactory;
        this.messageClassHandlers = new HashMap<Class<?>, TestControlMessageHandler>();
        this.testsToDispatch = pipeline.getRunInfoQueue();
        this.clientHandles = new ConcurrentHashMap<Integer, TestServerClientHandle>();

        doneLock = new ReentrantLock();
        stopping = new AtomicBoolean(false);

        runningClients = new ArrayList<TestServerClientHandle>();
        runningClientsLock = new ReentrantLock();
        allClientsStopped = runningClientsLock.newCondition();
    }

    /**
     * Handle messages received from the fork.
     */
    public void messageReceived(IoSession ioSession, Object message) {
        if (message != null) {
            final Class<?> messageClass = message.getClass();
            final TestControlMessageHandler handler = messageClassHandlers.get(messageClass);

            if (handler != null) {
                try {
                    final int forkId = ((TestClientControlMessage) message).getForkId();
                    final TestServerClientHandle client = clientHandles.get(forkId);
                    if (client == null) {
                        ioSession.write(new WaitActionMesssage(pipeline.getId(), 1000L));
                    } else {
                        handler.handle(ioSession, message, client);
                    }
                } catch (Throwable t) {
                    LOGGER.error("failed to handle " + message, t);
                }
            } else {
                LOGGER.error("received unknown message of type {} on pipeline ", messageClass,
                        pipeline.getName());
                ioSession.write(new StopForkActionMessage(pipeline.getId()));
            }
        }
    }

    public QueueingPipeline getPipeline() {
        return pipeline;
    }

    public boolean isPipelineSplittingEnded() {
        return pipeline.isPipelineSplittingEnded();
    }

    public boolean isAllTestsExecuted() {
        return testsToDispatch.isEmpty();
    }

    public void addMessageHandler(List<Class> supportedMessageClasses, TestControlMessageHandler messageHandler) {
        for (final Class supportedMessageClass : supportedMessageClasses) {
            messageClassHandlers.put(supportedMessageClass, messageHandler);
        }
    }

    public void forkAttach(int forkId) {
        TestServerClientHandle client = clientHandles.get(forkId);
        if (client == null) {
            client = clientHandleFactory.createTestServerClientHandle(pipeline, forkId);
            clientHandles.put(forkId, client);
        }
    }

    public void forkStarting(int forkId) {
        runningClientsLock.lock();
        try {
            TestServerClientHandle client = clientHandles.get(forkId);

            client.starting();

            runningClients.add(client);
        } finally {
            runningClientsLock.unlock();
        }
    }

    public void forkStopped(int forkId) {
        runningClientsLock.lock();

        TestServerClientHandle client = null;
        try {
            client = clientHandles.get(forkId);

            client.stopped(this);
        } finally {
            runningClientsLock.unlock();
        }

        client.requestClientStart(this);
    }

    public void forkFailed(int forkId, Throwable cause) {
        runningClientsLock.lock();

        TestServerClientHandle client = null;
        try {
            client = clientHandles.get(forkId);

            client.failed(this, cause);
        } finally {
            runningClientsLock.unlock();
        }

        client.requestClientStart(this);
    }

    public void forkAborted(int forkId) {
        runningClientsLock.lock();

        TestServerClientHandle client = null;
        try {
            client = clientHandles.get(forkId);

            client.aborted(this);
        } finally {
            runningClientsLock.unlock();
        }
    }

    public boolean isStopping() {
        return stopping.get();
    }

    public void stop() {
        stopping.set(true);

        ThreadUtils.run(new Runnable() {
            public void run() {
                ThreadUtils.interleavedConditionWait(runningClientsLock, allClientsStopped, 100L, TimeUnit.MILLISECONDS,
                        new ConditionWaitHandle() {
                            public boolean checkCondition() {
                                return runningClients.isEmpty();
                            }

                            public void conditionMatched() {
                                pipeline.stopped();
                            }
                        });
            }
        });
    }

    public TestClassRunInfo nextTest() throws InterruptedException {
        return testsToDispatch.poll(100L, TimeUnit.MILLISECONDS);
    }

    public boolean areAllClientsStopped() {
        return runningClients.isEmpty();
    }

    public void allClientsStopped() {
        if (!stopping.get()) {
            throw new IllegalStateException("pipeline dispatcher is not stopping!");
        }
        allClientsStopped.signal();
    }

    public void removeRunningHandle(TestServerClientHandle clientHandle) {
        runningClients.remove(clientHandle);
    }
}
