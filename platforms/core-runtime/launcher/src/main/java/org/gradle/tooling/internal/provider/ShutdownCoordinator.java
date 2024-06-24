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
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.launcher.daemon.client.DaemonStartListener;
import org.gradle.launcher.daemon.client.DaemonStopClient;
import org.gradle.launcher.daemon.context.DaemonConnectDetails;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@ServiceScope(Scope.Global.class)
public class ShutdownCoordinator implements DaemonStartListener, Stoppable {
    private final Set<DaemonConnectDetails> daemons = new CopyOnWriteArraySet<DaemonConnectDetails>();
    private final DaemonStopClient client;

    public ShutdownCoordinator(DaemonStopClient client) {
        this.client = client;
    }

    @Override
    public void daemonStarted(DaemonConnectDetails daemon) {
        daemons.add(daemon);
    }

    @Override
    public void stop() {
        client.gracefulStop(daemons);
    }
}
