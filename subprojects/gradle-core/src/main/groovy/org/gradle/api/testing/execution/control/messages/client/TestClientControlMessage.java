package org.gradle.api.testing.execution.control.messages.client;

import org.gradle.api.testing.execution.control.messages.TestControlMessage;

/**
 * @author Tom Eyckmans
 */
public interface TestClientControlMessage extends TestControlMessage {
    int getForkId();
}
