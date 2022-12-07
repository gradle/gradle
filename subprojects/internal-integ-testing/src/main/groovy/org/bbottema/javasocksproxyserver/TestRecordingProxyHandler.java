/*
 * Copyright 2022 the original author or authors.
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

package org.bbottema.javasocksproxyserver;

import java.net.InetAddress;
import java.net.Socket;
import java.util.List;

/**
 * Alternate implementation of {@link ProxyHandler} that records connections which would have been
 * made, but does not actually make them.
 *
 * Must live in same package as {@link Socks4Impl}.
 */
public final class TestRecordingProxyHandler extends ProxyHandler {
    private final List<InetAddress> connectionTargets;

    public TestRecordingProxyHandler(Socket clientSocket, List<InetAddress> connectionTargets) {
        super(clientSocket);
        this.connectionTargets = connectionTargets;
    }

    @Override
    public void processRelay() {
        Socks4Impl comm;

        try {
            byte theSOCKSversion = getByteFromClient();

            switch (theSOCKSversion) {
                case SocksConstants.SOCKS4_Version:
                    comm = new Socks4Impl(this);
                    break;
                case SocksConstants.SOCKS5_Version:
                    comm = new Socks5Impl(this);
                    break;
                default:
                    return;
            }

            comm.authenticate(theSOCKSversion);
            comm.getClientCommand();

            if (comm.socksCommand == SocksConstants.SC_CONNECT) {
                connectionTargets.add(comm.m_ServerIP);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
