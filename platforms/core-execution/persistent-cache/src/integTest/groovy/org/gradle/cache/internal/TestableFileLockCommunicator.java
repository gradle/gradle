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

package org.gradle.cache.internal;

import org.gradle.cache.internal.locklistener.FileLockCommunicator;
import org.gradle.cache.internal.locklistener.FileLockPacket;
import org.gradle.cache.internal.locklistener.FileLockPacketPayload;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.Optional;
import java.util.Set;

public class TestableFileLockCommunicator implements FileLockCommunicator {
    private final FileLockCommunicator delegate;

    private boolean allowCommunication = true;
    private int totalMessages = 0;

    public TestableFileLockCommunicator(FileLockCommunicator delegate) {
        this.delegate = delegate;
    }

    /**
     * Simulates a lock contention handler that does not respond to messages quickly enough
     *
     * @param allowCommunication true if we should respond to messages
     */
    public void setAllowCommunication(boolean allowCommunication) {
        this.allowCommunication = allowCommunication;
    }

    @Override
    public boolean pingOwner(InetAddress address, String pid, int ownerPort, long lockId, String displayName) {
        return delegate.pingOwner(address, pid, ownerPort, lockId, displayName);
    }

    @Override
    public Optional<FileLockPacket> receive() throws IOException {
        Optional<FileLockPacket> result = delegate.receive();
        if (result.isPresent()) {
            totalMessages++;
        }
        if (allowCommunication) {
            return result;
        }
        // Drop the message on the floor so we don't respond to it
        return Optional.empty();
    }

    @Override
    public FileLockPacketPayload decode(FileLockPacket receivedPacket) {
        return delegate.decode(receivedPacket);
    }

    @Override
    public void confirmUnlockRequest(SocketAddress requesterAddress, long lockId) {
        delegate.confirmUnlockRequest(requesterAddress, lockId);
    }

    @Override
    public void confirmLockRelease(Set<SocketAddress> requesterAddresses, long lockId) {
        delegate.confirmLockRelease(requesterAddresses, lockId);
    }

    @Override
    public void stop() {
        delegate.stop();
    }

    @Override
    public int getPort() {
        return delegate.getPort();
    }

    @Override
    public String createFileLockOwnerId(String pid, int port) {
        return delegate.createFileLockOwnerId(pid, port);
    }

    public int getTotalMessages() {
        return totalMessages;
    }
}
