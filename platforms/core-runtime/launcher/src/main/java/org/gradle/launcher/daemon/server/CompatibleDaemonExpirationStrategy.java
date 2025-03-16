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
import org.gradle.launcher.daemon.context.DaemonCompatibilitySpec;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.registry.DaemonInfo;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationResult;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStrategy;
import org.gradle.util.internal.CollectionUtils;

import java.util.Collection;

public class CompatibleDaemonExpirationStrategy implements DaemonExpirationStrategy {
    private final Daemon daemon;
    private final ExplainingSpec<DaemonContext> compatibilitySpec;

    public static final String EXPIRATION_REASON = "other compatible daemons were started";

    CompatibleDaemonExpirationStrategy(Daemon daemon, ExplainingSpec<DaemonContext> compatibilitySpec) {
        this.daemon = daemon;
        this.compatibilitySpec = compatibilitySpec;
    }

    CompatibleDaemonExpirationStrategy(Daemon daemon) {
        this(daemon, new DaemonCompatibilitySpec(daemon.getDaemonContext().toRequest()));
    }

    @Override
    public DaemonExpirationResult checkExpiration() {
        Collection<DaemonInfo> compatibleIdleDaemons = CollectionUtils.filter(daemon.getDaemonRegistry().getIdle(),
            daemonInfo -> compatibilitySpec.isSatisfiedBy(daemonInfo.getContext()));

        if (compatibleIdleDaemons.size() > 1) {
            return new DaemonExpirationResult(DaemonExpirationStatus.GRACEFUL_EXPIRE, EXPIRATION_REASON);
        } else {
            return DaemonExpirationResult.NOT_TRIGGERED;
        }
    }
}
