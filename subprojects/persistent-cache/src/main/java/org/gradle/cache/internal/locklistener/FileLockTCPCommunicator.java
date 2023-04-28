/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.cache.internal.locklistener;

import org.apache.commons.io.IOUtils;
import org.gradle.internal.Pair;
import org.gradle.internal.remote.internal.inet.InetAddressFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;

import static org.gradle.internal.UncheckedException.throwAsUncheckedException;

public class FileLockTCPCommunicator extends AbstractFileLockCommunicator {

    public static final int INCOMING_CONNECTION_BACKLOG = 1; //todo: does it make sense to set more?
    private static final int SOCKET_CONNECT_TIMEOUT_MS = 250; //todo: what optimal value to use? probably need to make it configurable
    private final ServerSocket serverSocket;

    public FileLockTCPCommunicator(InetAddressFactory addressFactory) {
        super(addressFactory);
        try {
            serverSocket = new ServerSocket(0, INCOMING_CONNECTION_BACKLOG, getBindingAddress());
        } catch (IOException e) {
            throw throwAsUncheckedException(e);
        }
    }

    @Override
    protected void sendBytes(SocketAddress address, byte[] bytes) throws IOException {
        try {
            Socket socket = new Socket();
            socket.connect(address, SOCKET_CONNECT_TIMEOUT_MS);

            OutputStream stream = socket.getOutputStream();
            stream.write(bytes);
            stream.flush();

            socket.close();
        } catch (ConnectException ce) {
            //ignore, it's ok to not be able to connect
        }
    }

    @Override
    protected Pair<byte[], InetSocketAddress> receiveBytes() throws IOException {
        Socket socket = serverSocket.accept();
        InetSocketAddress remoteSocketAddress = (InetSocketAddress) socket.getRemoteSocketAddress();
        byte[] data = IOUtils.toByteArray(socket.getInputStream());
        socket.close();
        return Pair.of(data, remoteSocketAddress);
    }

    @Override
    public void stop() {
        super.stop();
        try {
            serverSocket.close();
        } catch (IOException e) {
            throw new GracefullyStoppedException();
        }
    }

    @Override
    public int getPort() {
        if (serverSocket.isClosed()) {
            return -1;
        }
        return serverSocket.getLocalPort();
    }
}
