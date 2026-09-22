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
import org.gradle.internal.remote.internal.inet.MultiChoiceAddress;
import org.gradle.launcher.daemon.management.DaemonIndexEntry;
import org.gradle.launcher.daemon.management.DaemonStartSupport;
import org.gradle.launcher.daemon.management.ManagedDaemon;
import org.gradle.launcher.daemon.management.ManagedDaemons;
import org.gradle.launcher.daemon.management.UnsupportedDaemonVersionException;
import org.gradle.util.GradleVersion;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Finds daemons of every Gradle version under one daemon base directory.
 *
 * <p>Two sources are merged. The cross-version index holds an entry per daemon that publishes one, which
 * is every daemon from the version that introduced the index onwards. The per-version registries hold
 * everything else, read with the layout of the version that wrote each one. A daemon that appears in both
 * is reported once, preferring its index entry, which is the source designed to be read from outside.
 *
 * <p>Neither source is trusted to be current. A daemon killed outright leaves its entry behind in both,
 * so entries are reported as found and liveness is a question asked of the daemon, not of the file.
 */
public class DefaultManagedDaemons implements ManagedDaemons {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultManagedDaemons.class);
    private static final String REGISTRY_FILE_NAME = "registry.bin";
    private static final long START_TIMEOUT_MILLIS = 30000;
    private static final long START_POLL_MILLIS = 100;
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private final File daemonBaseDir;
    private final DaemonIndexStore indexStore;
    private final LegacyDaemonRegistryReader legacyReader;
    private final DaemonControlChannel channel;
    private final ProcessController processes;
    private final @Nullable DaemonStartSupport startSupport;

    public DefaultManagedDaemons(File daemonBaseDir, @Nullable DaemonStartSupport startSupport) {
        this(daemonBaseDir, new DaemonIndexStore(daemonBaseDir), new LegacyDaemonRegistryReader(), new DaemonControlChannel(), new ProcessController(), startSupport);
    }

    DefaultManagedDaemons(
        File daemonBaseDir,
        DaemonIndexStore indexStore,
        LegacyDaemonRegistryReader legacyReader,
        DaemonControlChannel channel,
        ProcessController processes,
        @Nullable DaemonStartSupport startSupport
    ) {
        this.daemonBaseDir = daemonBaseDir;
        this.indexStore = indexStore;
        this.legacyReader = legacyReader;
        this.channel = channel;
        this.processes = processes;
        this.startSupport = startSupport;
    }

    @Override
    public List<ManagedDaemon> all() {
        Map<String, ManagedDaemon> byUid = new LinkedHashMap<String, ManagedDaemon>();
        for (DaemonIndexEntry entry : indexStore.getAll()) {
            ManagedDaemon daemon = fromIndex(entry);
            if (daemon != null) {
                byUid.put(daemon.getUid(), daemon);
            }
        }
        for (File versionDir : versionDirectories()) {
            GradleVersion version = parseVersion(versionDir.getName());
            if (version == null) {
                continue;
            }
            for (LegacyDaemonRecord record : legacyReader.read(new File(versionDir, REGISTRY_FILE_NAME), version)) {
                String uid = record.getUid() != null ? record.getUid() : syntheticUid(record);
                if (byUid.containsKey(uid)) {
                    continue;
                }
                byUid.put(uid, new DefaultManagedDaemon(
                    version,
                    uid,
                    record.getPid(),
                    record.getAddress(),
                    record.getToken(),
                    record.getState(),
                    ManagedDaemon.Discovery.LEGACY_REGISTRY,
                    channel,
                    processes,
                    null
                ));
            }
        }
        return new ArrayList<ManagedDaemon>(byUid.values());
    }

    @Override
    public ManagedDaemon start(File projectDir) throws UnsupportedDaemonVersionException {
        String requestedVersion = versionOf(projectDir);
        String currentVersion = GradleVersion.current().getVersion();
        if (!currentVersion.equals(requestedVersion)) {
            throw new UnsupportedDaemonVersionException(requestedVersion,
                "Cannot start a Gradle " + requestedVersion + " daemon from Gradle " + currentVersion + ". "
                    + "Only the wrapper can resolve another version's distribution. "
                    + "Run the build through that project's wrapper instead.");
        }
        if (startSupport == null) {
            throw new UnsupportedDaemonVersionException(currentVersion, "This client cannot start daemons.");
        }
        String uid = startSupport.startDaemon();
        ManagedDaemon started = awaitAppearance(uid);
        if (started == null) {
            throw new IllegalStateException("Started a daemon but it did not appear in the daemon index at " + indexStore.getIndexDir());
        }
        return started;
    }

    /**
     * The Gradle version the given project builds with, taken from its wrapper configuration.
     */
    String versionOf(File projectDir) {
        File properties = new File(new File(projectDir, "gradle"), "wrapper/gradle-wrapper.properties");
        if (!properties.isFile()) {
            return GradleVersion.current().getVersion();
        }
        try {
            String text = new String(java.nio.file.Files.readAllBytes(properties.toPath()), UTF_8);
            for (String line : text.split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.startsWith("distributionUrl")) {
                    continue;
                }
                int gradlePrefix = trimmed.lastIndexOf("gradle-");
                int suffix = trimmed.lastIndexOf("-bin.zip");
                if (suffix < 0) {
                    suffix = trimmed.lastIndexOf("-all.zip");
                }
                if (gradlePrefix >= 0 && suffix > gradlePrefix) {
                    return trimmed.substring(gradlePrefix + "gradle-".length(), suffix);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Could not read the Gradle version from {}.", properties, e);
        }
        return GradleVersion.current().getVersion();
    }

    private @Nullable ManagedDaemon awaitAppearance(String uid) {
        long giveUpAt = System.currentTimeMillis() + START_TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < giveUpAt) {
            for (ManagedDaemon daemon : all()) {
                if (daemon.getUid().equals(uid)) {
                    return daemon;
                }
            }
            try {
                Thread.sleep(START_POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private @Nullable ManagedDaemon fromIndex(DaemonIndexEntry entry) {
        Address address = toAddress(entry);
        if (address == null) {
            return null;
        }
        GradleVersion version = parseVersion(entry.getGradleVersion());
        if (version == null) {
            return null;
        }
        return new DefaultManagedDaemon(
            version,
            entry.getUid(),
            entry.getPid(),
            address,
            entry.getToken(),
            "Unknown",
            ManagedDaemon.Discovery.INDEX,
            channel,
            processes,
            indexStore
        );
    }

    private static @Nullable Address toAddress(DaemonIndexEntry entry) {
        List<InetAddress> candidates = new ArrayList<InetAddress>(entry.getAddresses().size());
        for (String host : entry.getAddresses()) {
            try {
                candidates.add(InetAddress.getByName(host));
            } catch (Exception e) {
                LOGGER.debug("Ignoring unusable daemon address {}.", host, e);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        // The canonical address identifies the peer within a message hub and plays no part in connecting,
        // so it is derived from the uid rather than carried in the contract.
        return new MultiChoiceAddress(UUID.nameUUIDFromBytes(entry.getUid().getBytes(UTF_8)), entry.getPort(), candidates);
    }

    private List<File> versionDirectories() {
        File[] children = daemonBaseDir.listFiles();
        if (children == null) {
            return new ArrayList<File>();
        }
        List<File> result = new ArrayList<File>(children.length);
        for (File child : children) {
            if (child.isDirectory() && !DaemonIndexStore.INDEX_DIR_NAME.equals(child.getName())) {
                result.add(child);
            }
        }
        return result;
    }

    private static @Nullable GradleVersion parseVersion(String name) {
        try {
            return GradleVersion.version(name);
        } catch (IllegalArgumentException notAVersion) {
            return null;
        }
    }

    /**
     * An identifier for a daemon whose registry record did not survive far enough to carry its own.
     */
    private static String syntheticUid(LegacyDaemonRecord record) {
        return record.getGradleVersion().getVersion() + "@" + record.getAddress().getDisplayName();
    }
}
