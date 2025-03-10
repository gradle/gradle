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

package org.gradle.cache.internal.locklistener;

import org.gradle.api.NonNullApi;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.Optional;
import java.util.Set;

@NonNullApi
public interface FileLockCommunicator {
    boolean pingOwner(InetAddress address, String pid, int ownerPort, long lockId, String displayName);

    Optional<FileLockPacket> receive() throws IOException;

    FileLockPacketPayload decode(FileLockPacket receivedPacket);

    void confirmUnlockRequest(SocketAddress requesterAddress, long lockId);

    void confirmLockRelease(Set<SocketAddress> requesterAddresses, long lockId);

    void stop();

    int getPort();

    String createFileLockOwnerId(String pid, int port);
}
