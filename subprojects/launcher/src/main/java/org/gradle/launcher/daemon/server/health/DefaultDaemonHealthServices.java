/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.launcher.daemon.server.api.DaemonCommandAction;

import java.util.concurrent.ScheduledExecutorService;

public class DefaultDaemonHealthServices implements DaemonHealthServices {
    private final HintGCAfterBuild hygieneAction = new HintGCAfterBuild();
    private final DaemonStats stats;
    private final DaemonStatus status;
    private final HealthLogger logger = new HealthLogger();
    private final DaemonHealthTracker tracker;

    public DefaultDaemonHealthServices(ScheduledExecutorService scheduledExecutorService) {
        this.stats = new DaemonStats(scheduledExecutorService);
        this.status = new DaemonStatus(stats);
        this.tracker = new DaemonHealthTracker(stats, status, logger);
    }

    /**
     * {@inheritDoc}
     */
    public DaemonCommandAction getGCHintAction() {
        return hygieneAction;
    }

    /**
     * {@inheritDoc}
     */
    public DaemonCommandAction getHealthTrackerAction() {
        return tracker;
    }

    @Override
    public DaemonStats getDaemonStats() {
        return stats;
    }

    @Override
    public DaemonStatus getDaemonStatus() {
        return status;
    }
}
