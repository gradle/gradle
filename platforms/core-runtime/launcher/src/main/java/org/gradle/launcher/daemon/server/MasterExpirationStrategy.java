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
import org.gradle.cache.internal.locklistener.FileLockContentionHandler;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.launcher.daemon.configuration.DaemonServerConfiguration;
import org.gradle.launcher.daemon.server.expiry.AllDaemonExpirationStrategy;
import org.gradle.launcher.daemon.server.expiry.AnyDaemonExpirationStrategy;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationResult;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStrategy;
import org.gradle.launcher.daemon.server.health.HealthExpirationStrategy;
import org.gradle.launcher.daemon.server.health.LowMemoryDaemonExpirationStrategy;

import java.util.concurrent.TimeUnit;

@ServiceScope(Scope.Global.class)
public class MasterExpirationStrategy implements DaemonExpirationStrategy {
    private static final int DUPLICATE_DAEMON_GRACE_PERIOD_MS = 10000;

    private final DaemonExpirationStrategy strategy;

    public MasterExpirationStrategy(Daemon daemon, DaemonServerConfiguration params, HealthExpirationStrategy healthExpirationStrategy, FileLockContentionHandler fileLockContentionHandler, ListenerManager listenerManager) {
        ImmutableList.Builder<DaemonExpirationStrategy> strategies = ImmutableList.<DaemonExpirationStrategy>builder();

        // Expire under high JVM memory or GC pressure
        strategies.add(healthExpirationStrategy);

        // If we can no longer communicate with the outside world, the daemon should expire
        strategies.add(new FileLockContentionExpirationStrategy(fileLockContentionHandler));

        // Expire compatible, idle, not recently used Daemons after a short time
        strategies.add(new AllDaemonExpirationStrategy(ImmutableList.of(
            new CompatibleDaemonExpirationStrategy(daemon),
            new DaemonIdleTimeoutExpirationStrategy(daemon, DUPLICATE_DAEMON_GRACE_PERIOD_MS, TimeUnit.MILLISECONDS),
            new NotMostRecentlyUsedDaemonExpirationStrategy(daemon)
        )));

        // Expire after normal idle timeout
        strategies.add(new DaemonIdleTimeoutExpirationStrategy(daemon, params.getIdleTimeout(), TimeUnit.MILLISECONDS));

        // Expire recently unused Daemons when memory pressure is high
        addLowMemoryDaemonExpirationStrategyWhenSupported(daemon, strategies, listenerManager);

        // Expire when Daemon Registry becomes unreachable for some reason
        strategies.add(new DaemonRegistryUnavailableExpirationStrategy(daemon));

        this.strategy = new AnyDaemonExpirationStrategy(strategies.build());
    }

    private void addLowMemoryDaemonExpirationStrategyWhenSupported(Daemon daemon, ImmutableList.Builder<DaemonExpirationStrategy> strategies, ListenerManager listenerManager) {
        final LowMemoryDaemonExpirationStrategy lowMemoryDaemonExpirationStrategy = new LowMemoryDaemonExpirationStrategy(0.05);
        listenerManager.addListener(lowMemoryDaemonExpirationStrategy);
        strategies.add(new AllDaemonExpirationStrategy(ImmutableList.of(
            new DaemonIdleTimeoutExpirationStrategy(daemon, DUPLICATE_DAEMON_GRACE_PERIOD_MS, TimeUnit.MILLISECONDS),
            new NotMostRecentlyUsedDaemonExpirationStrategy(daemon),
            lowMemoryDaemonExpirationStrategy
        )));
    }

    @Override
    public DaemonExpirationResult checkExpiration() {
        return strategy.checkExpiration();
    }
}
