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
import com.google.common.collect.Lists;

import java.util.List;

import static org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus.*;

/**
 * Expires the daemon if any of the children would expire the daemon.
 */
public class AnyDaemonExpirationStrategy implements DaemonExpirationStrategy {
    private Iterable<DaemonExpirationStrategy> expirationStrategies;

    public AnyDaemonExpirationStrategy(List<DaemonExpirationStrategy> expirationStrategies) {
        this.expirationStrategies = expirationStrategies;
    }

    @Override
    public DaemonExpirationResult checkExpiration() {
        DaemonExpirationResult expirationResult;
        DaemonExpirationStatus expirationStatus = DO_NOT_EXPIRE;
        List<String> reasons = Lists.newArrayList();

        for (DaemonExpirationStrategy expirationStrategy : expirationStrategies) {
            expirationResult = expirationStrategy.checkExpiration();
            if (expirationResult.getStatus() != DO_NOT_EXPIRE) {
                reasons.add(expirationResult.getReason());
                expirationStatus = highestPriorityOf(expirationResult.getStatus(), expirationStatus);
            }
        }

        if (expirationStatus == DO_NOT_EXPIRE) {
            return DaemonExpirationResult.NOT_TRIGGERED;
        } else {
            return new DaemonExpirationResult(expirationStatus, Joiner.on(" and ").skipNulls().join(reasons));
        }
    }
}
