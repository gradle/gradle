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
package org.gradle.api.testing.execution.fork;

import org.gradle.util.exec.BadExitCodeException;
import org.gradle.util.exec.ExecHandle;
import org.gradle.util.exec.ExecHandleListener;

/**
 * @author Tom Eyckmans
 */
public class ForkControlListener implements ExecHandleListener {
    private final ForkControl forkControl;
    private final int pipelineId;
    private final int forkId;

    public ForkControlListener(ForkControl forkControl, int pipelineId, int forkId) {
        this.forkControl = forkControl;
        this.pipelineId = pipelineId;
        this.forkId = forkId;
    }

    public void executionStarted(ExecHandle execHandle) {
        forkControl.forkStarted(pipelineId, forkId);
    }

    public void executionFinished(ExecHandle execHandle) {
        forkControl.forkFinished(pipelineId, forkId);
    }

    public void executionAborted(ExecHandle execHandle) {
        forkControl.forkAborted(pipelineId, forkId);
    }

    public void executionFailed(ExecHandle execHandle) {
        final int exitCode = execHandle.getExitCode();
        final int normalExitCode = execHandle.getNormalTerminationExitCode();

        Throwable cause = execHandle.getFailureCause();
        if (cause == null && exitCode != normalExitCode) {
            cause = new BadExitCodeException(
                    "exit code was " + exitCode + " expected normal exit code " + normalExitCode);
        }

        forkControl.forkFailed(pipelineId, forkId, cause);
    }
}
