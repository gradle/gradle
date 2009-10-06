package org.gradle.api.testing.execution.control.server.messagehandlers;

import org.apache.mina.core.session.IoSession;
import org.gradle.api.testing.execution.PipelineDispatcher;
import org.gradle.api.testing.execution.control.messages.client.ForkStoppedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tom Eyckmans
 */
public class ForkStoppedMessageHandler extends AbstractTestServerControlMessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(ForkStoppedMessageHandler.class);

    protected ForkStoppedMessageHandler(PipelineDispatcher pipelineDispatcher) {
        super(pipelineDispatcher);
    }

    public void handle(IoSession ioSession, Object controlMessage) {
        final ForkStoppedMessage message = (ForkStoppedMessage) controlMessage;
        final int forkId = message.getForkId();

        pipelineDispatcher.forkStopped(forkId);
    }
}
