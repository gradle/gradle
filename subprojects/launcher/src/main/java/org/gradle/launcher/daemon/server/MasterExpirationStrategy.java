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
import org.gradle.launcher.daemon.server.expiry.AllDaemonExpirationStrategy;
import org.gradle.launcher.daemon.server.expiry.AnyDaemonExpirationStrategy;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationResult;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStrategy;
import org.gradle.launcher.daemon.server.health.HealthExpirationStrategy;
import org.gradle.launcher.daemon.server.health.LowMemoryDaemonExpirationStrategy;

import java.util.concurrent.TimeUnit;

public class MasterExpirationStrategy implements DaemonExpirationStrategy {

    private static final Logger LOGGER = Logging.getLogger(MasterExpirationStrategy.class);

    private final DaemonExpirationStrategy strategy;

    public MasterExpirationStrategy(Daemon daemon, DaemonServerConfiguration params, HealthExpirationStrategy healthExpirationStrategy) {
        ImmutableList.Builder<DaemonExpirationStrategy> strategies = ImmutableList.<DaemonExpirationStrategy>builder();

        strategies.add(healthExpirationStrategy);
        strategies.add(new DaemonIdleTimeoutExpirationStrategy(daemon, params.getIdleTimeout(), TimeUnit.MILLISECONDS));

        addLowMemoryDaemonExpirationStrategyWhenSupported(daemon, params.getIdleTimeout(), strategies);

        strategies.add(new DaemonRegistryUnavailableExpirationStrategy(daemon));

        this.strategy = new AnyDaemonExpirationStrategy(strategies.build());
    }

    private void addLowMemoryDaemonExpirationStrategyWhenSupported(Daemon daemon, int idleTimeout, ImmutableList.Builder<DaemonExpirationStrategy> strategies) {
        try {
            final LowMemoryDaemonExpirationStrategy lowMemoryDaemonExpirationStrategy = LowMemoryDaemonExpirationStrategy.belowFreeBytes(1024 * 1024 * 1024);
            // this is to check that the JVM supports calling MemoryInfo.getFreePhysicalMemory
            lowMemoryDaemonExpirationStrategy.checkExpiration();
            strategies.add(new AllDaemonExpirationStrategy(ImmutableList.of(
                new DaemonIdleTimeoutExpirationStrategy(daemon, idleTimeout / 8, TimeUnit.MILLISECONDS),
                lowMemoryDaemonExpirationStrategy
            )));
        } catch (UnsupportedOperationException e) {
            LOGGER.info("This JVM does not support getting free system memory, so daemons will not check for it");
        }
    }

    @Override
    public DaemonExpirationResult checkExpiration() {
        return strategy.checkExpiration();
    }
}
