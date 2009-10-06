package org.gradle.api.testing.execution.control.client.transport;

import org.apache.mina.core.service.IoConnector;
import org.apache.mina.core.service.IoHandler;

import java.io.IOException;
import java.net.SocketAddress;

/**
 * Base interface for 'network' communication connector factory (client-side).
 *
 * @author Tom Eyckmans
 */
public interface IoConnectorFactory<T extends IoConnector> {
    /**
     * Create and prepare the 'network' communication connector for use.
     *
     * @param handler The handler that needs to be called when messages are received on the 'network' communication connector. Can't be null.
     * @return The prepared 'network' communication connector.
     * @throws IOException When the 'network' communication connector could not be created/prepared.
     */
    T getIoConnector(IoHandler handler) throws IOException;

    /**
     * Retrieve the socket address used by the 'network' communication connector.
     *
     * @return The socket address used by the 'network' communication connector.
     */
    SocketAddress getSocketAddress();
}
