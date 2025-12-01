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
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.instrumentation.agent.AgentInitializer;
import org.gradle.internal.logging.LoggingManagerFactory;
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
import org.gradle.launcher.daemon.registry.DaemonDir;
import org.gradle.launcher.daemon.server.Daemon;
import org.gradle.launcher.daemon.server.DaemonLogFile;
import org.gradle.launcher.daemon.server.DaemonProcessState;
import org.gradle.launcher.daemon.server.DaemonStopState;
import org.gradle.launcher.daemon.server.MasterExpirationStrategy;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStrategy;
import org.gradle.process.internal.shutdown.ShutdownHooks;
import org.gradle.util.internal.DefaultGradleVersion;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.newOutputStream;
import static org.gradle.launcher.daemon.server.DaemonLogFile.DAEMON_LOG_PREFIX;
import static org.gradle.launcher.daemon.server.DaemonLogFile.DAEMON_LOG_SUFFIX;

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
        // The first argument is not really used, but it is very useful in diagnosing, i.e. running 'jps -m'
        if (args.length != 1) {
            invalidArgs();
        }

        // Read configuration from stdin
        File gradleHomeDir;
        List<File> additionalClassPath;
        DaemonServerConfiguration parameters;

        try {
            KryoBackedDecoder decoder = new KryoBackedDecoder(new EncodedStream.EncodedInput(System.in));
            gradleHomeDir = new File(decoder.readString());
            parameters = readDaemonServerConfiguration(decoder);
            additionalClassPath = readAdditionalClassPath(decoder);
        } catch (EOFException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }

        NativeServices.initializeOnDaemon(gradleHomeDir, NativeServicesMode.fromSystemProperties());
        ServiceRegistry loggingRegistry = LoggingServiceRegistry.newCommandLineProcessLogging();
        LoggingManagerInternal loggingManager = loggingRegistry.get(LoggingManagerFactory.class).createLoggingManager();

        DaemonProcessState daemonProcessState = new DaemonProcessState(parameters, loggingRegistry, loggingManager, DefaultClassPath.of(additionalClassPath));
        ServiceRegistry daemonServices = daemonProcessState.getServices();
        File daemonLog = daemonServices.get(DaemonLogFile.class).getFile();
        File daemonBaseDir = daemonServices.get(DaemonDir.class).getBaseDir();

        // Any logging prior to this point will not end up in the daemon log file.
        initialiseLogging(loggingManager, daemonLog);

        // Detach the process from the parent terminal/console
        ProcessEnvironment processEnvironment = daemonServices.get(ProcessEnvironment.class);
        processEnvironment.maybeDetachProcess();

        LOGGER.debug("Assuming the daemon was started with following jvm opts: {}", parameters.getJvmOptions());

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
            //TODO This should actually be used in `GradleUserHomeCleanupService`, but this is in core and core can't use the classes to get the proper daemon log dir name.
            cleanupOldLogFiles(daemonBaseDir);
        }
    }

    private static List<File> readAdditionalClassPath(KryoBackedDecoder decoder) throws EOFException {
        int additionalClassPathLength = decoder.readSmallInt();
        List<File> additionalClassPath = new ArrayList<>(additionalClassPathLength);
        for (int i = 0; i < additionalClassPathLength; i++) {
            additionalClassPath.add(new File(decoder.readString()));
        }
        return additionalClassPath;
    }

    private static DaemonServerConfiguration readDaemonServerConfiguration(KryoBackedDecoder decoder) throws EOFException {
        File daemonBaseDir = new File(decoder.readString());
        int idleTimeoutMs = decoder.readSmallInt();
        int periodicCheckIntervalMs = decoder.readSmallInt();
        boolean singleUse = decoder.readBoolean();
        NativeServicesMode nativeServicesMode = NativeServicesMode.values()[decoder.readSmallInt()];
        String daemonUid = decoder.readString();
        DaemonPriority priority = DaemonPriority.values()[decoder.readSmallInt()];
        int argCount = decoder.readSmallInt();
        List<String> startupJvmOpts = new ArrayList<>(argCount);
        for (int i = 0; i < argCount; i++) {
            startupJvmOpts.add(decoder.readString());
        }
        return new DefaultDaemonServerConfiguration(daemonUid, daemonBaseDir, idleTimeoutMs, periodicCheckIntervalMs, singleUse, priority, startupJvmOpts, nativeServicesMode);
    }

    private static void invalidArgs() {
        System.out.println("USAGE: <gradle version>");
        System.out.println("Following arguments are required: <gradle-version>");
        System.exit(1);
    }

    private static final long FOURTEEN_DAYS_MILLIS = TimeUnit.DAYS.toMillis(14);

    /**
     * Removes all log files in the given folder that haven't been modified for at least 2 weeks
     *
     * @param daemonBaseDir The currently used log file
     */
    public static void cleanupOldLogFiles(File daemonBaseDir) {
        try {
            File[] daemonLogDirectories = daemonBaseDir.listFiles(file ->
                file.isDirectory() && DefaultGradleVersion.VERSION_PATTERN.matcher(file.getName()).matches());
            if (daemonLogDirectories == null) {
                LOGGER.warn("Could not list daemon log directories for cleanup in: {}", daemonBaseDir.getAbsolutePath());
                return;
            }
            long maxAge = System.currentTimeMillis() - FOURTEEN_DAYS_MILLIS;
            for (File daemonLogDirectory : daemonLogDirectories) {
                File[] logFiles = daemonLogDirectory.listFiles(f -> f.isFile() && f.getName().endsWith(DAEMON_LOG_SUFFIX) && f.getName().startsWith(DAEMON_LOG_PREFIX));
                if (logFiles == null) {
                    LOGGER.warn("Could not list log files for cleanup in: {}", daemonLogDirectory.getAbsolutePath());
                    return;
                }
                for (File logFile : logFiles) {
                    if (logFile.equals(daemonBaseDir) // Should never happen, but just to be safe
                        || logFile.lastModified() >= maxAge) {
                        continue;
                    }

                    if (!logFile.delete()) {
                        LOGGER.warn("Could not delete old log file: {}", logFile.getAbsolutePath());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error cleaning up old log files", e);
        }

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

    protected void initialiseLogging(LoggingManagerInternal loggingManager, File daemonLog) {
        PrintStream log = createLogFile(daemonLog);
        reducePermissionsOnDaemonLog(daemonLog);

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

        // Making the daemon infrastructure log with DEBUG. This is only for the infrastructure!
        // Each build request carries it's own log level and it is used during the execution of the build (see LogToClient)
        loggingManager.setLevelInternal(LogLevel.DEBUG);

        loggingManager.start();
    }

    private static PrintStream createLogFile(File daemonLog) {
        try {
            Files.createParentDirs(daemonLog);
            // Note that DaemonDiagnostics class reads this log.
            return new PrintStream(newOutputStream(daemonLog.toPath()), true, UTF_8.toString());
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

    private void redirectOutputsAndInput(PrintStream printStream) {
        this.originalOut = System.out;
        this.originalErr = System.err;

        System.setOut(printStream);
        System.setErr(printStream);
        System.setIn(new ByteArrayInputStream(new byte[0]));
    }
}
