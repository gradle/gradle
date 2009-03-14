package org.gradle.util.exec;

/**
 * @author Tom Eyckmans
 */
public interface ExecHandleNotifierFactory {

    ExecHandleNotifier createStartedNotifier(ExecHandle execHandle);

    ExecHandleNotifier createFailedNotifier(ExecHandle execHandle);

    ExecHandleNotifier createAbortedNotifier(ExecHandle execHandle);

    ExecHandleNotifier createSucceededNotifier(ExecHandle execHandle);

}
