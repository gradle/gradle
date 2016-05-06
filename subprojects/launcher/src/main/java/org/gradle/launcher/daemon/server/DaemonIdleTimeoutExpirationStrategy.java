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

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.util.concurrent.TimeUnit;

public class DaemonIdleTimeoutExpirationStrategy implements DaemonExpirationStrategy {
    private static final Logger LOG = Logging.getLogger(DaemonIdleTimeoutExpirationStrategy.class);
    private Function<?, Long> idleTimeout;

    public DaemonIdleTimeoutExpirationStrategy(int idleTimeout, TimeUnit timeUnit) {
        this(Functions.constant(timeUnit.toMillis(idleTimeout)));
    }

    public DaemonIdleTimeoutExpirationStrategy(Function<?, Long> timeoutClosure) {
        this.idleTimeout = Preconditions.checkNotNull(timeoutClosure);
    }

    @Override
    public DaemonExpirationResult checkExpiration(Daemon daemon) {
        long idleMillis = daemon.getStateCoordinator().getIdleMillis(System.currentTimeMillis());
        boolean idleTimeoutExceeded = idleMillis > idleTimeout.apply(null);
        if (idleTimeoutExceeded) {
            LOG.info("Idle timeout: daemon has been idle for {} milliseconds. Expiring.", idleMillis);
            return new DaemonExpirationResult(true, "daemon has been idle for " + idleMillis + " milliseconds");
        }
        return new DaemonExpirationResult(false, null);
    }
}
