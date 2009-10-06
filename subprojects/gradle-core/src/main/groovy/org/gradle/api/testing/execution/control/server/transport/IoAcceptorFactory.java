package org.gradle.api.testing.execution.control.server.transport;

import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.service.IoHandler;

import java.io.IOException;

/**
 * Base interface for 'network' communication acceptor factory (server-side).
 *
 * @author Tom Eyckmans
 */
public interface IoAcceptorFactory {
    /**
     * Create an prepare the 'network' communication acceptor for use.
     *
     * @param handler The handler that needs to be called when messages are received on the 'network' communication acceptor.
     * @return The prepared 'network' communication acceptor.
     * @throws IOException When the 'network' communication acceptor could not be created/prepared.
     */
    IoAcceptor getIoAcceptor(IoHandler handler) throws IOException;

    /**
     * Retrieve the port the 'network' communication acceptor is listening on. This method is used to determine the
     * port the 'network' communication acceptor is listening on in case a network port is assigned by the operating
     * system (when initially using port zero).
     *
     * @param ioAcceptor The 'network' communication acceptor to retrieve the listen port from.
     * @return The listen port of the 'network' communication acceptor.
     */
    int getLocalPort(IoAcceptor ioAcceptor);
}
