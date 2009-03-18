package org.gradle.util.exec;

/**
 * @author Tom Eyckmans
 */
public enum ExecHandleState {
    INIT,
    STARTING,
    STARTED,
    ABORTED,
    FAILED,
    SUCCEEDED
}
