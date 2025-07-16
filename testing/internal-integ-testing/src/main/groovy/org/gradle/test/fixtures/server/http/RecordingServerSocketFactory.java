/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.test.fixtures.server.http;

import javax.net.ServerSocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RecordingServerSocketFactory extends ServerSocketFactory {

    private final List<InetAddress> connectionLog = Collections.synchronizedList(new ArrayList<>());

    public List<InetAddress> getConnectionLog() {
        return new ArrayList<>(connectionLog);
    }

    @Override
    public ServerSocket createServerSocket(int port) throws IOException {
        return new RecordingServerSocket(port);
    }

    @Override
    public ServerSocket createServerSocket(int port, int backlog) throws IOException {
        return new RecordingServerSocket(port, backlog);
    }

    @Override
    public ServerSocket createServerSocket(int port, int backlog, InetAddress bindAddr) throws IOException {
        return new RecordingServerSocket(port, backlog, bindAddr);
    }

    private class RecordingServerSocket extends ServerSocket {

        public RecordingServerSocket(int port) throws IOException {
            super(port);
        }

        public RecordingServerSocket(int port, int backlog) throws IOException {
            super(port, backlog);
        }

        public RecordingServerSocket(int port, int backlog, InetAddress bindAddr) throws IOException {
            super(port, backlog, bindAddr);
        }

        @Override
        public Socket accept() throws IOException {
            Socket socket = super.accept();
            InetAddress remoteAddress = socket.getInetAddress();
            connectionLog.add(remoteAddress);
            System.out.println("Accepted connection from: " + remoteAddress);
            return socket;
        }
    }
}
