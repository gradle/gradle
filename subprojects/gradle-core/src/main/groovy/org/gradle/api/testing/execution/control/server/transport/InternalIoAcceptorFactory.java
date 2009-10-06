package org.gradle.api.testing.execution.control.server.transport;

import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.transport.vmpipe.VmPipeAcceptor;
import org.apache.mina.transport.vmpipe.VmPipeAddress;

import java.io.IOException;

/**
 * In JVM 'network' communication acceptor. Uses the Apache MINA VmPipeAcceptor.
 *
 * @author Tom Eyckmans
 */
public class InternalIoAcceptorFactory extends AbstractIoAcceptorFactory<VmPipeAcceptor> {
    /**
     * Creates a 'network' communication acceptor with the selected port.
     *
     * @param port The 'network' port the communication acceptor needs to listen on.
     */
    public InternalIoAcceptorFactory(int port) {
        super(port);
    }

    /**
     * Creates a new instance of a VmPipeAcceptor.
     *
     * @return The newly instanciated VmPipeAcceptor instance.
     */
    protected VmPipeAcceptor instanciateIoAcceptor() {
        return new VmPipeAcceptor();
    }

    /**
     * Link the VmPipeAcceptor to the selected port.
     *
     * @param acceptor The 'network' communication acceptor to link.
     * @throws IOException When the 'network' communication acceptor could not be linked to the selected port.
     */
    protected void bind(VmPipeAcceptor acceptor) throws IOException {
        if (acceptor == null) throw new IllegalArgumentException("acceptor is null!");

        acceptor.bind(new VmPipeAddress(port));
    }

    /**
     * Retrieve the port the 'network' communication acceptor is listening on.
     *
     * @param ioAcceptor The 'network' communication acceptor to retrieve the listen port from.
     * @return The 'network' port the acceptor is listening on.
     */
    public int getLocalPort(IoAcceptor ioAcceptor) {
        if (ioAcceptor == null) throw new IllegalArgumentException("ioAcceptor is null!");

        return ((VmPipeAcceptor) ioAcceptor).getLocalAddress().getPort();
    }
}
