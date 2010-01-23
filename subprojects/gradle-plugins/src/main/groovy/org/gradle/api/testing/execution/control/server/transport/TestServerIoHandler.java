/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.testing.execution.control.server.transport;

import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.gradle.messaging.dispatch.Dispatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tom Eyckmans
 */
public class TestServerIoHandler extends IoHandlerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestServerIoHandler.class);

    private final Dispatch<TransportMessage> dispatch;

    public TestServerIoHandler(Dispatch<TransportMessage> dispatch) {
        this.dispatch = dispatch;
    }

    @Override
    public void messageReceived(IoSession ioSession, Object message) throws Exception {
        dispatch.dispatch(new TransportMessage(ioSession, message));
    }

    @Override
    public void sessionIdle(IoSession session, IdleStatus status) {
        LOGGER.info("session is idle (" + status + ")");
        // disconnect an idle client ?
//        session.close(true);
    }

    @Override
    public void exceptionCaught(IoSession session, Throwable cause) {
        // close the connection on exceptional situation
        LOGGER.error("server io error", cause);
        session.close(true);
    }
}
