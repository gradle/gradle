package org.gradle.api.testing.execution.control.messages;

import org.apache.mina.core.session.IoSession;

/**
 * @author Tom Eyckmans
 */
public interface TestControlMessageHandler {
    void handle(IoSession ioSession, Object controlMessage);
}
