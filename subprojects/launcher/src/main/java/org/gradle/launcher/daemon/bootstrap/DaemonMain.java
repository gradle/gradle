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
import org.gradle.launcher.bootstrap.EntryPoint;
import org.gradle.launcher.bootstrap.ExecutionListener;
import org.gradle.launcher.daemon.configuration.DaemonServerConfiguration;
import org.gradle.launcher.daemon.configuration.DefaultDaemonServerConfiguration;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.logging.DaemonMessages;
import org.gradle.launcher.daemon.server.Daemon;
import org.gradle.launcher.daemon.server.DaemonServices;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.logging.LoggingServiceRegistry;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The entry point for a daemon process.
 *
 * If the daemon hits the specified idle timeout the process will exit with 0. If the daemon encounters
 * an internal error or is explicitly stopped (which can be via receiving a stop command, or unexpected client disconnection)
 * the process will exit with 1.
 */
public class DaemonMain extends EntryPoint {

    private static final Logger LOGGER = Logging.getLogger(DaemonMain.class);

    private final DaemonServerConfiguration configuration;
    private PrintStream originalOut;
    private PrintStream originalErr;

    public static void main(String[] args) {
        //The first argument is not really used but it is very useful in diagnosing, i.e. running 'jps -m'
        if (args.length < 4) {
            invalidArgs("Following arguments are required: <gradle-version> <daemon-dir> <timeout-millis> <daemonUid> <optional startup jvm opts>");
        }
        File daemonBaseDir = new File(args[1]);

        int idleTimeoutMs = 0;
        try {
            idleTimeoutMs = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            invalidArgs("Second argument must be a whole number (i.e. daemon idle timeout in ms)");
        }

        String daemonUid = args[3];

        List<String> startupOpts = new LinkedList<String>();
        for (int i = 4; i < args.length; i++) {
            startupOpts.add(args[i]);
        }
        LOGGER.debug("Assuming the daemon was started with following jvm opts: {}", startupOpts);

        DaemonServerConfiguration parameters = new DefaultDaemonServerConfiguration(
                daemonUid, daemonBaseDir, idleTimeoutMs, startupOpts);
        DaemonMain daemonMain = new DaemonMain(parameters);

        daemonMain.run();
    }

    private static void invalidArgs(String message) {
        System.out.println("USAGE: <gradle version> <path to registry base dir> <idle timeout in milliseconds>");
        System.out.println(message);
        System.exit(1);
    }

    public DaemonMain(DaemonServerConfiguration configuration) {
        this.configuration = configuration;
    }

    protected void doAction(ExecutionListener listener) {
        LoggingServiceRegistry loggingRegistry = LoggingServiceRegistry.newProcessLogging();
        LoggingManagerInternal loggingManager = loggingRegistry.newInstance(LoggingManagerInternal.class);
        DaemonServices daemonServices = new DaemonServices(configuration, loggingRegistry, loggingManager);
        File daemonLog = daemonServices.getDaemonLogFile();
        final DaemonContext daemonContext = daemonServices.get(DaemonContext.class);

        initialiseLogging(loggingManager, daemonLog);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                LOGGER.info("Daemon[pid = {}] process has finished.", daemonContext.getPid());
            }
        });

        Daemon daemon = startDaemon(daemonServices);

        Long pid = daemonContext.getPid();
        LOGGER.lifecycle(DaemonMessages.PROCESS_STARTED + ((pid == null)? "":" Pid: " + pid + "."));
        daemonStarted(pid, daemonLog);

        try {
            daemon.requestStopOnIdleTimeout(configuration.getIdleTimeout(), TimeUnit.MILLISECONDS);
            LOGGER.info("Daemon hit idle timeout (" + configuration.getIdleTimeout() + "ms), stopping...");
        } finally {
            daemon.stop();
        }
    }

    protected void daemonStarted(Long pid, File daemonLog) {
        //directly printing to the stream to avoid log level filtering.
        new DaemonStartupCommunication().printDaemonStarted(originalOut, pid, daemonLog);
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

        Runtime.getRuntime().addShutdownHook(new Thread() {
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
        loggingManager.addStandardOutputAndError();

        //Making the daemon infrastructure log with DEBUG. This is only for the infrastructure!
        //Each build request carries it's own log level and it is used during the execution of the build (see LogToClient)
        loggingManager.setLevel(LogLevel.DEBUG);

        loggingManager.start();
    }

    protected Daemon startDaemon(DaemonServices daemonServices) {
        Daemon daemon = daemonServices.get(Daemon.class);
        daemon.start();
        return daemon;
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
