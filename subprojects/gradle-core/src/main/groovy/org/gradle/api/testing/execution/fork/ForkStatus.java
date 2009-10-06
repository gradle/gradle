package org.gradle.api.testing.execution.fork;

/**
 * @author Tom Eyckmans
 */
public enum ForkStatus {
    INIT,
    TESTING,
    RESTART,
    STOPPED
}
