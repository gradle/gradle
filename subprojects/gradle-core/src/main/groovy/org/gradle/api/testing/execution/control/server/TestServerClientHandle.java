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
package org.gradle.api.testing.execution.control.server;

import org.gradle.api.testing.execution.Pipeline;
import org.gradle.api.testing.execution.PipelineDispatcher;
import org.gradle.api.testing.execution.fork.ForkControl;
import org.gradle.api.testing.execution.fork.ForkStatus;
import org.gradle.api.testing.fabric.TestClassRunInfo;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Tom Eyckmans
 */
public class TestServerClientHandle {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestServerClientHandle.class);

    private final Pipeline pipeline;
    private final int forkId;
    private final ForkControl forkControl;

    private final Lock statusLock;
    private ForkStatus status = ForkStatus.STOPPED;
    private TestClassRunInfo currentTest;

    public TestServerClientHandle(Pipeline pipeline, int forkId, ForkControl forkControl) {
        this.pipeline = pipeline;
        this.forkId = forkId;
        this.forkControl = forkControl;
        statusLock = new ReentrantLock();
    }

    public ForkStatus getStatus() {
        statusLock.lock();
        try {
            return status;
        }
        finally {
            statusLock.unlock();
        }
    }

    public TestClassRunInfo getCurrentTest() {
        statusLock.lock();
        try {
            return currentTest;
        }
        finally {
            statusLock.unlock();
        }
    }

    public void setCurrentTest(TestClassRunInfo currentTest) {
        statusLock.lock();
        try {
            this.currentTest = currentTest;
        }
        finally {
            statusLock.unlock();
        }
    }

    public void starting()
    {
        statusLock.lock();
        try {
            if ( status != ForkStatus.STOPPED && status != ForkStatus.FAILED && status != ForkStatus.ABORTED && status != ForkStatus.RESTARTING )
                throw new IllegalArgumentException("can't change status to STARTING, current status is " + status);

            status = ForkStatus.STARTING;
        }
        finally {
            statusLock.unlock();
        }
    }

    public void restarting()
    {
        statusLock.lock();
        try {
            if ( status != ForkStatus.STARTED)
                throw new IllegalArgumentException("can't change status to RESTARTING, current status is " + status);

            status = ForkStatus.RESTARTING;
            forkControl.setRestarting(pipeline.getId(), forkId, true);
        }
        finally {
            statusLock.unlock();
        }
    }

    public void started()
    {
        statusLock.lock();
        try {
            if ( status != ForkStatus.STARTING && status != ForkStatus.RESTARTING )
                throw new IllegalArgumentException("can't change status to RUNNING, current status is " + status);

            status = ForkStatus.STARTED;
            forkControl.setRestarting(pipeline.getId(), forkId, false);
        }
        finally {
            statusLock.unlock();
        }
    }

    public void stopping()
    {
        statusLock.lock();
        try {
            if ( status != ForkStatus.RESTARTING ) {
                if ( status != ForkStatus.STARTED)
                    throw new IllegalStateException("can't change status to STOPPING, current status is " + status);

                status = ForkStatus.STOPPING;
            }
            // else stopping is ignored when restarting
        }
        finally {
            statusLock.unlock();
        }
    }

    public void stopped(PipelineDispatcher pipelineDispatcher)
    {
        statusLock.lock();
        try {
            if ( status != ForkStatus.STOPPING && status != ForkStatus.RESTARTING )
                throw new IllegalStateException("can't change status to STOPPED, current status is " + status);

            final ForkStatus previousStatus = status;
            status = ForkStatus.STOPPED;

            pipelineDispatcher.removeRunningHandle(this);

            signalAllClientsStoppedWhenNeeded(previousStatus, pipelineDispatcher);
        }
        finally {
            statusLock.unlock();
        }
    }

    public void failed(PipelineDispatcher pipelineDispatcher, Throwable cause)
    {
        statusLock.lock();
        try {
            if ( status != ForkStatus.STARTED && status != ForkStatus.STARTING && status != ForkStatus.RESTARTING && status != ForkStatus.STOPPING )
                throw new IllegalArgumentException("can't change status to FAILED, current status is " + status);

            final ForkStatus previousStatus = status;
            status = ForkStatus.FAILED;

            pipelineDispatcher.removeRunningHandle(this);

            signalAllClientsStoppedWhenNeeded(previousStatus, pipelineDispatcher);
        }
        finally {
            statusLock.unlock();
        }
    }

    public void aborted(PipelineDispatcher pipelineDispatcher)
    {
        statusLock.lock();
        try {
            if ( status != ForkStatus.STARTED && status != ForkStatus.STARTING && status != ForkStatus.RESTARTING && status != ForkStatus.STOPPING )
                throw new IllegalArgumentException("can't change status to ABORTED, current status is " + status);

            final ForkStatus previousStatus = status;
            status = ForkStatus.ABORTED;

            pipelineDispatcher.removeRunningHandle(this);

            signalAllClientsStoppedWhenNeeded(previousStatus, pipelineDispatcher);
        }
        finally {
            statusLock.unlock();
        }
    }

    public void signalAllClientsStoppedWhenNeeded(ForkStatus previousStatus, PipelineDispatcher pipelineDispatcher) {
        // TODO add check for other failed clients that need to re-run a test
        if ( previousStatus != ForkStatus.RESTARTING && pipelineDispatcher.areAllClientsStopped() && pipelineDispatcher.isStopping())
            pipelineDispatcher.allClientsStopped();
    }

    public void requestClientStart(PipelineDispatcher pipelineDispatcher) {
        if ( !pipelineDispatcher.isStopping() )
            forkControl.requestForkStart(pipeline.getId(), forkId);
    }

    public TestClassRunInfo nextTest(PipelineDispatcher pipelineDispatcher) {
        statusLock.lock();
        try {
            TestClassRunInfo nextTest = null;

            switch(status) {
                case FAILED: // TODO add re-launch failed fork policy, Never, FailFast, Fail after n crashes, ...?
                    nextTest = currentTest; // retry previous test
                    break;
                case STARTED:
                    try {
                        nextTest = pipelineDispatcher.nextTest();
                    }
                    catch (InterruptedException e) {
                        // ignore
                    }
                    break;
                // else no test case available
            }

            if ( pipeline.isPipelineSplittingEnded() && pipelineDispatcher.isAllTestsExecuted() )
                pipelineDispatcher.stop();

            return nextTest;
        }
        finally {
            statusLock.unlock();
        }
    }
}
