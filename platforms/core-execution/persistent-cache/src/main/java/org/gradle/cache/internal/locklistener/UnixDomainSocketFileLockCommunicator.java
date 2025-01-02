/*
 * Copyright 2024 the original author or authors.
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

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.NonNullApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.base.Preconditions.checkArgument;
import static org.gradle.cache.internal.locklistener.FileLockPacketType.LOCK_RELEASE_CONFIRMATION;
import static org.gradle.cache.internal.locklistener.FileLockPacketType.UNLOCK_REQUEST;
import static org.gradle.cache.internal.locklistener.FileLockPacketType.UNLOCK_REQUEST_CONFIRMATION;
import static org.gradle.cache.internal.locklistener.UnixDomainSocketUtil.isUnixDomainSocket;
import static org.gradle.cache.internal.locklistener.UnixDomainSocketUtil.unixDomainSocketPath;

/**
 * A {@link FileLockCommunicator} that uses Unix domain sockets to communicate with other Gradle processes.
 */
@NonNullApi
public class UnixDomainSocketFileLockCommunicator implements FileLockCommunicator {
    private static final Logger LOGGER = LoggerFactory.getLogger(UnixDomainSocketFileLockCommunicator.class);

    /**
     * In some cases we can have multiple instances of this class, let's generate an IN_PROCESS_ID to differentiate them for now.
     *
     * Using AtomicInteger instead of AtomicLong to keep the range in the port range that is an int.
     */
    private static final AtomicInteger IN_PROCESS_ID = new AtomicInteger(0);

    private final long pid;
    private final int inProcessId;
    private final SocketAddress thisProcessAddress;
    private final ServerSocketChannel serverChannel;
    private volatile boolean stopped;

    public UnixDomainSocketFileLockCommunicator() {
        this.pid = getCurrentPid();
        this.inProcessId = IN_PROCESS_ID.getAndIncrement();
        this.thisProcessAddress = unixDomainSocketAddressOf(Long.toString(pid), inProcessId);
        this.serverChannel = openAndBindServerSocketChannel(thisProcessAddress);
        unixDomainSocketPath(thisProcessAddress).toFile().deleteOnExit();
    }

    @Override
    public boolean pingOwner(String pid, int ownerPort, long lockId, String displayName) {
        SocketAddress otherProcessAddress = unixDomainSocketAddressOf(pid, ownerPort);
        try {
            byte[] bytesToSend = FileLockPacketPayload.encode(lockId, UNLOCK_REQUEST);
            return send(otherProcessAddress, bytesToSend);
        } catch (IOException e) {
            throw new RuntimeException(String.format("Failed to ping owner of lock for %s (my pid: %s, other pid: %s, lock id: %s)", displayName, this.pid, ownerPort, lockId), e);
        }
    }

    @Override
    public FileLockPacket receive() throws GracefullyStoppedException {
        try (SocketChannel channel = serverChannel.accept()) {
            byte[] bytes = readBytes(channel);
            // Packet is [pid + inProcessId + bytes]
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            String pid = Long.toString(buffer.getLong());
            int inProcessId = buffer.getInt();
            byte[] packet = Arrays.copyOfRange(bytes, Long.BYTES + Integer.BYTES, bytes.length);
            return FileLockPacket.of(packet, unixDomainSocketAddressOf(pid, inProcessId), createFileLockOwnerId(pid, inProcessId));
        } catch (IOException e) {
            if (!stopped) {
                throw new RuntimeException(e);
            }
            throw new GracefullyStoppedException();
        }
    }

