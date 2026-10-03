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

import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.services.LoggingServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.daemon.startup.DaemonServerConfiguration;
import org.gradle.launcher.daemon.startup.DaemonStartupCommunication;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

/**
 * The entry point for a daemon embedded in the current process.
 * <p>
 * This class is loaded reflectively by the client from the daemon server classpath. The client
 * has no compile-time dependency on it, and instead loads it reflectively via its well-known
 * classname and invokes it via its well-known method signature. The constructor and public
 * {@link #run(InputStream, OutputStream, OutputStream)} method should only accept public
 * JDK stdlib types.
 */
@SuppressWarnings("unused") // Loaded reflectively by the client
public class EmbeddedDaemonMain {

    /**
     * Runs an embedded daemon until it expires. Blocks the calling thread for the lifetime of
     * the daemon.
     * <p>
     * If startup fails before the handshake is written, the failure is rethrown and
     * {@code stdout} is closed so that a client blocked reading the handshake unblocks.
     */
    public void run(InputStream stdin, OutputStream stdout, OutputStream stderr) {
        PrintStream startupOut = new PrintStream(stdout, true);
        PrintStream startupErr = new PrintStream(stderr, true);
        try {
            DaemonServerConfiguration parameters = DaemonStartupCommunication.readDaemonServerConfiguration(stdin);

            // Native services are not initialized here. They are a per-process singleton that the
            // hosting client has already initialized, and which the daemon sees through the shared
            // parent classloader.

            ServiceRegistry loggingRegistry = LoggingServiceRegistry.newEmbeddableLogging();

            // There is not necessarily a process exit to rely on for cleanup after a forced stop.
            // Embedded TAPI clients may execute multiple embedded daemons sequentially in the same
            // process.
            boolean closeServicesOnForcedStop = true;

            DaemonServerBootstrap.run(
                parameters,
                startupOut,
                startupErr,
                loggingRegistry,
                closeServicesOnForcedStop,
                new EmbeddedDaemonServerEnvironment()
            );
        } finally {
            // Unblock a client waiting for the handshake if startup failed before it was written
            startupOut.close();
            startupErr.close();
        }
    }

    /**
     * An embedded daemon is a guest in the client's process. It attaches no outputs and creates
     * no log file, since all of its output leaves the daemon over its connections. It captures
     * the process' system sources while it runs, restoring them when the daemon stops.
     */
    private static class EmbeddedDaemonServerEnvironment implements DaemonServerEnvironment {

        @Override
        public void beforeStart(LoggingManagerInternal loggingManager, File daemonLog, ServiceRegistry daemonServices) {
            // Capture System.out and System.err so they are forwarded to the client as logging events.
            // The previous streams are restored when the logging manager stops.
            loggingManager.captureSystemSources();

            // Nothing is attached to the logging manager, and the daemon log file is deliberately
            // not created. Build output reaches build clients over the connection (LogToClient).
            // In future iterations we may choose to log to a file, but historically embedded
            // daemons do not write to a log file.

            // The process is not detached from the terminal since the terminal belongs to the client.
        }

    }

}
