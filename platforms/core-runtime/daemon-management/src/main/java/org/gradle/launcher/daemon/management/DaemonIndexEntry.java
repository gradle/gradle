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

package org.gradle.launcher.daemon.management;

import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * What one daemon publishes about itself so that processes of any Gradle version can find and manage it.
 *
 * <p>This is a published contract. A daemon writes one of these when it starts and deletes it when it
 * stops, and tools of unrelated Gradle versions read them.
 *
 * <p>The rule that keeps it usable across versions: new fields may be added at any time and readers
 * ignore fields they do not know, so adding one does not change the
 * {@link #getSchemaVersion() schema version}. Removing a field, renaming one, or giving one a new
 * meaning is what requires a new schema version, and a reader refuses entries whose schema version it
 * does not know rather than guessing at them.
 *
 * <p>The entry holds only what is needed to identify a daemon and open a connection to it. Everything
 * that changes while the daemon runs, its state above all, is asked of the daemon itself over the
 * connection, so that an entry never goes stale while its daemon is alive.
 */
public final class DaemonIndexEntry {

    /**
     * The schema this entry was written with. Readers reject entries whose major schema they do not know.
     */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private final int schemaVersion;
    private final String gradleVersion;
    private final String uid;
    private final @Nullable Long pid;
    private final int port;
    private final List<String> addresses;
    private final byte[] token;
    private final long startedAt;
    private final String daemonBaseDir;
    private final @Nullable String javaHome;
    private final @Nullable String logFile;

    public DaemonIndexEntry(
        int schemaVersion,
        String gradleVersion,
        String uid,
        @Nullable Long pid,
        int port,
        List<String> addresses,
        byte[] token,
        long startedAt,
        String daemonBaseDir,
        @Nullable String javaHome,
        @Nullable String logFile
    ) {
        this.schemaVersion = schemaVersion;
        this.gradleVersion = gradleVersion;
        this.uid = uid;
        this.pid = pid;
        this.port = port;
        this.addresses = Collections.unmodifiableList(new java.util.ArrayList<String>(addresses));
        this.token = token.clone();
        this.startedAt = startedAt;
        this.daemonBaseDir = daemonBaseDir;
        this.javaHome = javaHome;
        this.logFile = logFile;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    /**
     * The full Gradle version of the daemon, as {@code org.gradle.util.GradleVersion} spells it.
     */
    public String getGradleVersion() {
        return gradleVersion;
    }

    /**
     * The daemon's unique identifier, which is also the name of the file holding this entry.
     */
    public String getUid() {
        return uid;
    }

    /**
     * The daemon's process id, absent when the daemon could not determine it.
     */
    public @Nullable Long getPid() {
        return pid;
    }

    public int getPort() {
        return port;
    }

    /**
     * Host addresses the daemon listens on, in the order it offers them. A client tries each in turn.
     */
    public List<String> getAddresses() {
        return addresses;
    }

    /**
     * The authentication token a client must present with every command. Holding it is what permits
     * managing this daemon, which is why entries are readable only by their owner.
     */
    public byte[] getToken() {
        return token.clone();
    }

    /**
     * When the daemon started, in milliseconds since the epoch.
     */
    public long getStartedAt() {
        return startedAt;
    }

    /**
     * The daemon base directory this daemon registered under, which is the directory holding the
     * per-version registry directories.
     */
    public String getDaemonBaseDir() {
        return daemonBaseDir;
    }

    public @Nullable String getJavaHome() {
        return javaHome;
    }

    public @Nullable String getLogFile() {
        return logFile;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DaemonIndexEntry that = (DaemonIndexEntry) o;
        return schemaVersion == that.schemaVersion
            && port == that.port
            && startedAt == that.startedAt
            && gradleVersion.equals(that.gradleVersion)
            && uid.equals(that.uid)
            && Objects.equals(pid, that.pid)
            && addresses.equals(that.addresses)
            && Arrays.equals(token, that.token)
            && daemonBaseDir.equals(that.daemonBaseDir)
            && Objects.equals(javaHome, that.javaHome)
            && Objects.equals(logFile, that.logFile);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schemaVersion, gradleVersion, uid, pid, port, addresses, Arrays.hashCode(token), startedAt, daemonBaseDir, javaHome, logFile);
    }

    @Override
    public String toString() {
        return "DaemonIndexEntry{gradleVersion=" + gradleVersion + ", uid=" + uid + ", pid=" + pid + ", port=" + port + "}";
    }
}
