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

package org.gradle.launcher.daemon.server.exec;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.util.NumberUtil;
import org.gradle.launcher.daemon.server.api.DaemonCommandAction;
import org.gradle.launcher.daemon.server.api.DaemonCommandExecution;
import org.gradle.launcher.daemon.server.health.DaemonHealthCheck;
import org.gradle.launcher.daemon.server.health.DaemonHealthStats;
import org.gradle.launcher.daemon.server.stats.DaemonRunningStats;

public class LogAndCheckHealth implements DaemonCommandAction {

    public static final String HEALTH_MESSAGE_PROPERTY = "org.gradle.daemon.performance.logging";

    private final DaemonHealthStats stats;
    private final DaemonHealthCheck healthCheck;
    private final DaemonRunningStats runningStats;
    private final Logger logger;

    public LogAndCheckHealth(DaemonHealthStats stats, DaemonHealthCheck healthCheck, DaemonRunningStats runningStats) {
        this(stats, healthCheck, runningStats, Logging.getLogger(LogAndCheckHealth.class));
    }

    @VisibleForTesting
    LogAndCheckHealth(DaemonHealthStats stats, DaemonHealthCheck healthCheck, DaemonRunningStats runningStats, Logger logger) {
        this.stats = stats;
        this.healthCheck = healthCheck;
        this.runningStats = runningStats;
        this.logger = logger;
    }

    @Override
    public void execute(DaemonCommandExecution execution) {
        if (execution.isSingleUseDaemon()) {
            execution.proceed();
            return;
        }

        if (Boolean.getBoolean(LogAndCheckHealth.HEALTH_MESSAGE_PROPERTY)) {
            logger.lifecycle(getStartBuildMessage());
        } else {
            // The default.
            logger.info(getStartBuildMessage());
        }

        execution.proceed();

        // Execute the health check that should send out a DaemonExpiration event
        // if the daemon is unhealthy
        healthCheck.executeHealthCheck();
    }

    private String getStartBuildMessage() {
        int nextBuildNum = runningStats.getBuildCount() + 1;
        if (nextBuildNum == 1) {
            return String.format("Starting build in new daemon [memory: %s]", NumberUtil.formatBytes(Runtime.getRuntime().maxMemory()));
        } else {
            return String.format("Starting %s build in daemon %s", NumberUtil.ordinal(nextBuildNum), stats.getHealthInfo());
        }
    }
}
