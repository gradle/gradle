package org.gradle.api.testing.execution.control.messages.server;

import org.gradle.api.testing.execution.control.messages.TestControlMessage;

/**
 * @author Tom Eyckmans
 */
public interface TestServerControlMessage extends TestControlMessage {
    int getPipelineId();
}
