package org.gradle.util.exec;

import java.util.List;

/**
 * @author Tom Eyckmans
 */
public class ExecHandleFailedNotifier extends ExecHandleNotifier {
    public ExecHandleFailedNotifier(final ExecHandle execHandle) {
        super(execHandle);
    }

    protected boolean notifyListener(ExecHandle execHandle, ExecHandleListener listener) {
        listener.executionFailed(execHandle);
        return true;
    }
}
