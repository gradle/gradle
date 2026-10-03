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
package org.gradle.launcher.daemon.bootstrap;

import org.gradle.api.logging.LogLevel;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.instrumentation.agent.AgentInitializer;
import org.gradle.internal.logging.LoggingManagerFactory;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.diagnostics.DaemonDiagnostics;
import org.gradle.launcher.daemon.server.Daemon;
import org.gradle.launcher.daemon.server.DaemonLogFile;
import org.gradle.launcher.daemon.server.DaemonProcessState;
import org.gradle.launcher.daemon.server.DaemonStopState;
import org.gradle.launcher.daemon.server.MasterExpirationStrategy;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStrategy;
import org.gradle.launcher.daemon.startup.DaemonServerConfiguration;
import org.gradle.launcher.daemon.startup.DaemonStartupCommunication;
import org.gradle.launcher.daemon.startup.DaemonStartupInfo;

import java.io.File;
import java.io.PrintStream;

/**
 * Responsible for starting a daemon server. Reused for both forked and embedded daemons.
 */
public final class DaemonServerBootstrap {

    private DaemonServerBootstrap() {
    }

    /**
     * Runs the daemon until it expires. Builds the daemon process state, starts the server,
     * performs the server-side startup handshake, then blocks until the daemon expires.
     *
     * @param configuration The daemon server configuration. Read from stdin during startup communication.
     * @param startupOut The stream to write the startup handshake to. Closed once the handshake is complete.
     * @param startupErr The stream where diagnostics may be written. Closed once the handshake is complete.
     * @param loggingServices The logging services to run the daemon with.
     * @param closeServicesOnForcedStop Whether to stop the daemon services even after a forced stop.
     * A forked daemon skips this and relies on the imminent process exit for cleanup.
     * @param environment The environment-specific bootstrap logic.
     */
    public static void run(
        DaemonServerConfiguration configuration,
        PrintStream startupOut,
        PrintStream startupErr,
        ServiceRegistry loggingServices,
        boolean closeServicesOnForcedStop,
        DaemonServerEnvironment environment
    ) {
        LoggingManagerInternal loggingManager = loggingServices.get(LoggingManagerFactory.class).createLoggingManager();

        DaemonProcessState daemonProcessState = new DaemonProcessState(configuration, loggingServices, loggingManager);
        ServiceRegistry daemonServices = daemonProcessState.getServices();
        File daemonLog = daemonServices.get(DaemonLogFile.class).getFile();

        environment.beforeStart(loggingManager, daemonLog, daemonServices);

        // Making the daemon infrastructure log with DEBUG. This is only for the infrastructure!
        // Each build request carries it's own log level and it is used during the execution of the build (see LogToClient)
        loggingManager.setLevelInternal(LogLevel.DEBUG);

        // Any logging prior to this point will not end up in the daemon log.
        loggingManager.start();

        Daemon daemon = null;
        DaemonStopState stopState = null;
        try {
            daemonServices.get(AgentInitializer.class).maybeConfigureInstrumentationAgent();

            daemon = daemonServices.get(Daemon.class);
            daemon.start();

            Long pid = daemonServices.get(DaemonContext.class).getPid();
            DaemonStartupCommunication.writeDaemonStartupInfo(
                startupOut,
                new DaemonStartupInfo(
                    daemon.getUid(),
                    daemon.getAddress(),
                    new DaemonDiagnostics(daemonLog, pid)
                )
            );

            startupOut.close();
            startupErr.close();

            DaemonExpirationStrategy expirationStrategy = daemonServices.get(MasterExpirationStrategy.class);
            stopState = daemon.stopOnExpiration(expirationStrategy, configuration.getPeriodicCheckIntervalMs());
        } finally {
            if (stopState == DaemonStopState.Forced && !closeServicesOnForcedStop) {
                // The daemon could not be stopped cleanly, so its services could still be doing
                // work. Do not attempt to stop the services and rely on the imminent process exit
                // for cleanup.
                CompositeStoppable.stoppable(daemon).stop();
            } else {
                // The logging manager is stopped after the daemon services so logging during shutdown
                // is still captured. Stopping the logging manager restores the static global
                // logging state borrowed while the daemon was running, such as the slf4j
                // binding's listener and the captured System.out/err of an embedded daemon.
                CompositeStoppable.stoppable(daemon, daemonProcessState, loggingManager).stop();
            }
        }
    }

}
