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
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.launcher.daemon.client.DaemonParameters;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.logging.DaemonGreeter;
import org.gradle.launcher.daemon.registry.DaemonDir;
import org.gradle.launcher.daemon.server.Daemon;
import org.gradle.launcher.daemon.server.DaemonServices;
import org.gradle.launcher.daemon.server.DaemonStoppedException;
import org.gradle.launcher.exec.EntryPoint;
import org.gradle.launcher.exec.ExecutionListener;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.logging.LoggingServiceRegistry;
import org.gradle.logging.internal.OutputEventRenderer;

import java.io.*;
import java.util.UUID;

/**
 * The entry point for a daemon process.
 *
 * If the daemon hits the specified idle timeout the process will exit with 0. If the daemon encounters
 * an internal error or is explicitly stopped (which can be via receiving a stop command, or unexpected client disconnection)
 * the process will exit with 1.
 */
public class DaemonMain extends EntryPoint {

    private static final Logger LOGGER = Logging.getLogger(DaemonMain.class);

    final private File daemonBaseDir;
    final private boolean redirectIo;
    final private int idleTimeoutMs;

    public static void main(String[] args) {
        if (args.length != 3) {
            invalidArgs("3 arguments are required: <gradle-version> <daemon-dir> <timeout-millis>");
        }
        File daemonBaseDir = new File(args[1]);

        int idleTimeoutMs = 0;
        try {
            idleTimeoutMs = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            invalidArgs("Second argument must be a whole number (i.e. daemon idle timeout in ms)");
        }

        DaemonParameters parameters = new DaemonParameters();
        parameters.setBaseDir(daemonBaseDir);
        parameters.setIdleTimeout(idleTimeoutMs);

        new DaemonMain(parameters, true).run();
    }

    private static void invalidArgs(String message) {
        System.out.println("USAGE: <gradle version> <path to registry base dir> <idle timeout in milliseconds>");
        System.out.println(message);
        System.exit(1);
    }

    public DaemonMain(DaemonParameters parameters, boolean redirectIo) {
        this.daemonBaseDir = parameters.getBaseDir();
        this.idleTimeoutMs = parameters.getIdleTimeout();
        this.redirectIo = redirectIo;
    }

    protected void doAction(ExecutionListener listener) {
        LoggingServiceRegistry loggingRegistry = LoggingServiceRegistry.newChildProcessLogging();
        LoggingManagerInternal loggingManager = loggingRegistry.getFactory(LoggingManagerInternal.class).create();
        DaemonServices daemonServices = new DaemonServices(daemonBaseDir, idleTimeoutMs, loggingRegistry, loggingManager);
        DaemonDir daemonDir = daemonServices.get(DaemonDir.class);
        final DaemonContext daemonContext = daemonServices.get(DaemonContext.class);
        final Long pid = daemonContext.getPid();

        if (redirectIo) {
            //create log file
            PrintStream log = createLogOutput(daemonDir, pid);
            
            //close all streams and redirect IO
            redirectOutputsAndInput(log);
            
            //after redirecting we need to add the new std out/err to the renderer singleton
            //so that logging gets its way to the daemon log:
            loggingRegistry.get(OutputEventRenderer.class).addStandardOutputAndError();

            //Making the daemon infrastructure log with DEBUG. This is only for the infrastructure!
            //Each build request carries it's own log level and it is used during the execution of the build (see LogToClient)
            loggingManager.setLevel(LogLevel.DEBUG);
        }

        loggingManager.start();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                LOGGER.info("Daemon[pid = {}] finishing", pid);
            }
        });

        Daemon daemon = daemonServices.get(Daemon.class);
        daemon.start();
        try {
            daemon.awaitIdleTimeout(idleTimeoutMs);
            LOGGER.info("Daemon hit idle timeout (" + idleTimeoutMs + "ms), stopping");
            daemon.stop();
        } catch (DaemonStoppedException e) {
            LOGGER.info("Daemon stopping due to stop request");
            listener.onFailure(e);
        }
    }

    private static PrintStream createLogOutput(DaemonDir daemonDir, Long pid) {
        String logFileName = String.format("daemon-%s.out.log", pid == null ? UUID.randomUUID() : pid);
        File logFile = new File(daemonDir.getVersionedDir(), logFileName);
        try {
            Files.createParentDirs(logFile);
            return new PrintStream(new FileOutputStream(logFile), true);
        } catch (Exception e) {
            throw new RuntimeException("Unable to create daemon log file", e);
        }
    }

    private static void redirectOutputsAndInput(OutputStream log) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        //InputStream originalIn = System.in;

        PrintStream printStream = new PrintStream(log, true);

        System.setOut(printStream);
        System.setErr(printStream);
        System.setIn(new ByteArrayInputStream(new byte[0]));

        new DaemonGreeter().sendGreetingAndClose(originalOut);
        originalOut.close();
        originalErr.close();

        //TODO - make this work on windows
        //originalIn.close();
    }
}
