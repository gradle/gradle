/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.launcher.daemon.server;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.registry.DaemonInfo;
import org.gradle.launcher.daemon.registry.DaemonRegistry;

import java.util.Date;

/**
 * This strategy expires the daemon if it's been idle for the longest consecutive period of time.
 * It will not expire a busy daemon, or the last daemon running.
 *
 */
public class LruDaemonExpirationStrategy implements DaemonExpirationStrategy {
    public DaemonExpirationResult checkExpiration(Daemon daemon) {
        DaemonRegistry registry = daemon.getDaemonRegistry();
        return registry.getAll().size() > 1 && isOldest(daemon.getDaemonContext(), registry)
            ? new DaemonExpirationResult(true, "This is the least recently used daemon")
            : new DaemonExpirationResult(false, null);
    }

    // Daemons are compared by PID; the assumption is that two will never have an identical PID.

    @VisibleForTesting
    boolean isOldest(DaemonContext thisDaemon, DaemonRegistry registry) {
        long oldestPid = -1;
        Date oldestTimestamp = new Date(Long.MAX_VALUE);
        for (DaemonInfo daemon : registry.getIdle()) {
            Date daemonAccessTime = daemon.getLastBusy();
            if (daemonAccessTime.before(oldestTimestamp)) {
                oldestPid = daemon.getPid();
                oldestTimestamp = daemonAccessTime;
            }
        }
        return oldestPid == thisDaemon.getPid();
    }
}
