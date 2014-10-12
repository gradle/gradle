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
import org.gradle.launcher.daemon.client.DaemonStartListener;
import org.gradle.launcher.daemon.diagnostics.DaemonStartupInfo;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class ShutdownCoordinator implements DaemonStartListener, Stoppable {
    private final Set<DaemonStartupInfo> daemons = new CopyOnWriteArraySet<DaemonStartupInfo>();

    public void daemonStarted(DaemonStartupInfo daemonInfo) {
        daemons.add(daemonInfo);
    }

    public void stop() {
        for (DaemonStartupInfo daemonInfo : daemons) {
            System.out.println("-> STOP DAEMON " + daemonInfo);
        }
    }
}
