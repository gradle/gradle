package org.gradle.util.shutdown;

/**
 * @author Tom Eyckmans
 */
public interface ShutdownHookAction {
    void execute();
}
