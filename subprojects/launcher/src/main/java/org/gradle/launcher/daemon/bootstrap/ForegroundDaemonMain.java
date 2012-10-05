/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.launcher.daemon.bootstrap;

import org.gradle.launcher.daemon.configuration.DaemonServerConfiguration;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.launcher.daemon.server.Daemon;
import org.gradle.launcher.daemon.server.DaemonServices;
import org.gradle.logging.LoggingManagerInternal;

import java.io.File;

public class ForegroundDaemonMain extends DaemonMain {

    public ForegroundDaemonMain(DaemonServerConfiguration configuration) {
        super(configuration);
    }

    @Override
    protected void initialiseLogging(LoggingManagerInternal loggingManager, File daemonLog) {
        // Don't redirect IO for foreground daemon
        loggingManager.start();
    }

    @Override
    protected void daemonStarted(Long pid, File daemonLog) {
        //don't do anything
    }

    @Override
    protected Daemon startDaemon(DaemonServices daemonServices) {
        Daemon daemon = super.startDaemon(daemonServices);
        daemonServices.get(DaemonRegistry.class).markIdle(daemon.getAddress());
        return daemon;
    }
}
