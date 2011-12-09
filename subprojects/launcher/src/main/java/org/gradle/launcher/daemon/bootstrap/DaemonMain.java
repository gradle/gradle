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

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.registry.DaemonDir;
import org.gradle.launcher.daemon.server.Daemon;
import org.gradle.launcher.daemon.server.DaemonParameters;
import org.gradle.launcher.daemon.server.DaemonServices;
import org.gradle.launcher.daemon.server.DaemonStoppedException;
import org.gradle.launcher.exec.EntryPoint;
import org.gradle.launcher.exec.ExecutionListener;
import org.gradle.logging.LoggingServiceRegistry;

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
        DaemonServices daemonServices = new DaemonServices(daemonBaseDir, idleTimeoutMs, LoggingServiceRegistry.newChildProcessLogging());
        DaemonDir daemonDir = daemonServices.get(DaemonDir.class);
        final DaemonContext daemonContext = daemonServices.get(DaemonContext.class);
        final Long pid = daemonContext.getPid();

        if (redirectIo) {
            try {
                redirectOutputsAndInput(daemonDir, pid);
            } catch (IOException e) {
                listener.onFailure(e);
                return;
            }
        }

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

    private static void redirectOutputsAndInput(DaemonDir daemonDir, Long pid) throws IOException {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
//        InputStream originalIn = System.in;

        //very simplistic, just making sure each damon has unique log file
        File logOutputFile = new File(daemonDir.getVersionedDir(), String.format("daemon-%s.out.log", pid == null ? UUID.randomUUID() : pid));
        logOutputFile.getParentFile().mkdirs();
        PrintStream printStream = new PrintStream(new FileOutputStream(logOutputFile), true);
        System.setOut(printStream);
        System.setErr(printStream);
        System.setIn(new ByteArrayInputStream(new byte[0]));
        originalOut.close();
        originalErr.close();
        // TODO - make this work on windows
//        originalIn.close();
    }
}
