package org.gradle.api.testing.execution.control.server;

/**
 * @author Tom Eyckmans
 */
public interface TestControlServer {

    int start();

    void stop();

    int getPort();
}
