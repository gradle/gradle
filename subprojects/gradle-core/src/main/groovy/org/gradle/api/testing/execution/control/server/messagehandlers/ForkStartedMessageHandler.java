package org.gradle.api.testing.execution.control.server.messagehandlers;

import org.apache.mina.core.session.IoSession;
import org.gradle.api.testing.execution.PipelineDispatcher;
import org.gradle.api.testing.execution.control.messages.client.ForkStartedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tom Eyckmans
 */
public class ForkStartedMessageHandler extends AbstractTestServerControlMessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(ForkStartedMessageHandler.class);

    protected ForkStartedMessageHandler(PipelineDispatcher pipelineDispatcher) {
        super(pipelineDispatcher);
    }

    public void handle(IoSession ioSession, Object controlMessage) {
        final ForkStartedMessage message = (ForkStartedMessage) controlMessage;
        final int forkId = message.getForkId();

        pipelineDispatcher.initializeFork(forkId, ioSession);
        pipelineDispatcher.scheduleExecuteTest(forkId);
    }
}
