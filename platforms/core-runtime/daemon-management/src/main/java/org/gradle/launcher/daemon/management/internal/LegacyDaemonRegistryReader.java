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

package org.gradle.launcher.daemon.management.internal;

import org.gradle.internal.remote.Address;
import org.gradle.internal.remote.internal.inet.MultiChoiceAddressSerializer;
import org.gradle.internal.remote.internal.inet.SocketInetAddress;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.InputStreamBackedDecoder;
import org.gradle.util.GradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the per-version {@code registry.bin} of any Gradle version, including versions older than this
 * reader.
 *
 * <p>This is how daemons of already released versions are found. They cannot be taught to publish a
 * cross-version index, so the only thing they leave behind is the registry their own version wrote.
 *
 * <p>Two things make reading it across versions safe. The outer layout, the address table and the
 * identity fields of each record, has not changed since Gradle 7.0, and the one part that did change,
 * the daemon context, varies in a way fully determined by the version in the directory name. See
 * {@link DaemonContextFormat}.
 *
 * <p>The file is read without taking its lock. The lock format is itself version specific, so acquiring
 * it across versions would reintroduce the coupling this class exists to avoid. The cost is that a read
 * can land in the middle of another process rewriting the file, which is handled by reading again: a
 * torn read fails to parse rather than producing plausible wrong values, because every record is length
 * prefixed and bounded by the address count.
 */
public class LegacyDaemonRegistryReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyDaemonRegistryReader.class);
    private static final MultiChoiceAddressSerializer MULTI_CHOICE_ADDRESS_SERIALIZER = new MultiChoiceAddressSerializer();
    private static final int READ_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MILLIS = 50;

    /**
     * The daemon states, by the ordinal they are stored with.
     *
     * <p>Deliberately a copy rather than a reference to the live enum: these ordinals describe files
     * written by past versions, and must keep meaning what they meant when those versions wrote them
     * even if the enum is reordered later.
     */
    private static final String[] STATES = {"Idle", "Busy", "Canceled", "StopRequested", "Stopped", "ForceStopped", "Broken"};

    public List<LegacyDaemonRecord> read(File registryFile, GradleVersion version) {
        if (!registryFile.isFile()) {
            return new ArrayList<LegacyDaemonRecord>();
        }
        Exception lastFailure = null;
        for (int attempt = 0; attempt < READ_ATTEMPTS; attempt++) {
            try {
                return parse(Files.readAllBytes(registryFile.toPath()), version);
            } catch (Exception e) {
                lastFailure = e;
                sleepBeforeRetry();
            }
        }
        LOGGER.debug("Could not read the daemon registry {} of Gradle {}.", registryFile, version.getVersion(), lastFailure);
        return new ArrayList<LegacyDaemonRecord>();
    }

    private static void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_DELAY_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    List<LegacyDaemonRecord> parse(byte[] content, GradleVersion version) throws Exception {
        DaemonContextFormat format = DaemonContextFormat.forVersion(version);
        InputStreamBackedDecoder decoder = new InputStreamBackedDecoder(new ByteArrayInputStream(content));
        if (!decoder.readBoolean()) {
            return new ArrayList<LegacyDaemonRecord>();
        }
        List<Address> addresses = readAddresses(decoder);
        List<LegacyDaemonRecord> records = new ArrayList<LegacyDaemonRecord>(addresses.size());
        for (int i = 0; i < addresses.size(); i++) {
            Address address = addresses.get(decoder.readInt());
            byte[] token = decoder.readBinary();
            String state = stateName(decoder.readByte());
            decoder.readLong(); // last busy
            DaemonContextFormat.Context context;
            try {
                context = format.read(decoder);
            } catch (Exception e) {
                // The identity of this daemon is already known, only the trailing context failed. Keep
                // what was read and stop, because without the context length the next record's offset
                // is unknown.
                records.add(new LegacyDaemonRecord(version, null, address, token, state, null, null));
                LOGGER.debug("Stopped reading the Gradle {} daemon registry after {} of {} records.", version.getVersion(), records.size(), addresses.size(), e);
                return records;
            }
            records.add(new LegacyDaemonRecord(version, context.getUid(), address, token, state, context.getPid(), context.getJavaHome()));
        }
        return records;
    }

    private static String stateName(byte ordinal) {
        return ordinal >= 0 && ordinal < STATES.length ? STATES[ordinal] : "Unknown";
    }

    private static List<Address> readAddresses(Decoder decoder) throws Exception {
        int count = decoder.readInt();
        if (count < 0) {
            throw new IOException("Daemon registry declares a negative number of entries: " + count);
        }
        List<Address> addresses = new ArrayList<Address>(Math.min(count, 64));
        for (int i = 0; i < count; i++) {
            byte type = decoder.readByte();
            switch (type) {
                case 0:
                    addresses.add(SocketInetAddress.SERIALIZER.read(decoder));
                    break;
                case 1:
                    addresses.add(MULTI_CHOICE_ADDRESS_SERIALIZER.read(decoder));
                    break;
                default:
                    ObjectInputStream stream = new ObjectInputStream(decoder.getInputStream());
                    addresses.add((Address) stream.readObject());
            }
        }
        return addresses;
    }
}
