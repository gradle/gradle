/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.tooling.internal.provider;

import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.logging.services.LoggingServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.launcher.daemon.client.DaemonStartListener;
import org.gradle.launcher.daemon.client.DaemonStopClientExecuter;
import org.gradle.launcher.daemon.context.DaemonConnectDetails;
import org.gradle.launcher.daemon.registry.DaemonDir;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@ServiceScope(Scope.Global.class)
public class ShutdownCoordinator implements DaemonStartListener, Stoppable {
    private final Map<File, Set<DaemonConnectDetails>> daemons = new HashMap<>();
    private final DaemonStopClientExecuter client;

    public ShutdownCoordinator(DaemonStopClientExecuter client) {
        this.client = client;
    }

    @Override
    public void daemonStarted(DaemonDir daemonDir, DaemonConnectDetails daemon) {
        synchronized (daemons) {
            final Set<DaemonConnectDetails> startedDaemons;
            if (!daemons.containsKey(daemonDir.getBaseDir())) {
                startedDaemons = new HashSet<>();
                daemons.put(daemonDir.getBaseDir(), startedDaemons);
            } else {
                startedDaemons = daemons.get(daemonDir.getBaseDir());
            }
            startedDaemons.add(daemon);
        }
    }

    public void stopStartedDaemons(ServiceRegistry requestSpecificLoggingServices, File daemonBaseDir) {
        synchronized (daemons) {
            Set<DaemonConnectDetails> startedDaemons = daemons.get(daemonBaseDir);
            if (startedDaemons != null) {
                client.execute(requestSpecificLoggingServices, daemonBaseDir, daemonStopClient -> daemonStopClient.gracefulStop(startedDaemons));
            }
        }
    }

    @Override
    public void stop() {
        ServiceRegistry requestSpecificLoggingServices = LoggingServiceRegistry.newNestedLogging();
        synchronized (daemons) {
            for (File daemonBaseDir : daemons.keySet()) {
                stopStartedDaemons(requestSpecificLoggingServices, daemonBaseDir);
            }
        }
    }
}
