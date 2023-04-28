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

import org.gradle.internal.Pair;
import org.gradle.internal.remote.internal.inet.InetAddressFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Set;

import static org.gradle.cache.internal.locklistener.FileLockPacketType.LOCK_RELEASE_CONFIRMATION;
import static org.gradle.cache.internal.locklistener.FileLockPacketType.UNLOCK_REQUEST;
import static org.gradle.cache.internal.locklistener.FileLockPacketType.UNLOCK_REQUEST_CONFIRMATION;

abstract class AbstractFileLockCommunicator implements FileLockCommunicator {

    private static final String SOCKET_OPERATION_NOT_PERMITTED_ERROR_MESSAGE = "Operation not permitted";
    private static final String SOCKET_NETWORK_UNREACHABLE_ERROR_MESSAGE = "Network is unreachable";
    private static final String SOCKET_CANNOT_ASSIGN_ADDRESS_ERROR_MESSAGE = "Cannot assign requested address";

    private static final Logger LOGGER = LoggerFactory.getLogger(FileLockCommunicator.class);

    private final InetAddressFactory addressFactory;

    protected AbstractFileLockCommunicator(InetAddressFactory addressFactory) {
        this.addressFactory = addressFactory;
    }

    private volatile boolean stopped;

    protected InetAddress getBindingAddress() {
        return addressFactory.getWildcardBindingAddress();
    }

    @Override
    public FileLockPacketPayload receive() throws GracefullyStoppedException {
        try {
            Pair<byte[], InetSocketAddress> receivedBytes = receiveBytes();
            return FileLockPacketPayload.decode(receivedBytes.left, receivedBytes.right);
        } catch (IOException e) {
            if (!stopped) {
                throw new RuntimeException(e);
            }
            throw new GracefullyStoppedException();
        }
    }

    @Override
    public boolean pingOwner(int ownerPort, long lockId, String displayName) {
        boolean pingSentSuccessfully = false;
        try {
            byte[] bytesToSend = FileLockPacketPayload.encode(lockId, UNLOCK_REQUEST);
            for (InetAddress address : addressFactory.getCommunicationAddresses()) {
                try {
                    sendBytes(new InetSocketAddress(address, ownerPort), bytesToSend);
                    pingSentSuccessfully = true;
                } catch (IOException e) {
                    String message = e.getMessage();
                    if (message != null && (
                        message.startsWith(SOCKET_OPERATION_NOT_PERMITTED_ERROR_MESSAGE)
                            || message.startsWith(SOCKET_NETWORK_UNREACHABLE_ERROR_MESSAGE)
                            || message.startsWith(SOCKET_CANNOT_ASSIGN_ADDRESS_ERROR_MESSAGE)
                    )) {
                        LOGGER.debug("Failed attempt to ping owner of lock for {} (lock id: {}, port: {}, address: {})", displayName, lockId, ownerPort, address);
                    } else {
                        throw e;
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(String.format("Failed to ping owner of lock for %s (lock id: %s, port: %s)", displayName, lockId, ownerPort), e);
        }
        return pingSentSuccessfully;
    }

    @Override
    public void confirmUnlockRequest(SocketAddress address, long lockId) {
        try {
            byte[] bytes = FileLockPacketPayload.encode(lockId, UNLOCK_REQUEST_CONFIRMATION);
            sendBytes(address, bytes);
        } catch (IOException e) {
            if (!stopped) {
                throw new RuntimeException(e);
            }
            throw new GracefullyStoppedException();
        }
    }

    @Override
    public void confirmLockRelease(Set<SocketAddress> addresses, long lockId) {
        byte[] bytes = FileLockPacketPayload.encode(lockId, LOCK_RELEASE_CONFIRMATION);
        for (SocketAddress address : addresses) {
            LOGGER.debug("Confirming lock release to Gradle process at port {} for lock with id {}.", ((InetSocketAddress) address).getPort(), lockId);
            try {
                sendBytes(address, bytes);
            } catch (IOException e) {
                if (!stopped) {
                    LOGGER.debug("Failed to confirm lock release to Gradle process at port {} for lock with id {}.", ((InetSocketAddress) address).getPort(), lockId);
                }
            }
        }
    }

    protected abstract void sendBytes(SocketAddress address, byte[] bytes) throws IOException;

    protected abstract Pair<byte[], InetSocketAddress> receiveBytes() throws IOException;

    @Override
    public void stop() {
        stopped = true;
    }
}
