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
package org.gradle.api.testing.execution.control.server.transport;

import org.apache.commons.lang.StringUtils;
import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * Network communication acceptor factory for real network communication. Uses the Apache MINA NioSocketAcceptor.
 *
 * @author Tom Eyckmans
 */
public class ExternalIoAcceptorFactory extends AbstractIoAcceptorFactory<NioSocketAcceptor> {
    /**
     * When no host is specified when constructing an instance of this factory, the communication acceptor defaults
     * to creating a communication acceptor that listens on 'localhost'.
     */
    private static final String DEFAULT_HOSTNAME = "localhost";

    /**
     * When no port is specified when constructing an instance of this factory, the communication acceptor defaults
     * to creating a communication acceptor that listens on an unused port that is assigned by the operating system.
     * <p/>
     * To make the operating system assign an unused port the port is set to zero.
     */
    private static final int DEFAULT_PORT = 0;

    /**
     * The host network address of the machine to listen on.
     */
    private final InetAddress inetAddress;
    /**
     * The host name of the machine to listen on.
     */
    private final String hostname;

    /**
     * Creates a network communication acceptor factory that listens on an automatically
     * assigned network port by the operating system on the localhost address.
     */
    public ExternalIoAcceptorFactory() {
        this(DEFAULT_HOSTNAME, DEFAULT_PORT);
    }

    /**
     * Creates a network communication acceptor factory that listens on the selected port on the localhost address.
     *
     * @param port The network port to listen on.
     */
    public ExternalIoAcceptorFactory(int port) {
        this(DEFAULT_HOSTNAME, port);
    }

    /**
     * Creates a network communication acceptor factory that listenes on the selected network address and port.
     *
     * @param inetAddress The network address to listen on.
     * @param port        The network port to listen on.
     */
    public ExternalIoAcceptorFactory(InetAddress inetAddress, int port) {
        super(port);

        if (inetAddress == null) throw new IllegalArgumentException("inetAddress is null!");

        this.inetAddress = inetAddress;
        this.hostname = null;
    }

    /**
     * Creates a network communication acceptor factory that listenes on the selected hostname and port.
     *
     * @param hostname The hostname to listen on.
     * @param port     The network port to listen on.
     */
    public ExternalIoAcceptorFactory(String hostname, int port) {
        super(port);

        if (StringUtils.isEmpty(hostname)) throw new IllegalArgumentException("hostname is null!");

        this.hostname = hostname;
        this.inetAddress = null;
    }

    /**
     * Creates an instance of NioSocketAcceptor.
     *
     * @return The created instance of NioSocketAcceptor.
     */
    protected NioSocketAcceptor instanciateIoAcceptor() {
        final NioSocketAcceptor acceptor = new NioSocketAcceptor();

        acceptor.setReuseAddress(true);

        return acceptor;
    }

    /**
     * Link the NioSocketAcceptor to the selected network address and port.
     *
     * @param acceptor The 'network' communication acceptor to link.
     * @throws IOException When the 'network' communication acceptor could not be linked to the selected port.
     */
    protected void bind(NioSocketAcceptor acceptor) throws IOException {
        if (acceptor == null) throw new IllegalArgumentException("acceptor is null!");

        acceptor.bind(getInetSocketAddress());
    }

    /**
     * Retrieve the port the network communication acceptor is listening on.
     *
     * @param ioAcceptor The network communication acceptor to retrieve the listen port from.
     * @return The network port the acceptor is listening on.
     */
    public int getLocalPort(IoAcceptor ioAcceptor) {
        if (ioAcceptor == null) throw new IllegalArgumentException("ioAcceptor is null!");

        return ((NioSocketAcceptor) ioAcceptor).getLocalAddress().getPort();
    }

    /**
     * Create an instance of {@link InetSocketAddress} depending on the used constructor.
     *
     * @return The created {@link InetSocketAddress} instance.
     */
    InetSocketAddress getInetSocketAddress() {
        InetSocketAddress inetSocketAddress = null;

        if (inetAddress != null) inetSocketAddress = new InetSocketAddress(inetAddress, port);

        if (hostname != null) inetSocketAddress = new InetSocketAddress(hostname, port);

        if (inetSocketAddress == null) throw new IllegalStateException("inetSocketAddress not created, " +
                "both inetAddress and hostname were null!");

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
