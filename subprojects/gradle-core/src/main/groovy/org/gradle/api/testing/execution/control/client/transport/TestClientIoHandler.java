package org.gradle.api.testing.execution.control.client.transport;

import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.gradle.api.testing.execution.control.messages.TestControlMessage;
import org.gradle.util.queues.BlockingQueueItemProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tom Eyckmans
 */
public class TestClientIoHandler extends IoHandlerAdapter {

    private final static Logger logger = LoggerFactory.getLogger(TestClientIoHandler.class);

    private final BlockingQueueItemProducer<TestControlMessage> testControlMessageProducer;

    public TestClientIoHandler(BlockingQueueItemProducer<TestControlMessage> testControlMessageProducer) {
        if (testControlMessageProducer == null)
            throw new IllegalArgumentException("testControlMessageProducer == null!");

        this.testControlMessageProducer = testControlMessageProducer;
    }

    @Override
    public void messageReceived(IoSession ioSession, Object message) throws Exception {
        if (message != null) {
            final Class messageClass = message.getClass();

            if (TestControlMessage.class.isAssignableFrom(messageClass)) {
                testControlMessageProducer.produce((TestControlMessage) message);
            } else {
                logger.warn("received an unsupported message of type {}", messageClass);
            }
        }
    }

    @Override
    public void sessionIdle(IoSession session, IdleStatus status) {
        logger.info("Disconnecting the idle.");
        // disconnect an idle client
        session.close(true);
    }

    @Override
    public void exceptionCaught(IoSession session, Throwable cause) {
        // close the connection on exceptional situation
        logger.error("Client error", cause);
        session.close(true);
    }
}
