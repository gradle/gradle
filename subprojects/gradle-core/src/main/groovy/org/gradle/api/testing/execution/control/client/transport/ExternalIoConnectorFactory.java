package org.gradle.api.testing.execution.control.client.transport;

import org.apache.commons.lang.StringUtils;
import org.apache.mina.transport.socket.nio.NioSocketConnector;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * Network communication connector factory for real network communication. Uses the Apache MINA NioSocketConnector.
 *
 * @author Tom Eyckmans
 */
public class ExternalIoConnectorFactory extends AbstractIoConnectorFactory<NioSocketConnector> {
    /**
     * When no host is specified when constructing an instance of this factory, the communication connector defaults
     * to creating a communication connector that connects to 'localhost'.
     */
    private static final String DEFAULT_HOSTNAME = "localhost";

    /**
     * The host network address of the machine to connect to.
     */
    private final InetAddress inetAddress;
    /**
     * The host name of the machine to connect to.
     */
    private final String hostname;

    /**
     * Creates a 'network' communication connector with the default host ('localhost') and the selected port.
     *
     * @param port The 'network' port the communication connector needs to connect to.
     */
    public ExternalIoConnectorFactory(int port) {
        this(DEFAULT_HOSTNAME, port);
    }

    /**
     * Creates a 'network' communication connector with the selected host address and port.
     *
     * @param inetAddress The 'network' host address the communication connector needs to connect to.
     * @param port        The 'network' port the communication connector needs to connect to.
     */
    public ExternalIoConnectorFactory(InetAddress inetAddress, int port) {
        super(port);

        if (inetAddress == null) throw new IllegalArgumentException("inetAddress is null!");

        this.inetAddress = inetAddress;
        this.hostname = null;
    }

    /**
     * Creates a 'network' communication connector with the selected port.
     *
     * @param hostname The 'network' hostname the communication connector needs to connect to.
     * @param port     The 'network' port the communication connector needs to connect to.
     */
    public ExternalIoConnectorFactory(String hostname, int port) {
        super(port);

        if (StringUtils.isEmpty(hostname)) throw new IllegalArgumentException("hostname is empty!");

        this.inetAddress = null;
        this.hostname = hostname;
    }

    /**
     * Creates a new instance of a NioSocketConnector.
     *
     * @return The newly instanciated NioSocketConnector instance.
     */
    protected NioSocketConnector instanciateIoConnector() {
        return new NioSocketConnector();
    }

    /**
     * Depending on the constructor used on this factory class an instance of an InetSocketAddress is created.
     *
     * @return The created InetSocketAddress.
     */
    public SocketAddress getSocketAddress() {
        InetSocketAddress inetSocketAddress = null;

        if (inetAddress != null) {
            inetSocketAddress = new InetSocketAddress(inetAddress, port);
        }

        if (hostname != null) {
            inetSocketAddress = new InetSocketAddress(hostname, port);
        }

        return inetSocketAddress;
    }

    /**
     * Retrieve the inetAddress attribute value (for test purposes).
     *
     * @return The inetAddress attribute value.
     */
    InetAddress getInetAddress() {
        return inetAddress;
    }

    /**
     * Retrieve the hostname attribute value (for test purposes).
     *
     * @return The hostname attribute value.
     */
    String getHostname() {
        return hostname;
    }
}
