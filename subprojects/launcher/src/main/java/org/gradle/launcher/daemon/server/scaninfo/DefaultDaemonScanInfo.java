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

package org.gradle.launcher.daemon.server.scaninfo;

import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.Action;
import org.gradle.internal.event.ListenerManager;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationListener;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationResult;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus;
import org.gradle.launcher.daemon.server.stats.DaemonRunningStats;

public class DefaultDaemonScanInfo implements DaemonScanInfo {

    private final DaemonRunningStats stats;
    private final long idleTimeout;
    private final DaemonRegistry daemonRegistry;
    private final ListenerManager listenerManager;

    public DefaultDaemonScanInfo(final DaemonRunningStats stats, final long idleTimeout, final DaemonRegistry daemonRegistry, final ListenerManager listenerManager) {
        this.stats = stats;
        this.idleTimeout = idleTimeout;
        this.daemonRegistry = daemonRegistry;
        this.listenerManager = listenerManager;
    }

    @Override
    public int getNumberOfBuilds() {
        return stats.getBuildCount();
    }

    @Override
    public long getStartedAt() {
        return stats.getStartTime();
    }

    @Override
    public long getIdleTimeout() {
        return idleTimeout;
    }

    @Override
    public int getNumberOfRunningDaemons() {
        return daemonRegistry.getAll().size();
    }

    @Override
    public void notifyOnUnhealthy(final Action<? super String> listener) {
        /*
            The semantics of this method are that the given action should be notified if the
            Daemon is going to be terminated at the end of this build.
            It is not a generic outlet for “expiry events”.

            Ideally, the value given would describe the problem and not be phrased in terms of why we are shutting down,
            but this is a practical compromise born out of piggy backing on the expiration listener mechanism to implement it.
         */
        final DaemonExpirationListener daemonExpirationListener = new DaemonExpirationListener() {
            @Override
            public void onExpirationEvent(DaemonExpirationResult result) {
                if (result.getStatus() == DaemonExpirationStatus.GRACEFUL_EXPIRE) {
                    try {
                        listener.execute(result.getReason());
                    } finally {
                        listenerManager.removeListener(this);
                    }
                }
            }
        };
        listenerManager.addListener(daemonExpirationListener);
        listenerManager.addListener(new BuildAdapter() {
            @Override
            public void buildFinished(BuildResult result) {
                listenerManager.removeListener(daemonExpirationListener);
            }
        });
    }

}
