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

import org.gradle.util.UncheckedException;
import org.gradle.StartParameter;
import org.gradle.initialization.DefaultCommandLineConverter;
import org.gradle.launcher.daemon.registry.DaemonDir;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.launcher.daemon.registry.PersistentDaemonRegistry;
import org.gradle.launcher.daemon.server.*;
import org.gradle.launcher.daemon.server.exec.DefaultDaemonCommandExecuter;
import org.gradle.logging.LoggingServiceRegistry;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.launcher.env.LenientEnvHacker;

import java.io.*;
import java.util.Arrays;

/**
 * The server portion of the build daemon. See {@link org.gradle.launcher.daemon.client.DaemonClient} for a description of the protocol.
 */
public class DaemonMain implements Runnable {

    private static final Logger LOGGER = Logging.getLogger(DaemonMain.class);
    
    final private StartParameter startParameter;
    
    public static void main(String[] args) {
        StartParameter startParameter = new DefaultCommandLineConverter().convert(Arrays.asList(args));
        
        try {
            redirectOutputsAndInput(startParameter);
        } catch (IOException e) {
            throw UncheckedException.asUncheckedException(e);
        }
        
        new DaemonMain(startParameter).run();
    }
    
    public DaemonMain(StartParameter startParameter) {
        this.startParameter = startParameter;
    }
    
    public void run() {
        final Long pid = new LenientEnvHacker().getPid();
        
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                LOGGER.info("Daemon[pid = {}] finishing", pid);
            }
        });
        
        LoggingServiceRegistry loggingServices = LoggingServiceRegistry.newChildProcessLogging();
        DaemonServerConnector connector = new DaemonTcpServerConnector();
        
        File registryDir = startParameter.getGradleUserHomeDir();
        DaemonRegistry daemonRegistry = new PersistentDaemonRegistry(registryDir);
        
        int idleTimeout = getIdleTimeout(startParameter);
        float idleTimeoutSecs = idleTimeout / 1000;
        
        LOGGER.lifecycle("Starting daemon[pid = {}] with settings: idleTimeout = {} secs, registryDir = {}", pid, idleTimeoutSecs, registryDir);
        Daemon daemon = new Daemon(connector, daemonRegistry, new DefaultDaemonCommandExecuter(loggingServices));
        
        daemon.start();
        boolean wasStopped = daemon.awaitStopOrIdleTimeout(idleTimeout);
        if (wasStopped) {
            LOGGER.info("Daemon stopping due to stop request");
        } else {
            LOGGER.info("Daemon hit idle timeout (" + idleTimeoutSecs + " secs), stopping");
            daemon.stop();
        }
    }

    private static void redirectOutputsAndInput(StartParameter startParameter) throws IOException {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
//        InputStream originalIn = System.in;
        DaemonDir daemonDir = new DaemonDir(startParameter.getGradleUserHomeDir());
        File logOutputFile = daemonDir.createUniqueLog();
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
    
    private static int getIdleTimeout(StartParameter startParameter) {
        return new DaemonIdleTimeout(startParameter).getIdleTimeout();
    }
}
