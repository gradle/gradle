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
import org.gradle.launcher.daemon.management.DaemonIndexEntry;
import org.gradle.internal.remote.internal.inet.InetEndpoint;
import org.gradle.util.GradleVersion;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * How a daemon announces itself to processes of other Gradle versions.
 *
 * <p>Called once when the daemon has started listening and once when it stops. Nothing is written in
 * between, so no part of running a build goes through here.
 *
 * <p>Failing to publish is never fatal. An unpublished daemon is one that tools have to find the slower
 * way, through its own version's registry, which is exactly the situation for every daemon released
 * before this existed. Failing a build over it would be out of all proportion.
 */
public class DaemonIndexPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(DaemonIndexPublisher.class);

    private final DaemonIndexStore store;

    public DaemonIndexPublisher(File daemonBaseDir) {
        this.store = new DaemonIndexStore(daemonBaseDir);
    }

    public void publish(String uid, @Nullable Long pid, Address address, byte[] token, File daemonBaseDir, @Nullable File javaHome, @Nullable File logFile) {
        if (!(address instanceof InetEndpoint)) {
            LOGGER.debug("Not publishing daemon {} to the cross-version index: its address {} is not a socket address.", uid, address);
            return;
        }
        InetEndpoint endpoint = (InetEndpoint) address;
        List<String> hosts = new ArrayList<String>();
        for (InetAddress candidate : endpoint.getCandidates()) {
            hosts.add(candidate.getHostAddress());
        }
        DaemonIndexEntry entry = new DaemonIndexEntry(
            DaemonIndexEntry.CURRENT_SCHEMA_VERSION,
            GradleVersion.current().getVersion(),
            uid,
            pid,
            endpoint.getPort(),
            hosts,
            token,
            System.currentTimeMillis(),
            daemonBaseDir.getPath(),
            javaHome == null ? null : javaHome.getPath(),
            logFile == null ? null : logFile.getPath()
        );
        try {
            store.store(entry);
        } catch (Exception e) {
            LOGGER.debug("Could not publish daemon {} to the cross-version index.", uid, e);
        }
    }

    public void withdraw(String uid) {
        try {
            store.remove(uid);
        } catch (Exception e) {
            LOGGER.debug("Could not remove daemon {} from the cross-version index.", uid, e);
        }
    }
}
