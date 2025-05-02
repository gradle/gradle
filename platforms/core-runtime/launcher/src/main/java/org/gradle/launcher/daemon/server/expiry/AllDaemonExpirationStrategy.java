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

package org.gradle.launcher.daemon.server.expiry;

import com.google.common.base.Joiner;

import java.util.ArrayList;
import java.util.List;

import static org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus.DO_NOT_EXPIRE;
import static org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus.highestPriorityOf;

/**
 * Expires the daemon only if all children would expire the daemon.
 */
public class AllDaemonExpirationStrategy implements DaemonExpirationStrategy {
    private Iterable<DaemonExpirationStrategy> expirationStrategies;

    public AllDaemonExpirationStrategy(List<DaemonExpirationStrategy> expirationStrategies) {
        this.expirationStrategies = expirationStrategies;
    }

    @Override
    public DaemonExpirationResult checkExpiration() {
        // If no expiration strategies exist, the daemon will not expire.
        DaemonExpirationResult expirationResult = DaemonExpirationResult.NOT_TRIGGERED;
        DaemonExpirationStatus expirationStatus = DO_NOT_EXPIRE;

        List<String> reasons = new ArrayList<>();
        for (DaemonExpirationStrategy expirationStrategy : expirationStrategies) {
            // If any of the child strategies don't expire the daemon, the daemon will not expire.
            // Otherwise, the daemon will expire and aggregate the reasons together.
            expirationResult = expirationStrategy.checkExpiration();

            if (expirationResult.getStatus() == DO_NOT_EXPIRE) {
                return DaemonExpirationResult.NOT_TRIGGERED;
            } else {
                reasons.add(expirationResult.getReason());
                expirationStatus = highestPriorityOf(expirationResult.getStatus(), expirationStatus);
            }
        }

        if (expirationResult.getStatus() == DO_NOT_EXPIRE) {
            return DaemonExpirationResult.NOT_TRIGGERED;
        } else {
            return new DaemonExpirationResult(expirationStatus, Joiner.on(" and ").skipNulls().join(reasons));
        }
    }
}
