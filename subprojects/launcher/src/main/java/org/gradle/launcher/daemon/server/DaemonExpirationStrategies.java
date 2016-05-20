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

import com.google.common.collect.ImmutableList;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.launcher.daemon.configuration.DaemonServerConfiguration;
import org.gradle.launcher.daemon.server.health.DaemonHealthServices;

import java.util.concurrent.TimeUnit;

public class DaemonExpirationStrategies {
    private static Logger logger = Logging.getLogger(DaemonExpirationStrategies.class);

    public static DaemonExpirationStrategy getDefaultStrategy(Daemon daemon, DaemonServices daemonServices, final DaemonServerConfiguration params) {
        ImmutableList.Builder<DaemonExpirationStrategy> strategies = ImmutableList.<DaemonExpirationStrategy>builder();
        strategies.add(getHealthStrategy(daemonServices));
        strategies.add(new DaemonIdleTimeoutExpirationStrategy(daemon, params.getIdleTimeout(), TimeUnit.MILLISECONDS));
        try {
            strategies.add(new AllDaemonExpirationStrategy(ImmutableList.of(
                new DaemonIdleTimeoutExpirationStrategy(daemon, params.getIdleTimeout() / 8, TimeUnit.MILLISECONDS),
                LowMemoryDaemonExpirationStrategy.belowFreePercentage(0.2)
            )));
        } catch (UnsupportedOperationException e) {
            logger.info("This JVM does not support getting free system memory, so daemons will not check for it");
        }

        strategies.add(new DaemonRegistryUnavailableExpirationStrategy(daemon));
        return new AnyDaemonExpirationStrategy(strategies.build());
    }

    public static DaemonExpirationStrategy getHealthStrategy(DaemonServices daemonServices) {
        return new AnyDaemonExpirationStrategy(ImmutableList.of(
            new GcThrashingDaemonExpirationStrategy(daemonServices.get(DaemonHealthServices.class)),
            new LowTenuredSpaceDaemonExpirationStrategy(daemonServices.get(DaemonHealthServices.class)),
            new LowPermGenDaemonExpirationStrategy(daemonServices.get(DaemonHealthServices.class))
        ));
    }
}
