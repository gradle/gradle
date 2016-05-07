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
import com.google.common.collect.Lists;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.registry.DaemonDir;
import org.gradle.launcher.daemon.registry.DaemonInfo;

import java.io.File;
import java.util.List;

public class DaemonRegistryUnavailableExpirationStrategy implements DaemonExpirationStrategy {
    private static final Logger LOG = Logging.getLogger(DaemonRegistryUnavailableExpirationStrategy.class);

    @Override
    public DaemonExpirationResult checkExpiration(Daemon daemon) {
        try {
            final DaemonContext daemonContext = daemon.getDaemonContext();
            final File daemonRegistryDir = daemonContext.getDaemonRegistryDir();
            if (!new DaemonDir(daemonRegistryDir).getRegistry().canRead()) {
                LOG.warn("Daemon registry {} became unreadable. Expiring daemon.", daemonRegistryDir);
                return new DaemonExpirationResult(true, "daemon registry became unreadable");
            } else {
                // Check that given daemon still exists in registry - a daemon registry could be removed and recreated between checks
                List<Long> allDaemonPids = Lists.transform(daemon.getDaemonRegistry().getAll(), new Function<DaemonInfo, Long>() {
                    public Long apply(DaemonInfo info) {
                        return info.getPid();
                    }
                });
                if (!allDaemonPids.contains(daemonContext.getPid())) {
                    return new DaemonExpirationResult(true, "daemon registry entry unexpectedly lost");
                }
            }
        } catch (SecurityException se) {
            LOG.warn("Daemon registry became inaccessible. Expiring daemon. Error message is '{}'", se.getMessage());
            return new DaemonExpirationResult(true, "daemon registry became inaccessible");
        }
        return new DaemonExpirationResult(false, null);
    }
}

