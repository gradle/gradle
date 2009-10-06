/*
 * Copyright 2007-2009 the original author or authors.
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
import org.gradle.api.testing.execution.fork.ForkControl;
import org.gradle.api.testing.execution.fork.ForkInfo;
import org.gradle.api.testing.execution.fork.ForkStatus;
import org.gradle.api.testing.execution.fork.policies.local.single.LocalSimpleForkPolicyForkInfo;
import org.gradle.api.testing.fabric.TestClassRunInfo;
import org.gradle.util.exec.ExecHandleState;

/**
 * @author Tom Eyckmans
 */
public class TestServerClientHandle {
    private final Pipeline pipeline;
    private final int forkId;
    private final ForkControl forkControl;

    private ForkStatus status = ForkStatus.INIT;
    private TestClassRunInfo currentTest;

    public TestServerClientHandle(Pipeline pipeline, int forkId, ForkControl forkControl) {
        this.pipeline = pipeline;
        this.forkId = forkId;
        this.forkControl = forkControl;
    }

    public ForkStatus getStatus() {
        return status;
    }

    public TestClassRunInfo getCurrentTest() {
        return currentTest;
    }

    public void setCurrentTest(TestClassRunInfo currentTest) {
        this.currentTest = currentTest;
    }

    public void scheduleForkRestart() {
        status = ForkStatus.RESTART;
        getForkInfo().setRestarting(true);
    }

    public void scheduleExecuteTest() {
        status = ForkStatus.TESTING;
        getForkInfo().setRestarting(false);
    }

    private ForkInfo getForkInfo() {
        return forkControl.getForkInfo(pipeline.getId(), forkId);
    }

    public void forkStopped() {
        final ForkInfo forkInfo = forkControl.getForkInfo(pipeline.getId(), forkId);
        final ExecHandleState forkEndState = ((LocalSimpleForkPolicyForkInfo) forkInfo.getForkPolicyInfo()).getForkHandle().waitForFinish();

        // TODO handle forkEndState of stopped fork

        if (!pipeline.isPipelineSplittingEnded())
            forkControl.requestForkStart(forkInfo);
    }
}
