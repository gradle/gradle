package org.gradle.util.exec;

import java.util.List;

/**
 * @author Tom Eyckmans
 */
public class ExecHandleStartedNotifier extends ExecHandleNotifier {
    public ExecHandleStartedNotifier(final ExecHandle execHandle) {
        super(execHandle);
    }

    protected boolean notifyListener(ExecHandle execHandle, ExecHandleListener listener) {
        listener.executionStarted(execHandle);
        return true;
    }
}