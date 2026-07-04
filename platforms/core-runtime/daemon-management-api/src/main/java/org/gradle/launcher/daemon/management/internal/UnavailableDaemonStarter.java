/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.launcher.daemon.management.internal;

import org.gradle.launcher.daemon.client.DaemonStarter;
import org.gradle.launcher.daemon.diagnostics.DaemonStartupInfo;

/**
 * A {@link DaemonStarter} that never starts a daemon. The management client only ever talks to already-running
 * daemons, so the connector's start path is unreachable here.
 */
class UnavailableDaemonStarter implements DaemonStarter {
    @Override
    public DaemonStartupInfo startDaemon(boolean singleRun) {
        throw new UnsupportedOperationException("Daemons cannot be started by the daemon management client.");
    }
}
