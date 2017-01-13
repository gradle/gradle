/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.base.Objects;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.remote.internal.RecoverableMessageIOException;
import org.gradle.internal.serialize.FlushableEncoder;
import org.gradle.internal.serialize.ObjectReader;
import org.gradle.internal.serialize.ObjectWriter;
import org.gradle.internal.serialize.StatefulSerializer;
import org.gradle.internal.remote.internal.MessageIOException;
import org.gradle.internal.remote.internal.MessageSerializer;
import org.gradle.internal.remote.internal.RemoteConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class SocketConnection<T> implements RemoteConnection<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SocketConnection.class);
    private final SocketChannel socket;
    private final SocketInetAddress localAddress;
    private final SocketInetAddress remoteAddress;
    private final ObjectWriter<T> objectWriter;
    private final ObjectReader<T> objectReader;
    private final InputStream instr;
    private final OutputStream outstr;
    private final FlushableEncoder encoder;

    public SocketConnection(SocketChannel socket, MessageSerializer streamSerializer, StatefulSerializer<T> messageSerializer) {
        this.socket = socket;
        try {
            // NOTE: we use non-blocking IO as there is no reliable way when using blocking IO to shutdown reads while
            // keeping writes active. For example, Socket.shutdownInput() does not work on Windows.
            socket.configureBlocking(false);
            outstr = new SocketOutputStream(socket);
            instr = new SocketInputStream(socket);
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        InetSocketAddress localSocketAddress = (InetSocketAddress) socket.socket().getLocalSocketAddress();
        localAddress = new SocketInetAddress(localSocketAddress.getAddress(), localSocketAddress.getPort());
        InetSocketAddress remoteSocketAddress = (InetSocketAddress) socket.socket().getRemoteSocketAddress();
        remoteAddress = new SocketInetAddress(remoteSocketAddress.getAddress(), remoteSocketAddress.getPort());
        objectReader = messageSerializer.newReader(streamSerializer.newDecoder(instr));
        encoder = streamSerializer.newEncoder(outstr);
        objectWriter = messageSerializer.newWriter(encoder);
    }

    @Override
    public String toString() {
        return "socket connection from " + localAddress + " to " + remoteAddress;
    }

    public T receive() throws MessageIOException {
        try {
            return objectReader.read();
        } catch (EOFException e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Discarding EOFException: {}", e.toString());
            }
            return null;
        } catch (ObjectStreamException e) {
            throw new RecoverableMessageIOException(String.format("Could not read message from '%s'.", remoteAddress), e);
        } catch (ClassNotFoundException e) {
            throw new RecoverableMessageIOException(String.format("Could not read message from '%s'.", remoteAddress), e);
        } catch (Exception e) {
            throw new MessageIOException(String.format("Could not read message from '%s'.", remoteAddress), e);
        }
    }

    private static boolean isEndOfStream(Exception e) {
        if (e instanceof EOFException) {
            return true;
        }
        if (e instanceof IOException) {
            if (Objects.equal(e.getMessage(), "An existing connection was forcibly closed by the remote host")) {
                return true;
            }
            if (Objects.equal(e.getMessage(), "An established connection was aborted by the software in your host machine")) {
                return true;
            }
            if (Objects.equal(e.getMessage(), "Connection reset by peer")) {
                return true;
            }
        }
        return false;
    }

    public void dispatch(T message) throws MessageIOException {
        try {
            objectWriter.write(message);
        } catch (ObjectStreamException e) {
            throw new RecoverableMessageIOException(String.format("Could not write message %s to '%s'.", message, remoteAddress), e);
        } catch (ClassNotFoundException e) {
            throw new RecoverableMessageIOException(String.format("Could not read message from '%s'.", remoteAddress), e);
        } catch (Exception e) {
            throw new MessageIOException(String.format("Could not write message %s to '%s'.", message, remoteAddress), e);
        }
    }

    @Override
    public void flush() throws MessageIOException {
        try {
            encoder.flush();
            outstr.flush();
        } catch (Exception e) {
            throw new MessageIOException(String.format("Could not write '%s'.", remoteAddress), e);
        }
    }

    public void stop() {
        CompositeStoppable.stoppable(new Closeable() {
            @Override
            public void close() throws IOException {
                flush();
            }
        }, instr, outstr, socket).stop();
    }

    private static class SocketInputStream extends InputStream {
        private final Selector selector;
        private final ByteBuffer buffer;
        private final SocketChannel socket;
        private final byte[] readBuffer = new byte[1];

        public SocketInputStream(SocketChannel socket) throws IOException {
            this.socket = socket;
            selector = Selector.open();
            socket.register(selector, SelectionKey.OP_READ);
            buffer = ByteBuffer.allocateDirect(4096);
            buffer.limit(0);
        }

        @Override
        public int read() throws IOException {
            int nread = read(readBuffer, 0, 1);
            if (nread <= 0) {
                return nread;
            }
            return readBuffer[0];
        }

        @Override
        public int read(byte[] dest, int offset, int max) throws IOException {
            if (max == 0) {
                return 0;
            }

            if (buffer.remaining() == 0) {
                try {
                    selector.select();
                } catch (ClosedSelectorException e) {
                    return -1;
                }
                if (!selector.isOpen()) {
                    return -1;
                }

                buffer.clear();
                int nread;
                try {
                    nread = socket.read(buffer);
                } catch (IOException e) {
                    if (isEndOfStream(e)) {
                        buffer.position(0);
                        buffer.limit(0);
                        return -1;
                    }
                    throw e;
                }
                buffer.flip();

                if (nread < 0) {
                    return -1;
                }
            }

            int count = Math.min(buffer.remaining(), max);
            buffer.get(dest, offset, count);
            return count;
        }

        @Override
        public void close() throws IOException {
            selector.close();
        }
    }

    private static class SocketOutputStream extends OutputStream {
        private static final int RETRIES_WHEN_BUFFER_FULL = 2;
        private Selector selector;
        private final SocketChannel socket;
        private final ByteBuffer buffer;
        private final byte[] writeBuffer = new byte[1];

        public SocketOutputStream(SocketChannel socket) throws IOException {
            this.socket = socket;
            buffer = ByteBuffer.allocateDirect(32 * 1024);
        }

        @Override
        public void write(int b) throws IOException {
            writeBuffer[0] = (byte) b;
            write(writeBuffer);
        }

        @Override
        public void write(byte[] src, int offset, int max) throws IOException {
            int remaining = max;
            int currentPos = offset;
            while (remaining > 0) {
                int count = Math.min(remaining, buffer.remaining());
                if (count > 0) {
                    buffer.put(src, currentPos, count);
                    remaining -= count;
                    currentPos += count;
                }
                while (buffer.remaining() == 0) {
                    writeBufferToChannel();
                }
            }
        }

        @Override
        public void flush() throws IOException {
            while (buffer.position() > 0) {
                writeBufferToChannel();
            }
        }

        private void writeBufferToChannel() throws IOException {
            buffer.flip();
            int count = writeWithNonBlockingRetry();
            if (count == 0) {
                // buffer was still full after non-blocking retries, now block
                waitForWriteBufferToDrain();
            }
            buffer.compact();
        }

        private int writeWithNonBlockingRetry() throws IOException {
            int count = 0;
            int retryCount = 0;
            while (count == 0 && retryCount++ < RETRIES_WHEN_BUFFER_FULL) {
                count = socket.write(buffer);
                if (count < 0) {
                    throw new EOFException();
                } else if (count == 0) {
                    // buffer was full, just call Thread.yield
                    Thread.yield();
                }
            }
            return count;
        }

        private void waitForWriteBufferToDrain() throws IOException {
            if (selector == null) {
                selector = Selector.open();
            }
            SelectionKey key = socket.register(selector, SelectionKey.OP_WRITE);
            // block until ready for write operations
            selector.select();
            // cancel OP_WRITE selection
            key.cancel();
            // complete cancelling key
            selector.selectNow();
        }

        @Override
        public void close() throws IOException {
            if (selector != null) {
                selector.close();
                selector = null;
            }
        }
    }
}
