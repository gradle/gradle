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
        if (cause == null && exitCode != normalExitCode)
            cause = new BadExitCodeException("exit code was " + exitCode + " expected normal exit code " + normalExitCode);

        forkControl.forkFailed(pipelineId, forkId, cause);
    }
}
