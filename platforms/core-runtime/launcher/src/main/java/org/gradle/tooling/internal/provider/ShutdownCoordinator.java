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
import org.gradle.launcher.cli.converter.BuildLayoutConverter;
import org.gradle.launcher.daemon.client.DaemonStartListener;
import org.gradle.launcher.daemon.client.DaemonStopClientExecuter;
import org.gradle.launcher.daemon.configuration.DaemonParameters;
import org.gradle.launcher.daemon.context.DaemonConnectDetails;
import org.gradle.launcher.daemon.registry.DaemonDir;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Keeps track of all started daemons and stops them when the service is stopped.
 *
 * Additionally, daemons for a particular daemon registry can be stopped explicitly.
 *
 * This is only used by Tooling API clients.
 *
 * @see ProviderConnection
 * @see org.gradle.tooling.internal.consumer.ConnectorServices
 */
@ServiceScope(Scope.Global.class)
public class ShutdownCoordinator implements DaemonStartListener, Stoppable {
    private final Map<File, Set<DaemonConnectDetails>> daemons = new HashMap<>();
    private final DaemonStopClientExecuter client;
    private final File incorrectDaemonRegistryPath;

    public ShutdownCoordinator(DaemonStopClientExecuter client) {
        this.client = client;
        this.incorrectDaemonRegistryPath = new DaemonParameters(new BuildLayoutConverter().defaultValues().getGradleUserHomeDir(), null).getBaseDir();
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
            if (startedDaemons != null && !startedDaemons.isEmpty()) {
                try {
                    client.execute(requestSpecificLoggingServices, daemonBaseDir, daemonStopClient -> daemonStopClient.gracefulStop(startedDaemons));
                } finally {
                    startedDaemons.clear();
                }
            }
        }
    }

    @Override
    public void stop() {
        ServiceRegistry requestSpecificLoggingServices = LoggingServiceRegistry.newNestedLogging();
        synchronized (daemons) {
            // TODO: This should go away, but exists for backwards compatibility.
            // The path used for the services to stop daemons when stopping all daemons has always been
            // a default path that may not represent the actual daemon registry path.
            //
            // We should instead create services for each known daemon registry or make it so a different shutdown
            // is used for each daemon registry.
            // 
            // This has complications in TestKit because we shutdown all running daemons in a shutdown hook
            // Our integration testing infrastructure does not expect any tests to write to test file directories
            // when the test process stops. This is treated as an error.
            for (Set<DaemonConnectDetails> startedDaemons : daemons.values()) {
                try {
                    client.execute(requestSpecificLoggingServices, incorrectDaemonRegistryPath, daemonStopClient -> daemonStopClient.gracefulStop(startedDaemons));
                } finally {
                    startedDaemons.clear();
                }
            }
        }
    }
}
