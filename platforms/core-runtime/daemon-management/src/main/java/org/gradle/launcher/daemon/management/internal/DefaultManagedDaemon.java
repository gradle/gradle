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
import org.gradle.launcher.daemon.management.DaemonStatus;
import org.gradle.launcher.daemon.management.ManagedDaemon;
import org.gradle.launcher.daemon.protocol.CancelBuild;
import org.gradle.launcher.daemon.protocol.ReportStatus;
import org.gradle.launcher.daemon.protocol.Status;
import org.gradle.launcher.daemon.protocol.Stop;
import org.gradle.launcher.daemon.protocol.StopWhenIdle;
import org.gradle.util.GradleVersion;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

/**
 * A daemon reached over its socket, falling back to its process when it stops answering.
 */
public class DefaultManagedDaemon implements ManagedDaemon {

    /**
     * The first Gradle version whose daemon understands a cancel request from a process other than the
     * one that started the build.
     */
    static final GradleVersion CANCEL_SUPPORTED_FROM = GradleVersion.version("9.9");

    private static final long STOP_GRACE_MILLIS = 10000;
    private static final long KILL_GRACE_MILLIS = 5000;
    private static final long POLL_INTERVAL_MILLIS = 100;

    private final GradleVersion gradleVersion;
    private final String uid;
    private final @Nullable Long pid;
    private final Address address;
    private final byte[] token;
    private final String recordedState;
    private final Discovery discovery;
    private final DaemonControlChannel channel;
    private final ProcessController processes;
    private final @Nullable DaemonIndexStore indexStore;

    public DefaultManagedDaemon(
        GradleVersion gradleVersion,
        String uid,
        @Nullable Long pid,
        Address address,
        byte[] token,
        String recordedState,
        Discovery discovery,
        DaemonControlChannel channel,
        ProcessController processes,
        @Nullable DaemonIndexStore indexStore
    ) {
        this.gradleVersion = gradleVersion;
        this.uid = uid;
        this.pid = pid;
        this.address = address;
        this.token = token;
        this.recordedState = recordedState;
        this.discovery = discovery;
        this.channel = channel;
        this.processes = processes;
        this.indexStore = indexStore;
    }

    @Override
    public String getGradleVersion() {
        return gradleVersion.getVersion();
    }

    @Override
    public String getUid() {
        return uid;
    }

    @Override
    public @Nullable Long getPid() {
        return pid;
    }

    @Override
    public Discovery getDiscovery() {
        return discovery;
    }

    @Override
    public boolean isAlive() {
        return channel.isReachable(address, gradleVersion) || (pid != null && processes.isAlive(pid));
    }

    @Override
    public DaemonStatus status() {
        if (isCurrentVersion()) {
            DaemonControlChannel.Reply reply = channel.send(address, gradleVersion, new ReportStatus(newId(), token));
            Object value = reply.getValue();
            if (reply.isUnderstood() && value instanceof Status) {
                Status status = (Status) value;
                return new DaemonStatus(status.getVersion(), status.getPid(), status.getStatus(), DaemonStatus.Source.LIVE);
            }
        }
        if (channel.isReachable(address, gradleVersion)) {
            // The daemon answers but its replies cannot be decoded across versions, so the best available
            // state is the one it wrote down for itself.
            return new DaemonStatus(getGradleVersion(), pid, recordedState.toUpperCase(Locale.ROOT), DaemonStatus.Source.RECORDED);
        }
        return new DaemonStatus(getGradleVersion(), pid, "UNKNOWN", DaemonStatus.Source.UNREACHABLE);
    }

    @Override
    public boolean stop() {
        DaemonControlChannel.Reply reply = channel.send(address, gradleVersion, new Stop(newId(), token));
        boolean gone;
        if (reply.isDelivered()) {
            gone = awaitDeath(STOP_GRACE_MILLIS);
        } else if (pid == null) {
            // Nothing is listening and there is no process to fall back on.
            gone = !isAlive();
        } else {
            // Nothing is listening. Either the entry is stale and the process is long gone, or the daemon
            // is wedged. Both are cases where a user asking for daemons to stop wants the process gone.
            gone = processes.terminate(pid, KILL_GRACE_MILLIS);
        }
        if (gone) {
            forgetIndexEntry();
        }
        return gone;
    }

    /**
     * Drops the index entry of a daemon that is gone.
     *
     * <p>A daemon normally withdraws its own entry as it shuts down. One that was killed outright never
     * got the chance, and its entry would otherwise be reported for ever. Only index entries are cleaned
     * up this way: rewriting another version's registry would mean writing a format this process does not
     * own, and that version's own client removes its stale entries anyway.
     */
    private void forgetIndexEntry() {
        if (indexStore != null && discovery == Discovery.INDEX) {
            indexStore.remove(uid);
        }
    }

    @Override
    public boolean stopWhenIdle() {
        return channel.send(address, gradleVersion, new StopWhenIdle(newId(), token)).isDelivered();
    }

    @Override
    public CancelOutcome cancelBuild() {
        if (gradleVersion.getBaseVersion().compareTo(CANCEL_SUPPORTED_FROM) < 0) {
            return CancelOutcome.UNSUPPORTED;
        }
        DaemonControlChannel.Reply reply = channel.send(address, gradleVersion, new CancelBuild(newId(), token));
        if (!reply.isDelivered()) {
            return CancelOutcome.FAILED;
        }
        if (reply.isUnderstood() && Boolean.FALSE.equals(reply.getValue())) {
            return CancelOutcome.NOTHING_RUNNING;
        }
        return CancelOutcome.REQUESTED;
    }

    private boolean awaitDeath(long deadlineMillis) {
        long giveUpAt = System.currentTimeMillis() + deadlineMillis;
        while (System.currentTimeMillis() < giveUpAt) {
            if (!isAlive()) {
                return true;
            }
            try {
                Thread.sleep(POLL_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return !isAlive();
    }

    private boolean isCurrentVersion() {
        return GradleVersion.current().getVersion().equals(gradleVersion.getVersion());
    }

    private static UUID newId() {
        return UUID.randomUUID();
    }

    @Override
    public String toString() {
        return "ManagedDaemon{version=" + getGradleVersion() + ", pid=" + pid + ", discovery=" + discovery + "}";
    }
}
