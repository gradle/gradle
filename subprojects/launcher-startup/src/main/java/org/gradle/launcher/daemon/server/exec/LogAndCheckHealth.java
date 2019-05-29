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
import org.gradle.launcher.daemon.server.api.DaemonCommandAction;
import org.gradle.launcher.daemon.server.api.DaemonCommandExecution;
import org.gradle.launcher.daemon.server.health.DaemonHealthCheck;
import org.gradle.launcher.daemon.server.health.DaemonHealthStats;
import org.gradle.launcher.daemon.server.health.HealthLogger;

public class LogAndCheckHealth implements DaemonCommandAction {

    private static final Logger LOG = Logging.getLogger(LogAndCheckHealth.class);

    private final DaemonHealthStats stats;
    private final DaemonHealthCheck healthCheck;
    private final HealthLogger logger;

    public LogAndCheckHealth(DaemonHealthStats stats, DaemonHealthCheck healthCheck) {
        this(stats, healthCheck, new HealthLogger());
    }

    @VisibleForTesting
    LogAndCheckHealth(DaemonHealthStats stats, DaemonHealthCheck healthCheck, HealthLogger logger) {
        this.stats = stats;
        this.healthCheck = healthCheck;
        this.logger = logger;
    }

    @Override
    public void execute(DaemonCommandExecution execution) {
        if (execution.isSingleUseDaemon()) {
            execution.proceed();
            return;
        }

        logger.logHealth(stats, LOG);
        execution.proceed();

        // Execute the health check that should send out a DaemonExpiration event
        // if the daemon is unhealthy
        healthCheck.executeHealthCheck();
    }
}
