package org.gradle.util.exec;

/**
 * @author Tom Eyckmans
 */
public interface ExecHandleListener {

    void executionStarted(ExecHandle execHandle);

    void executionFinished(ExecHandle execHandle);

    void executionAborted(ExecHandle execHandle);

    void executionFailed(ExecHandle execHandle);

}
