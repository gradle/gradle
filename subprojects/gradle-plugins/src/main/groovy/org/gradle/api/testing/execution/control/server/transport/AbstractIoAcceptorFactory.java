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

import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.service.IoHandler;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.serialization.ObjectSerializationCodecFactory;

import java.io.IOException;

/**
 * Base factory class for Apache MINA 'network' communication acceptors.
 *
 * @author Tom Eyckmans
 */
public abstract class AbstractIoAcceptorFactory<T extends IoAcceptor> implements IoAcceptorFactory {
    /**
     * The 'network' port the communication connector needs to listen on.
     */
    protected final int port;

    /**
     * Creates a 'network' communication acceptor with the selected port.
     *
     * @param port The 'network' port the communication connector needs to listen on.
     */
    protected AbstractIoAcceptorFactory(int port) {
        if (port < 0) {
            throw new IllegalArgumentException("port is less then zero!");
        }

        this.port = port;
    }

    /**
     * Here the specific 'network' communication acceptor needs to be instanciated.
     *
     * @return The 'network' communication acceptor.
     */
    protected abstract T instanciateIoAcceptor();

    /**
     * Link the 'network' communication acceptor with the selected port.
     *
     * @param acceptor The 'network' communication acceptor to link.
     * @throws IOException When the 'network' communication acceptor could not be linked to the selected port.
     */
    protected abstract void bind(T acceptor) throws IOException;

    /**
     * Create and prepare the 'network' communication acceptor for use. Call {@link #instanciateIoAcceptor} to create
     * the 'network' communication acceptor, hardwire it with an ObjectSerializationCodecFactory, install the handler
     * and call {@link #bind} to link the acceptor to the selected port.
     *
     * @param handler The handler that needs to be called when messages are received on the 'network' communication
     * acceptor.
     * @return The prepared 'network' communication acceptor.
     * @throws IOException When the 'network' communication acceptor could not be created/prepared.
     */
    public IoAcceptor getIoAcceptor(IoHandler handler) throws IOException {
        if (handler == null) {
            throw new IllegalArgumentException("handler is null!");
        }

        final T acceptor = instanciateIoAcceptor();

        acceptor.getFilterChain().addLast("codec", new ProtocolCodecFilter(new ObjectSerializationCodecFactory()));

        acceptor.setHandler(handler);

        bind(acceptor);

        return acceptor;
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
