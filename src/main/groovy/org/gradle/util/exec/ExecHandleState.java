package org.gradle.util.exec;

/**
 * @author Tom Eyckmans
 */
public enum ExecHandleState {
    INIT,
    STARTED,
    ABORTED,
    FAILED,
    SUCCEEDED
}
