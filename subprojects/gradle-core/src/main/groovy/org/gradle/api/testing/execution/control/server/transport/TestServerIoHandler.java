package org.gradle.api.testing.execution.control.server.transport;

import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.gradle.api.testing.execution.PipelineDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tom Eyckmans
 */
public class TestServerIoHandler extends IoHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(TestServerIoHandler.class);

    private final PipelineDispatcher pipelineDispatcher;

    public TestServerIoHandler(PipelineDispatcher pipelineDispatcher) {
        this.pipelineDispatcher = pipelineDispatcher;
    }

    @Override
    public void messageReceived(IoSession ioSession, Object message) throws Exception {
        pipelineDispatcher.messageReceived(ioSession, message);
    }

    @Override
    public void sessionIdle(IoSession session, IdleStatus status) {
        logger.info("session is idle (" + status + ")");
        // disconnect an idle client ?
//        session.close(true);
    }

    @Override
    public void exceptionCaught(IoSession session, Throwable cause) {
        // close the connection on exceptional situation
        logger.error("server io error", cause);
        session.close(true);
    }


}
