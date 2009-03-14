package org.gradle.util.exec;

import java.util.List;

/**
 * @author Tom Eyckmans
 */
public class ExecHandleAbortedNotifier extends ExecHandleNotifier {
    public ExecHandleAbortedNotifier(final ExecHandle execHandle) {
        super(execHandle);
    }

    protected boolean notifyListener(ExecHandle execHandle, ExecHandleListener listener) {
        listener.executionAborted(execHandle);
        return true;
    }
}
