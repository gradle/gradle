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

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.launcher.daemon.server.api.DaemonCommandAction;
import org.gradle.launcher.daemon.server.api.DaemonCommandExecution;

class DaemonHealthTracker implements DaemonCommandAction {

    private final static Logger LOG = Logging.getLogger(DaemonHealthTracker.class);

    private final DaemonStats stats;
    private final DaemonStatus status;
    private final HealthLogger logger;

    DaemonHealthTracker(DaemonStats stats, DaemonStatus status, HealthLogger logger) {
        this.stats = stats;
        this.status = status;
        this.logger = logger;
    }

    public void execute(DaemonCommandExecution execution) {
        if (execution.isSingleUseDaemon()) {
            execution.proceed();
            return;
        }

        stats.buildStarted();
        logger.logHealth(stats, LOG);
        try {
            execution.proceed();
        } finally {
            stats.buildFinished();
        }

        if(status.isDaemonTired(stats)) {
            execution.getDaemonStateControl().requestStop();
        }
    }
}
