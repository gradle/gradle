package org.gradle.api.testing.execution.control.server;

import org.apache.mina.core.service.IoAcceptor;
import org.gradle.api.GradleException;
import org.gradle.api.testing.execution.PipelineDispatcher;
import org.gradle.api.testing.execution.control.server.transport.IoAcceptorFactory;
import org.gradle.api.testing.execution.control.server.transport.TestServerIoHandler;

import java.io.IOException;

/**
 * @author Tom Eyckmans
 */
public class DefaultTestControlServer implements TestControlServer {
    private final IoAcceptorFactory ioAcceptorFactory;
    private final TestServerIoHandler testServerIoHandler;

    private IoAcceptor ioAcceptor;
    private int port;

    public DefaultTestControlServer(IoAcceptorFactory ioAcceptorFactory, PipelineDispatcher pipelineDispatcher) {
        if (ioAcceptorFactory == null) throw new IllegalArgumentException("socketAcceptorProvider is null!");
        if (pipelineDispatcher == null) throw new IllegalArgumentException("pipeline is null!");

        this.ioAcceptorFactory = ioAcceptorFactory;
        this.testServerIoHandler = new TestServerIoHandler(pipelineDispatcher);
    }

    public int start() {
        try {
            ioAcceptor = ioAcceptorFactory.getIoAcceptor(testServerIoHandler);
            port = ioAcceptorFactory.getLocalPort(ioAcceptor);
        }
        catch (IOException e) {
            throw new GradleException("failed to start test control server", e);
        }
        return port;
    }

    public void stop() {
        ioAcceptor.unbind();
    }

    public int getPort() {
        return port;
    }


}
