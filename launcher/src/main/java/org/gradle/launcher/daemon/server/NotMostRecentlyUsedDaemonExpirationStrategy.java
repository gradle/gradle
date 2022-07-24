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

package org.gradle.launcher.daemon.server;

import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.registry.DaemonInfo;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationResult;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStrategy;

import java.util.Collection;
import java.util.Date;

public class NotMostRecentlyUsedDaemonExpirationStrategy implements DaemonExpirationStrategy {
    private final Daemon daemon;
    public static final String EXPIRATION_REASON = "not recently used";

    NotMostRecentlyUsedDaemonExpirationStrategy(Daemon daemon) {
        this.daemon = daemon;
    }

    @Override
    public DaemonExpirationResult checkExpiration() {
        if (!isMostRecentlyUsed(daemon.getDaemonRegistry().getIdle(), daemon.getDaemonContext())) {
            return new DaemonExpirationResult(DaemonExpirationStatus.GRACEFUL_EXPIRE, EXPIRATION_REASON);
        }
        return DaemonExpirationResult.NOT_TRIGGERED;
    }

    private boolean isMostRecentlyUsed(Collection<DaemonInfo> daemonInfos, DaemonContext thisDaemonContext) {
        String mruUid = null;
        Date mruTimestamp = new Date(Long.MIN_VALUE);
        for (DaemonInfo daemonInfo : daemonInfos) {
            Date daemonAccessTime = daemonInfo.getLastBusy();
            if (daemonAccessTime.after(mruTimestamp)) {
                mruUid = daemonInfo.getUid();
                mruTimestamp = daemonAccessTime;
            }
        }
        return thisDaemonContext.getUid().equals(mruUid);
    }
}
