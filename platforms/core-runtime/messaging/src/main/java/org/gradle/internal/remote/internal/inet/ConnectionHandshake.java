/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.remote.internal.inet;

import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Mutual authentication of the two ends of a freshly opened socket, done before any message is exchanged.
 * <p>
 * The connection token holds two independent random secrets, one per side. The connecting side sends its secret first.
 * Only once it matches does the accepting side send its own. A process that took over the port of a dead peer only learns
 * the client secret of that dead peer, and cannot answer the client.
 */
public final class ConnectionHandshake {
    public static final int PROOF_LENGTH = 16;
    public static final int TOKEN_LENGTH = 2 * PROOF_LENGTH;

    private ConnectionHandshake() {
    }

    public static byte[] newConnectionToken() {
        byte[] token = new byte[TOKEN_LENGTH];
        new SecureRandom().nextBytes(token);
        return token;
    }

    public static byte[] clientProof(byte[] connectionToken) {
        return Arrays.copyOfRange(checkLength(connectionToken), 0, PROOF_LENGTH);
    }

    static byte[] serverProof(byte[] connectionToken) {
        return Arrays.copyOfRange(checkLength(connectionToken), PROOF_LENGTH, TOKEN_LENGTH);
    }

    static boolean matches(byte[] expected, byte[] actual) {
        return MessageDigest.isEqual(expected, actual);
    }

    private static byte[] checkLength(byte[] connectionToken) {
        if (connectionToken.length != TOKEN_LENGTH) {
            throw new IllegalArgumentException("Connection token must be " + TOKEN_LENGTH + " bytes long, but was " + connectionToken.length);
        }
        return connectionToken;
    }

    static byte[] read(SocketChannel socket, int length, long timeoutMillis) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(length);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        try (Selector selector = Selector.open()) {
            socket.register(selector, SelectionKey.OP_READ);
            while (buffer.hasRemaining()) {
                long remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                if (remainingMillis <= 0) {
                    throw new SocketTimeoutException("Peer did not complete the connection handshake in time");
                }
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedIOException("Interrupted while waiting for the connection handshake");
                }
                selector.select(remainingMillis);
                selector.selectedKeys().clear();
                if (socket.read(buffer) == -1) {
                    throw new EOFException("Peer closed the connection during the connection handshake");
                }
            }
        }
        return buffer.array();
    }

    static void write(SocketChannel socket, byte[] bytes) throws IOException {
        if (socket.write(ByteBuffer.wrap(bytes)) != bytes.length) {
            throw new IOException("Could not send the connection handshake");
        }
    }
}
