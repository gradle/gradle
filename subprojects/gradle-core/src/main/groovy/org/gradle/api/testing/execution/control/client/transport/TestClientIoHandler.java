/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

    private final static Logger LOGGER = LoggerFactory.getLogger(TestClientIoHandler.class);

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
                LOGGER.warn("received an unsupported message of type {}", messageClass);
            }
        }
    }

    @Override
    public void sessionIdle(IoSession session, IdleStatus status) {
        LOGGER.info("Disconnecting the idle.");
        // disconnect an idle client
        session.close(true);

        System.exit(1);
    }

    @Override
    public void exceptionCaught(IoSession session, Throwable cause) {
        // close the connection on exceptional situation
        LOGGER.error("Client error", cause);
        session.close(true);
    }
}
