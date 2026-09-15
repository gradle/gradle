/*
 * Copyright 2024 the original author or authors.
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

import com.google.common.io.Files;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.services.LoggingServiceRegistry;
import org.gradle.internal.nativeintegration.ProcessEnvironment;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.nativeintegration.services.NativeServices.NativeServicesMode;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.bootstrap.EntryPoint;
import org.gradle.launcher.bootstrap.ExecutionListener;
import org.gradle.launcher.daemon.logging.DaemonMessages;
import org.gradle.launcher.daemon.startup.DaemonServerConfiguration;
import org.gradle.launcher.daemon.startup.DaemonStartupCommunication;
import org.gradle.process.internal.shutdown.ShutdownHooks;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static java.nio.file.Files.newOutputStream;

/**
 * The entry point for a daemon process.
 *
 * If the daemon hits the specified idle timeout the process will exit with 0. If the daemon encounters an internal error or is explicitly stopped (which can be via receiving a stop command, or
 * unexpected client disconnection) the process will exit with 1.
 */
public class DaemonMain extends EntryPoint {

    @Override
    protected void doAction(String[] args, ExecutionListener listener) {
        // The first argument is not really used, but it is very useful in diagnosing, i.e. running 'jps -m'
        if (args.length != 1) {
            invalidArgs();
        }

        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;

        // Read configuration from stdin
        DaemonServerConfiguration parameters = DaemonStartupCommunication.readDaemonServerConfiguration(System.in);
        NativeServices.initializeOnDaemon(parameters.getGradleUserHomeDir(), NativeServicesMode.fromSystemProperties());
        ServiceRegistry loggingRegistry = LoggingServiceRegistry.newCommandLineProcessLogging();

        // A forked daemon relies on the process exiting to clean up after a forced stop
        boolean closeServicesOnForcedStop = false;

        DaemonServerBootstrap.run(
            parameters,
            originalOut,
            originalErr,
            loggingRegistry,
            closeServicesOnForcedStop,
            new ForkedDaemonServerEnvironment()
        );
    }

    private static void invalidArgs() {
        System.out.println("USAGE: <gradle version>");
        System.out.println("Following arguments are required: <gradle-version>");
        System.exit(1);
    }

    /**
     * A forked daemon owns its whole process. It creates the daemon log file, redirects the
     * process' system streams to it, attaches the logging manager to the redirected streams,
     * and detaches the process from the parent terminal.
     */
    private static class ForkedDaemonServerEnvironment implements DaemonServerEnvironment {

        @Override
        public void beforeStart(LoggingManagerInternal loggingManager, File daemonLog, ServiceRegistry daemonServices) {
            PrintStream log = createLogFile(daemonLog);
            reducePermissionsOnDaemonLog(daemonLog);

            ShutdownHooks.addShutdownHook(() -> {
                // Just in case we have a bug related to logging,
                // printing some exit info directly to file.
                log.println(DaemonMessages.DAEMON_VM_SHUTTING_DOWN);
            });

            // Close all streams and redirect IO.
            redirectOutputsAndInput(log);

            // After redirecting we need to add the new std out/err to the renderer singleton
            // so that logging gets its way to the daemon log.
            loggingManager.attachSystemOutAndErr();

            // Detach the process from the parent terminal/console.
            ProcessEnvironment processEnvironment = daemonServices.get(ProcessEnvironment.class);
            processEnvironment.maybeDetachProcess();
        }

        private static PrintStream createLogFile(File daemonLog) {
            try {
                Files.createParentDirs(daemonLog);
                // Note that DaemonDiagnostics class reads this log.
                return new PrintStream(newOutputStream(daemonLog.toPath()), true, StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new RuntimeException("Unable to create daemon log file", e);
            }
        }

        /**
         * Set the permissions for the daemon log to be only readable/writable by the current user.
         */
        private static void reducePermissionsOnDaemonLog(File daemonLog) {
            //noinspection ResultOfMethodCallIgnored
            daemonLog.setReadable(false, false);
            //noinspection ResultOfMethodCallIgnored
            daemonLog.setReadable(true);
            //noinspection ResultOfMethodCallIgnored
            daemonLog.setExecutable(false);
        }

        private static void redirectOutputsAndInput(PrintStream printStream) {
            System.setOut(printStream);
            System.setErr(printStream);
            System.setIn(new ByteArrayInputStream(new byte[0]));
        }

    }

}
