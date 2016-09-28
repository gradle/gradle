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

import org.gradle.api.internal.specs.ExplainingSpec;
import org.gradle.api.specs.Spec;
import org.gradle.launcher.daemon.context.DaemonCompatibilitySpec;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.registry.DaemonInfo;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationResult;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStrategy;
import org.gradle.util.CollectionUtils;

import java.util.Collection;
import java.util.Date;

import static org.gradle.launcher.daemon.server.api.DaemonStateControl.State.*;

public class DuplicateIdleDaemonExpirationStrategy implements DaemonExpirationStrategy {
    public final static int IDLE_COMPATIBLE_TIMEOUT = 10 * 1000;
    private final Daemon daemon;
    private final ExplainingSpec<DaemonContext> compatibilitySpec;

    DuplicateIdleDaemonExpirationStrategy(Daemon daemon, ExplainingSpec<DaemonContext> compatibilitySpec) {
        this.daemon = daemon;
        this.compatibilitySpec = compatibilitySpec;
    }

    public DuplicateIdleDaemonExpirationStrategy(Daemon daemon) {
        this(daemon, new DaemonCompatibilitySpec(daemon.getDaemonContext()));
    }

    @Override
    public DaemonExpirationResult checkExpiration() {
        Spec<DaemonInfo> spec = new Spec<DaemonInfo>() {
            @Override
            public boolean isSatisfiedBy(DaemonInfo daemonInfo) {
                return compatibilitySpec.isSatisfiedBy(daemonInfo.getContext());
            }
        };
        Collection<DaemonInfo> compatibleIdleDaemons = CollectionUtils.filter(daemon.getDaemonRegistry().getIdle(), spec);

        if (compatibleIdleDaemons.size() > 1
            && daemon.getStateCoordinator().getState() == Idle
            && !isMostRecentlyUsed(compatibleIdleDaemons, daemon.getDaemonContext())
            && hasBeenIdle()) {
            return new DaemonExpirationResult(DaemonExpirationStatus.GRACEFUL_EXPIRE, "after other compatible daemons were started");
        } else {
            return DaemonExpirationResult.NOT_TRIGGERED;
        }
    }

    boolean isMostRecentlyUsed(Collection<DaemonInfo> compatibleDaemons, DaemonContext thisDaemonContext) {
        String mruUid = null;
        Date mruTimestamp = new Date(Long.MIN_VALUE);
        for (DaemonInfo daemonInfo : compatibleDaemons) {
            Date daemonAccessTime = daemonInfo.getLastBusy();
            if (daemonAccessTime.after(mruTimestamp)) {
                mruUid = daemonInfo.getUid();
                mruTimestamp = daemonAccessTime;
            }
        }
        return thisDaemonContext.getUid().equals(mruUid);
    }

    boolean hasBeenIdle() {
        long idleTime = daemon.getStateCoordinator().getIdleMillis(System.currentTimeMillis());
        return idleTime > IDLE_COMPATIBLE_TIMEOUT;
    }
}
