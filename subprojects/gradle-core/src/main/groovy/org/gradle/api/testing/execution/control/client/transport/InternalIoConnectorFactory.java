package org.gradle.api.testing.execution.control.client.transport;

import org.apache.mina.transport.vmpipe.VmPipeAddress;
import org.apache.mina.transport.vmpipe.VmPipeConnector;

import java.net.SocketAddress;

/**
 * In JVM 'network' communication connector. Uses the Apache MINA VmPipeConnector.
 *
 * @author Tom Eyckmans
 */
public class InternalIoConnectorFactory extends AbstractIoConnectorFactory<VmPipeConnector> {
    /**
     * Creates a 'network' communication connector with the selected port.
     *
     * @param port The 'network' port the communication connector needs to connect to.
     */
    public InternalIoConnectorFactory(int port) {
        super(port);
    }

    /**
     * Creates a new instance of a VmPipeConnector.
     *
     * @return The newly instanciated VmPipeConnector instance.
     */
    protected VmPipeConnector instanciateIoConnector() {
        return new VmPipeConnector();
    }

    /**
     * Create an instance of a VmPipeAddress.
     *
     * @return The created VmPipeAddress.
     */
    public SocketAddress getSocketAddress() {
        return new VmPipeAddress(port);
    }
}