    private static byte[] readBytes(SocketChannel channel) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES + Integer.BYTES + FileLockPacketPayload.MAX_BYTES);
        int bytesRead = channel.read(buffer);
        byte[] bytes = new byte[bytesRead];
        buffer.flip();
        buffer.get(bytes);
        return bytes;
    }

    @Override
    public FileLockPacketPayload decode(FileLockPacket receivedPacket) {
        try {
            return FileLockPacketPayload.decode(receivedPacket.getData(), receivedPacket.getLength());
        } catch (IOException e) {
            if (!stopped) {
                throw new RuntimeException(e);
            }
            throw new GracefullyStoppedException();
        }
    }

    @Override
    public void confirmUnlockRequest(SocketAddress address, long lockId) {
        try {
            byte[] bytes = FileLockPacketPayload.encode(lockId, UNLOCK_REQUEST_CONFIRMATION);
            send(address, bytes);
        } catch (IOException e) {
            if (!stopped) {
                throw new RuntimeException(e);
            }
            throw new GracefullyStoppedException();
        }
    }

    @Override
    public void confirmLockRelease(Set<SocketAddress> addresses, long lockId) {
        byte[] packet = FileLockPacketPayload.encode(lockId, LOCK_RELEASE_CONFIRMATION);
        for (SocketAddress address : addresses) {
            try {
                LOGGER.debug("Confirming lock release to Gradle process at address {} for lock with id {}.", address, lockId);
                send(address, packet);
            } catch (IOException e) {
                if (!stopped) {
                    LOGGER.debug("Failed to confirm lock release to Gradle process at address {} for lock with id {}.", address, lockId);
                }
            }
        }
    }

    @Override
    public void stop() {
        try {
            stopped = true;
            serverChannel.close();
            Files.deleteIfExists(unixDomainSocketPath(thisProcessAddress));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getPort() {
        // This is a Unix domain socket, so there is no port, we use inProcessId instead
        return stopped ? -1 : inProcessId;
    }

    @Override
    public String createFileLockOwnerId(String pid, int port) {
        return pid + "-" + port;
    }

    private static ServerSocketChannel openAndBindServerSocketChannel(SocketAddress thisProcessAddress) {
        try {
            ServerSocketChannel serverSocketChannel = UnixDomainSocketUtil.openUnixServerSocketChannel();
            return serverSocketChannel.bind(thisProcessAddress);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @VisibleForTesting
    SocketAddress unixDomainSocketAddressOf(String pid, int inProcessId) {
        // TODO: Where to put domain sockets? If we put them in gradleUserHomeDir, we can get
        //  java.net.SocketException: Unix domain path too long
        File unixDomainSocketsDir = new File("/tmp/gradle");
        if (!unixDomainSocketsDir.exists()) {
            unixDomainSocketsDir.mkdir();
        }
        String id = createFileLockOwnerId(pid, inProcessId);
        Path address = new File(unixDomainSocketsDir, "gradle-" + id + ".sock").toPath();
        return UnixDomainSocketUtil.unixDomainSocketAddressOf(address);
    }

    private boolean send(SocketAddress address, byte[] bytesToSend) throws IOException {
        checkArgument(isUnixDomainSocket(address), "Only UnixDomainSocketAddress is supported, but got: %s", address.getClass().getName());
        try (SocketChannel clientChannel = SocketChannel.open(address)) {
            // Packet is [pid + inProcessId + bytesToSend]
            ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES + Integer.BYTES + bytesToSend.length)
                .putLong(pid)
                .putInt(inProcessId)
                .put(bytesToSend);
            buffer.position(0);
            return clientChannel.write(buffer) > 0;
        } catch (SocketException e) {
            if (e.getMessage().contains("No such file or directory")) {
                // Socket file doesn't exist
                return false;
            }
            throw e;
        }
    }

    /**
     * This class is only used with Java17 so ProcessHandle is available, we could also get pid from environment
     */
    static long getCurrentPid() {
        try {
            // Call ProcessHandle.current().pid() to get the pid
            Object processHandle = Class.forName("java.lang.ProcessHandle").getMethod("current").invoke(null);
            return (long) Class.forName("java.lang.ProcessHandle").getMethod("pid").invoke(processHandle);
        } catch (ClassNotFoundException | InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
