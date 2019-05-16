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

public class HealthLogger {

    static final String HEALTH_MESSAGE_PROPERTY = "org.gradle.daemon.performance.logging";

    public void logHealth(DaemonHealthStats stats, Logger logger) {
        if (Boolean.getBoolean(HEALTH_MESSAGE_PROPERTY)) {
            logger.lifecycle(stats.getHealthInfo());
        } else {
            //the default
            logger.info(stats.getHealthInfo());
        }
    }
}
