package org.gradle.api.testing.execution.control.server;

import org.gradle.api.testing.execution.Pipeline;

/**
 * @author Tom Eyckmans
 */
public interface ControlServerFactory {
    TestControlServer createTestControlServer(Pipeline pipeline);
}
