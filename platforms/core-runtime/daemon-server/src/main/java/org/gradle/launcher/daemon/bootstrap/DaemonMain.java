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
import org.gradle.api.UncheckedIOException;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.instrumentation.agent.AgentInitializer;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.services.LoggingServiceRegistry;
import org.gradle.internal.nativeintegration.ProcessEnvironment;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.nativeintegration.services.NativeServices.NativeServicesMode;
import org.gradle.internal.remote.Address;
import org.gradle.internal.serialize.kryo.KryoBackedDecoder;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.stream.EncodedStream;
import org.gradle.launcher.bootstrap.EntryPoint;
import org.gradle.launcher.bootstrap.ExecutionListener;
import org.gradle.launcher.daemon.configuration.DaemonPriority;
import org.gradle.launcher.daemon.configuration.DaemonServerConfiguration;
import org.gradle.launcher.daemon.configuration.DefaultDaemonServerConfiguration;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.logging.DaemonMessages;
import org.gradle.launcher.daemon.server.Daemon;
import org.gradle.launcher.daemon.server.DaemonLogFile;
import org.gradle.launcher.daemon.server.DaemonProcessState;
import org.gradle.launcher.daemon.server.DaemonStopState;
import org.gradle.launcher.daemon.server.MasterExpirationStrategy;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStrategy;
import org.gradle.process.internal.shutdown.ShutdownHooks;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import static java.util.concurrent.TimeUnit.DAYS;

/**
 * The entry point for a daemon process.
 *
 * If the daemon hits the specified idle timeout the process will exit with 0. If the daemon encounters an internal error or is explicitly stopped (which can be via receiving a stop command, or
 * unexpected client disconnection) the process will exit with 1.
 */
public class DaemonMain extends EntryPoint {
    private static final Logger LOGGER = Logging.getLogger(DaemonMain.class);

    private PrintStream originalOut;
    private PrintStream originalErr;

    @Override
    protected void doAction(String[] args, ExecutionListener listener) {
        // The first argument is not really used but it is very useful in diagnosing, i.e. running 'jps -m'
        if (args.length != 1) {
            invalidArgs("Following arguments are required: <gradle-version>");
        }

        // Read configuration from stdin
        List<String> startupOpts;
        File gradleHomeDir;
        File daemonBaseDir;
        int idleTimeoutMs;
        int periodicCheckIntervalMs;
        boolean singleUse;
        NativeServicesMode nativeServicesMode;
        String daemonUid;
        DaemonPriority priority;
        LogLevel logLevel;
        List<File> additionalClassPath;

        KryoBackedDecoder decoder = new KryoBackedDecoder(new EncodedStream.EncodedInput(System.in));
        try {
            gradleHomeDir = new File(decoder.readString());
            daemonBaseDir = new File(decoder.readString());
            idleTimeoutMs = decoder.readSmallInt();
            periodicCheckIntervalMs = decoder.readSmallInt();
            singleUse = decoder.readBoolean();
            nativeServicesMode = NativeServicesMode.values()[decoder.readSmallInt()];
            daemonUid = decoder.readString();
            priority = DaemonPriority.values()[decoder.readSmallInt()];
            logLevel = LogLevel.values()[decoder.readSmallInt()];
            int argCount = decoder.readSmallInt();
            startupOpts = new ArrayList<String>(argCount);
            for (int i = 0; i < argCount; i++) {
                startupOpts.add(decoder.readString());
            }
            int additionalClassPathLength = decoder.readSmallInt();
            additionalClassPath = new ArrayList<File>(additionalClassPathLength);
            for (int i = 0; i < additionalClassPathLength; i++) {
                additionalClassPath.add(new File(decoder.readString()));
            }
        } catch (EOFException e) {
            throw new UncheckedIOException(e);
        }

        NativeServices.initializeOnDaemon(gradleHomeDir, NativeServicesMode.fromSystemProperties());
        DaemonServerConfiguration parameters = new DefaultDaemonServerConfiguration(daemonUid, daemonBaseDir, idleTimeoutMs, periodicCheckIntervalMs, singleUse, priority, startupOpts, nativeServicesMode);
        ServiceRegistry loggingRegistry = LoggingServiceRegistry.newCommandLineProcessLogging();
        LoggingManagerInternal loggingManager = loggingRegistry.newInstance(LoggingManagerInternal.class);

        DaemonProcessState daemonProcessState = new DaemonProcessState(parameters, loggingRegistry, loggingManager, DefaultClassPath.of(additionalClassPath));
        ServiceRegistry daemonServices = daemonProcessState.getServices();
        File daemonLog = daemonServices.get(DaemonLogFile.class).getFile();

        // Any logging prior to this point will not end up in the daemon log file.
        initialiseLogging(loggingManager, daemonLog, logLevel);
        cleanupOldLogFiles(daemonLog);

        // Detach the process from the parent terminal/console
        ProcessEnvironment processEnvironment = daemonServices.get(ProcessEnvironment.class);
        processEnvironment.maybeDetachProcess();

        LOGGER.debug("Assuming the daemon was started with following jvm opts: {}", startupOpts);

        daemonServices.get(AgentInitializer.class).maybeConfigureInstrumentationAgent();

        Daemon daemon = daemonServices.get(Daemon.class);
        daemon.start();

        try {
            DaemonContext daemonContext = daemonServices.get(DaemonContext.class);
            Long pid = daemonContext.getPid();
            daemonStarted(pid, daemon.getUid(), daemon.getAddress(), daemonLog);
            DaemonExpirationStrategy expirationStrategy = daemonServices.get(MasterExpirationStrategy.class);
            DaemonStopState stopState = daemon.stopOnExpiration(expirationStrategy, parameters.getPeriodicCheckIntervalMs());
            daemonProcessState.stopped(stopState);
        } finally {
            CompositeStoppable.stoppable(daemon, daemonProcessState).stop();
        }
    }

