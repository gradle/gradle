/*
 * Copyright 2010 the original author or authors.
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
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.services.LoggingServiceRegistry;
import org.gradle.internal.nativeintegration.ProcessEnvironment;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.remote.Address;
import org.gradle.internal.serialize.kryo.KryoBackedDecoder;
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry;
import org.gradle.launcher.bootstrap.EntryPoint;
import org.gradle.launcher.bootstrap.ExecutionListener;
import org.gradle.launcher.daemon.configuration.DaemonServerConfiguration;
import org.gradle.launcher.daemon.configuration.DefaultDaemonServerConfiguration;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.logging.DaemonMessages;
import org.gradle.launcher.daemon.server.Daemon;
import org.gradle.launcher.daemon.server.DaemonServices;
import org.gradle.launcher.daemon.server.MasterExpirationStrategy;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStrategy;
import org.gradle.process.internal.shutdown.ShutdownHookActionRegister;
import org.gradle.process.internal.streams.EncodedStream;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The entry point for a daemon process.
 *
 * If the daemon hits the specified idle timeout the process will exit with 0. If the daemon encounters an internal error or is explicitly stopped (which can be via receiving a stop command, or
 * unexpected client disconnection) the process will exit with 1.
 */
public class DaemonMain extends EntryPoint {

    private static final Logger LOGGER = Logging.getLogger(DaemonMain.class);
    public static final String SINGLE_USE_FLAG = "--single-use";

    private PrintStream originalOut;
    private PrintStream originalErr;

    @Override
    protected void doAction(String[] args, ExecutionListener listener) {
        //The first argument is not really used but it is very useful in diagnosing, i.e. running 'jps -m'
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
        String daemonUid;
        List<File> additionalClassPath;

        KryoBackedDecoder decoder = new KryoBackedDecoder(new EncodedStream.EncodedInput(System.in));
        try {
            gradleHomeDir = new File(decoder.readString());
            daemonBaseDir = new File(decoder.readString());
            idleTimeoutMs = decoder.readSmallInt();
            periodicCheckIntervalMs = decoder.readSmallInt();
            singleUse = decoder.readBoolean();
            daemonUid = decoder.readString();
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

        NativeServices.initialize(gradleHomeDir);
        DaemonServerConfiguration parameters = new DefaultDaemonServerConfiguration(daemonUid, daemonBaseDir, idleTimeoutMs, periodicCheckIntervalMs, singleUse, startupOpts);
        LoggingServiceRegistry loggingRegistry = LoggingServiceRegistry.newCommandLineProcessLogging();
        LoggingManagerInternal loggingManager = loggingRegistry.newInstance(LoggingManagerInternal.class);

        DaemonServices daemonServices = new DaemonServices(parameters, loggingRegistry, loggingManager, new DefaultClassPath(additionalClassPath));
        File daemonLog = daemonServices.getDaemonLogFile();

        // Any logging prior to this point will not end up in the daemon log file.
        initialiseLogging(loggingManager, daemonLog);

        // Detach the process from the parent terminal/console
        ProcessEnvironment processEnvironment = daemonServices.get(ProcessEnvironment.class);
        processEnvironment.maybeDetachProcess();

        LOGGER.debug("Assuming the daemon was started with following jvm opts: {}", startupOpts);

        Daemon daemon = daemonServices.get(Daemon.class);
        daemon.start();

        try {
            DaemonContext daemonContext = daemonServices.get(DaemonContext.class);
            Long pid = daemonContext.getPid();
            daemonStarted(pid, daemon.getUid(), daemon.getAddress(), daemonLog);
            DaemonExpirationStrategy expirationStrategy = daemonServices.get(MasterExpirationStrategy.class);
            daemon.stopOnExpiration(expirationStrategy, parameters.getPeriodicCheckIntervalMs());
        } finally {
            daemon.stop();
            // TODO: Stop all daemon services
            CompositeStoppable.stoppable(daemonServices.get(GradleUserHomeScopeServiceRegistry.class)).stop();
        }
    }
    
    private static void invalidArgs(String message) {
        System.out.println("USAGE: <gradle version>");
        System.out.println(message);
        System.exit(1);
    }

    protected void daemonStarted(Long pid, String uid, Address address, File daemonLog) {
        //directly printing to the stream to avoid log level filtering.
        new DaemonStartupCommunication().printDaemonStarted(originalOut, pid, uid, address, daemonLog);
        try {
            originalOut.close();
            originalErr.close();

            //TODO - make this work on windows
            //originalIn.close();
        } finally {
            originalOut = null;
            originalErr = null;
        }
    }

    protected void initialiseLogging(LoggingManagerInternal loggingManager, File daemonLog) {
        //create log file
        PrintStream result;
        try {
            Files.createParentDirs(daemonLog);
            result = new PrintStream(new FileOutputStream(daemonLog), true);
        } catch (Exception e) {
            throw new RuntimeException("Unable to create daemon log file", e);
        }
        final PrintStream log = result;

        ShutdownHookActionRegister.addAction(new Runnable() {
            public void run() {
                //just in case we have a bug related to logging,
                //printing some exit info directly to file:
                log.println(DaemonMessages.DAEMON_VM_SHUTTING_DOWN);
            }
        });

        //close all streams and redirect IO
        redirectOutputsAndInput(log);

        //after redirecting we need to add the new std out/err to the renderer singleton
        //so that logging gets its way to the daemon log:
        loggingManager.attachSystemOutAndErr();

        //Making the daemon infrastructure log with DEBUG. This is only for the infrastructure!
        //Each build request carries it's own log level and it is used during the execution of the build (see LogToClient)
        loggingManager.setLevelInternal(LogLevel.DEBUG);

        loggingManager.start();
    }

    private void redirectOutputsAndInput(PrintStream printStream) {
        this.originalOut = System.out;
        this.originalErr = System.err;
        //InputStream originalIn = System.in;

        System.setOut(printStream);
        System.setErr(printStream);
        System.setIn(new ByteArrayInputStream(new byte[0]));
    }
}
