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

package org.gradle.launcher.daemon.server.health;

import org.gradle.launcher.daemon.registry.DaemonRegistry;

public class DefaultDaemonInformation implements DaemonInformation {
    private final DaemonStats stats;
    private final long idleTimeout;
    private final DaemonRegistry daemonRegistry;

    public static DefaultDaemonInformation of(DaemonStats stats, int idleTimeout, DaemonRegistry daemonRegistry) {
        return new DefaultDaemonInformation(stats, (long) idleTimeout, daemonRegistry);
    }

    private DefaultDaemonInformation(DaemonStats stats, long idleTimeout, DaemonRegistry daemonRegistry) {
        this.stats = stats;
        this.idleTimeout = idleTimeout;
        this.daemonRegistry = daemonRegistry;
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
}