    private static void invalidArgs(String message) {
        System.out.println("USAGE: <gradle version>");
        System.out.println(message);
        System.exit(1);
    }

    protected void daemonStarted(Long pid, String uid, Address address, File daemonLog) {
        // directly printing to the stream to avoid log level filtering.
        new DaemonStartupCommunication().printDaemonStarted(originalOut, pid, uid, address, daemonLog);
        try {
            originalOut.close();
            originalErr.close();
        } finally {
            originalOut = null;
            originalErr = null;
        }
    }

    protected void initialiseLogging(LoggingManagerInternal loggingManager, File daemonLog, LogLevel logLevel) {
        // create log file
        PrintStream result;
        try {
            Files.createParentDirs(daemonLog);
            // Note that DaemonDiagnostics class reads this log.
            result = new PrintStream(new FileOutputStream(daemonLog), true, "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException("Unable to create daemon log file", e);
        }

        reducePermissionsOnDaemonLog(daemonLog);

        final PrintStream log = result;

        ShutdownHooks.addShutdownHook(new Runnable() {
            @Override
            public void run() {
                // just in case we have a bug related to logging,
                // printing some exit info directly to file:
                log.println(DaemonMessages.DAEMON_VM_SHUTTING_DOWN);
            }
        });

        // close all streams and redirect IO
        redirectOutputsAndInput(log);

        // after redirecting we need to add the new std out/err to the renderer singleton
        // so that logging gets its way to the daemon log:
        loggingManager.attachSystemOutAndErr();

        loggingManager.setLevelInternal(logLevel);

        loggingManager.start();
    }

    /**
     * Removes all log files in the given folder
     * older than ~2 weeks
     *
     * @param currentLogFile The currently used log file
     */
    private static void cleanupOldLogFiles(File currentLogFile) {
        String extension = ".log";
        File folder = currentLogFile.getParentFile();
        File[] logFiles = folder.listFiles(f -> f.isFile() && f.getName().endsWith(extension));
        if (logFiles == null) {
            LOGGER.warn("Could not list log files for cleanup");
            return;
        }
        long maxAge = System.currentTimeMillis() - DAYS.toMillis(14);
        for (File logFile : logFiles) {
            if (logFile.equals(currentLogFile) // Should never happen, but just to be safe
                || logFile.lastModified() >= maxAge) {
                continue;
            }

            if (!logFile.delete()) {
                LOGGER.warn("Could not delete old log file: {}", logFile.getAbsolutePath());
            }
        }
    }

    /**
     * Set the permissions for the daemon log to be only readable/writable by the current user.
     */
    private void reducePermissionsOnDaemonLog(File daemonLog) {
        //noinspection ResultOfMethodCallIgnored
        daemonLog.setReadable(false, false);
        //noinspection ResultOfMethodCallIgnored
        daemonLog.setReadable(true);
        //noinspection ResultOfMethodCallIgnored
        daemonLog.setExecutable(false);
    }

    private void redirectOutputsAndInput(PrintStream printStream) {
        this.originalOut = System.out;
        this.originalErr = System.err;

        System.setOut(printStream);
        System.setErr(printStream);
        System.setIn(new ByteArrayInputStream(new byte[0]));
    }
}
