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

import org.gradle.initialization.BuildLayoutParameters;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.daemon.client.DaemonClientFactory;
import org.gradle.launcher.daemon.client.DaemonStartListener;
import org.gradle.launcher.daemon.client.DaemonStopClient;
import org.gradle.launcher.daemon.configuration.DaemonParameters;
import org.gradle.launcher.daemon.context.DaemonAddress;
import org.gradle.launcher.daemon.diagnostics.DaemonStartupInfo;
import org.gradle.logging.internal.OutputEventRenderer;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class ShutdownCoordinator implements DaemonStartListener, Stoppable {
    private final Set<DaemonStartupInfo> daemons = new CopyOnWriteArraySet<DaemonStartupInfo>();
    private final DaemonClientFactory clientFactory;
    private final OutputEventRenderer outputEventRenderer;

    public ShutdownCoordinator(DaemonClientFactory clientFactory, OutputEventRenderer outputEventRenderer) {
        this.clientFactory = clientFactory;
        this.outputEventRenderer = outputEventRenderer;
    }

    public void daemonStarted(DaemonStartupInfo daemonInfo) {
        daemons.add(daemonInfo);
    }

    public void stop() {
        ServiceRegistry clientServices = clientFactory.createStopDaemonServices(outputEventRenderer, new DaemonParameters(new BuildLayoutParameters()));
        DaemonStopClient client = clientServices.get(DaemonStopClient.class);
        Set<DaemonAddress> addresses = new HashSet<DaemonAddress>();
        for (DaemonStartupInfo daemonInfo : daemons) {
            addresses.add(daemonInfo.getAddress());
        }
        client.gracefulStop(addresses);
    }
}
