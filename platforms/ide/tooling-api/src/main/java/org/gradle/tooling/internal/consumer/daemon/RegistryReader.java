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
package org.gradle.tooling.internal.consumer.daemon;

import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.InputStreamBackedDecoder;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a Gradle daemon {@code registry.bin} file. Handles all four historical layouts
 * (5.0–8.2, 8.3–8.7, 8.8–8.9, 8.10+) by dispatching to a version-specific
 * {@link DaemonContextReader} for the nested DaemonContext bytes. Errors are swallowed
 * — corrupted, partial-write, or unknown-format registries return an empty list.
 */
final class RegistryReader {

    private static final byte ADDRESS_TYPE_SOCKET_INET = 0;
    private static final byte ADDRESS_TYPE_MULTI_CHOICE = 1;

    /**
     * Cap on the daemon count read from a registry envelope. Defends against
     * garbage bytes (e.g. a partially-overwritten file) where {@code readInt()}
     * returns a huge value and a naive {@code new ArrayList<>(count)} would OOM.
     * Real registries rarely hold more than a handful of entries.
     */
    private static final int MAX_PLAUSIBLE_DAEMON_COUNT = 4096;

    List<DaemonInfoView> read(File registryFile, String gradleVersion) {
        if (!registryFile.isFile()) {
            return new ArrayList<>();
        }
        try (InputStreamBackedDecoder decoder = new InputStreamBackedDecoder(
                new BufferedInputStream(new FileInputStream(registryFile)))) {
            return readContent(decoder, gradleVersion);
        } catch (Throwable t) {
            // Best-effort: corrupted/partial-write registries, garbage bytes producing
            // implausible array allocations (OOM), or any other unexpected failure
            // all surface as empty rather than propagating to the IDE.
            return new ArrayList<>();
        }
    }

    private List<DaemonInfoView> readContent(Decoder decoder, String gradleVersion) throws Exception {
        List<DaemonInfoView> result = new ArrayList<>();
        if (!decoder.readBoolean()) {
            return result;
        }
        int count = decoder.readInt();
        if (count < 0 || count > MAX_PLAUSIBLE_DAEMON_COUNT) {
            return result;
        }
        List<AddressView> addresses = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            addresses.add(readAddress(decoder));
        }
        DaemonContextReader contextReader = DaemonContextReaders.forVersion(gradleVersion);
        for (int i = 0; i < count; i++) {
            int addressIndex = decoder.readInt();
            byte[] token = decoder.readBinary();
            DaemonStateView state = DaemonStateView.fromOrdinal(decoder.readByte());
            long lastBusy = decoder.readLong();
            DaemonContextView context = contextReader.read(decoder);
            if (addressIndex >= 0 && addressIndex < addresses.size()) {
                result.add(new DaemonInfoView(addresses.get(addressIndex), token, state, lastBusy, context));
            }
        }
        // Stop events follow but are not surfaced — we do not consume them so
        // callers that only need live daemons skip the trailing bytes.
        return result;
    }

    private AddressView readAddress(Decoder decoder) throws Exception {
        byte type = decoder.readByte();
        switch (type) {
            case ADDRESS_TYPE_SOCKET_INET:
                InetAddress host = InetAddress.getByAddress(decoder.readBinary());
                int port = decoder.readInt();
                return new AddressView(host, port);
            case ADDRESS_TYPE_MULTI_CHOICE:
                // MultiChoiceAddress: UUID (2 longs) + int port + smallInt count + N binary candidates.
                decoder.readLong();
                decoder.readLong();
                int mcPort = decoder.readInt();
                int candidateCount = decoder.readSmallInt();
                InetAddress firstCandidate = null;
                for (int i = 0; i < candidateCount; i++) {
                    InetAddress candidate = InetAddress.getByAddress(decoder.readBinary());
                    if (firstCandidate == null) {
                        firstCandidate = candidate;
                    }
                }
                if (firstCandidate == null) {
                    throw new IllegalStateException("MultiChoiceAddress with no candidates");
                }
                return new AddressView(firstCandidate, mcPort);
            default:
                // Legacy Java-serialized address (pre-3.1) — unsupported.
                throw new IllegalStateException("Unsupported registry address type: " + type);
        }
    }
}
