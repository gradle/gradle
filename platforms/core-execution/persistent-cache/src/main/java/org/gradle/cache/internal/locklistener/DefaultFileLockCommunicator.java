/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.NonNullApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.Optional;
import java.util.Set;

import static org.gradle.cache.internal.locklistener.FileLockPacketType.LOCK_RELEASE_CONFIRMATION;
import static org.gradle.cache.internal.locklistener.FileLockPacketType.UNLOCK_REQUEST;
import static org.gradle.cache.internal.locklistener.FileLockPacketType.UNLOCK_REQUEST_CONFIRMATION;
import static org.gradle.internal.UncheckedException.throwAsUncheckedException;

@NonNullApi
public class DefaultFileLockCommunicator implements FileLockCommunicator {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultFileLockCommunicator.class);

    private final DatagramSocket socket;

    public DefaultFileLockCommunicator(InetAddressProvider inetAddressProvider) {
        try {
            socket = new DatagramSocket(0, inetAddressProvider.getWildcardBindingAddress());
        } catch (SocketException e) {
            throw throwAsUncheckedException(e);
        }
    }

    @Override
    public boolean pingOwner(InetAddress address, int ownerPort, long lockId, String displayName) {
        boolean pingSentSuccessfully = false;
        byte[] bytesToSend = FileLockPacketPayload.encode(lockId, UNLOCK_REQUEST);
        try {
            socket.send(new DatagramPacket(bytesToSend, bytesToSend.length, address, ownerPort));
            pingSentSuccessfully = true;
        } catch (IOException e) {
            LOGGER.debug("Failed attempt to ping owner of lock for {} (lock id: {}, port: {}, address: {})", displayName, lockId, ownerPort, address);
        }
        return pingSentSuccessfully;
    }

    @Override
    public Optional<DatagramPacket> receive() throws IOException {
        try {
            byte[] bytes = new byte[FileLockPacketPayload.MAX_BYTES];
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length);
            socket.receive(packet);
            return Optional.of(packet);
        } catch (IOException e) {
            // Socket was shutdown while waiting to receive message
            if (socket.isClosed()) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public FileLockPacketPayload decode(DatagramPacket receivedPacket) {
        return FileLockPacketPayload.decode(receivedPacket.getData(), receivedPacket.getLength());
    }

    @Override
    public void confirmUnlockRequest(SocketAddress requesterAddress, long lockId) {
        byte[] bytes = FileLockPacketPayload.encode(lockId, UNLOCK_REQUEST_CONFIRMATION);
        DatagramPacket packet = new DatagramPacket(bytes, bytes.length);
        packet.setSocketAddress(requesterAddress);
        LOGGER.debug("Confirming unlock request to Gradle process at port {} for lock with id {}.", packet.getPort(), lockId);
        try {
            socket.send(packet);
        } catch (IOException e) {
            LOGGER.debug("Failed to confirm unlock request to Gradle process at port {} for lock with id {}.", packet.getPort(), lockId);
        }
    }

    @Override
    public void confirmLockRelease(Set<SocketAddress> requesterAddresses, long lockId) {
        byte[] bytes = FileLockPacketPayload.encode(lockId, LOCK_RELEASE_CONFIRMATION);
        for (SocketAddress requesterAddress : requesterAddresses) {
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length);
            packet.setSocketAddress(requesterAddress);
            LOGGER.debug("Confirming lock release to Gradle process at port {} for lock with id {}.", packet.getPort(), lockId);
            try {
                socket.send(packet);
            } catch (IOException e) {
                LOGGER.debug("Failed to confirm lock release to Gradle process at port {} for lock with id {}.", packet.getPort(), lockId);
            }
        }
    }

    @Override
    public void stop() {
        socket.close();
    }

    @Override
    public int getPort() {
        return socket.getLocalPort();
    }
}
