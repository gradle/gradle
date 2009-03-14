package org.gradle.util.exec;

/**
 * @author Tom Eyckmans
 */
public class DefaultExecHandleNotifierFactory implements ExecHandleNotifierFactory {
    public ExecHandleNotifier createStartedNotifier(ExecHandle execHandle) {
        return new ExecHandleStartedNotifier(execHandle);
    }

    public ExecHandleNotifier createFailedNotifier(ExecHandle execHandle) {
        return new ExecHandleFailedNotifier(execHandle);
    }

    public ExecHandleNotifier createAbortedNotifier(ExecHandle execHandle) {
        return new ExecHandleAbortedNotifier(execHandle);
    }

    public ExecHandleNotifier createSucceededNotifier(ExecHandle execHandle) {
        return new ExecHandleSucceededNotifier(execHandle);
    }
}
