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

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationResult;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStrategy;

import static org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus.IMMEDIATE_EXPIRE;

public class GcThrashingDaemonExpirationStrategy implements DaemonExpirationStrategy {
    private final DaemonMemoryStatus status;
    private static final Logger LOG = Logging.getLogger(GcThrashingDaemonExpirationStrategy.class);

    public static final String EXPIRATION_REASON = "JVM garbage collector thrashing";

    public GcThrashingDaemonExpirationStrategy(DaemonMemoryStatus status) {
        this.status = status;
    }

    @Override
    public DaemonExpirationResult checkExpiration() {
        if (status.isThrashing()) {
            LOG.info("JVM garbage collector is thrashing. Daemon will be stopped immediately");
            return new DaemonExpirationResult(IMMEDIATE_EXPIRE, EXPIRATION_REASON);
        } else {
            return DaemonExpirationResult.NOT_TRIGGERED;
        }
    }
}
