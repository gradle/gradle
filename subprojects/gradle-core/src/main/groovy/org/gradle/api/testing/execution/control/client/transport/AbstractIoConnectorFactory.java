package org.gradle.api.testing.execution.control.client.transport;

import org.apache.mina.core.service.IoConnector;
import org.apache.mina.core.service.IoHandler;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.serialization.ObjectSerializationCodecFactory;

import java.io.IOException;

/**
 * Base factory class for Apache MINA 'network' communication connectors.
 *
 * @author Tom Eyckmans
 */
public abstract class AbstractIoConnectorFactory<T extends IoConnector> implements IoConnectorFactory<T> {
    /**
     * The 'network' port the communication connector needs to connect to.
     */
    protected final int port;

    /**
     * Creates a 'network' communication connector with the selected port.
     *
     * @param port The 'network' port the communication connector needs to connect to.
     */
    protected AbstractIoConnectorFactory(int port) {
        if (port <= 0)
            throw new IllegalArgumentException("The 'network' port the communication connector needs to connect to can't be equal to or lower to zero!");
        this.port = port;
    }

    /**
     * Here the specific 'network' communication connector needs to be instanciated.
     *
     * @return The 'network' communication connector.
     */
    protected abstract T instanciateIoConnector();

    /**
     * Create and prepare the 'network' communication connector for use. Call {@link #instanciateIoConnector} to create the
     * 'network' communication connector, hardwire it with an ObjectSerializationCodecFactory and install the handler.
     *
     * @param handler The handler that needs to be called when messages are received on the 'network' communication connector.
     * @return The prepared 'network' communication connector.
     * @throws IOException When the 'network' communication connector could not be created/prepared.
     */
    public final T getIoConnector(IoHandler handler) throws IOException {
        if (handler == null)
            throw new IllegalArgumentException("The handler that needs to be called when messages are received on the 'network' communication connector can't be null!");

        final T connector = instanciateIoConnector();

        connector.getFilterChain().addLast(
                "codec",
                new ProtocolCodecFilter(
                        new ObjectSerializationCodecFactory()));

        connector.setHandler(handler);

        return connector;
    }

    /**
     * Retrieve the port attribute (for test purposes).
     *
     * @return The current port.
     */
    int getPort() {
        return port;
    }
}
