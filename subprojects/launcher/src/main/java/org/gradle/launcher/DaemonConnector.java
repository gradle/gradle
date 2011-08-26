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
package org.gradle.launcher;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.internal.DefaultClassPathRegistry;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.DefaultCommandLineConverter;
import org.gradle.messaging.remote.internal.ConnectException;
import org.gradle.messaging.remote.internal.Connection;
import org.gradle.messaging.remote.internal.DefaultMessageSerializer;
import org.gradle.messaging.remote.internal.inet.TcpOutgoingConnector;
import org.gradle.util.GUtil;
import org.gradle.util.Jvm;
import org.gradle.util.UncheckedException;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

public class DaemonConnector {
    private static final Logger LOGGER = Logging.getLogger(DaemonConnector.class);
    private final File userHomeDir;
    private DaemonRegistry daemonRegistry;
    private final int idleDaemonTimeout;

    public DaemonConnector(File userHomeDir) {
        this(userHomeDir, 3 * 60 * 60 * 1000);
    }

    DaemonConnector(File userHomeDir, int idleDaemonTimeout) {
        this.userHomeDir = userHomeDir;
        this.daemonRegistry = new DaemonRegistry(userHomeDir);
        this.idleDaemonTimeout = idleDaemonTimeout;
    }

    /**
     * Attempts to connect to the daemon, if it is running.
     *
     * @return The connection, or null if not running.
     */
    public Connection<Object> maybeConnect() {
        return findConnection(daemonRegistry.getAll());
    }

    private Connection<Object> findConnection(List<DaemonStatus> statuses) {
        for (DaemonStatus status : statuses) {
            try {
                return new TcpOutgoingConnector<Object>(new DefaultMessageSerializer<Object>(getClass().getClassLoader())).connect(status.getAddress());
            } catch (ConnectException e) {
                // Ignore
            }
        }
        return null;
    }

    /**
     * Connects to the daemon, starting it if required.
     *
     * @return The connection. Never returns null.
     */
    public Connection<Object> connect() {
        Connection<Object> connection = findConnection(daemonRegistry.getIdle());
        if (connection != null) {
            return connection;
        }

        LOGGER.info("Starting Gradle daemon");
        startDaemon();
        Date expiry = new Date(System.currentTimeMillis() + 30000L);
        do {
            connection = findConnection(daemonRegistry.getIdle());
            if (connection != null) {
                return connection;
            }
            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                throw UncheckedException.asUncheckedException(e);
            }
        } while (System.currentTimeMillis() < expiry.getTime());

        throw new GradleException("Timeout waiting to connect to Gradle daemon.");
    }

    private void startDaemon() {
        Set<File> bootstrapClasspath = new DefaultClassPathRegistry().getClassPathFiles("GRADLE_BOOTSTRAP");
        if (bootstrapClasspath.isEmpty()) {
            throw new IllegalStateException("Unable to construct a bootstrap classpath when starting the daemon");
        }
        
        List<String> daemonArgs = new ArrayList<String>();
        daemonArgs.add(Jvm.current().getJavaExecutable().getAbsolutePath());
        daemonArgs.add("-Xmx1024m");
        daemonArgs.add("-XX:MaxPermSize=256m");
        //TODO SF - remove later
//        daemonArgs.add("-Xdebug");
//        daemonArgs.add("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5006");
        daemonArgs.add("-cp");
        daemonArgs.add(GUtil.join(bootstrapClasspath, File.pathSeparator));
        daemonArgs.add(GradleDaemon.class.getName());
        daemonArgs.add(String.format("-%s", DefaultCommandLineConverter.GRADLE_USER_HOME));
        daemonArgs.add(userHomeDir.getAbsolutePath());
        daemonArgs.add("-DidleDaemonTimeout=" + idleDaemonTimeout);
        //TODO SF standarize the sys property org.gradle.daemon.idletimeout
        DaemonStartAction daemon = new DaemonStartAction();
        daemon.args(daemonArgs);
        daemon.workingDir(userHomeDir);
        daemon.start();
    }


    public DaemonRegistry getDaemonRegistry() {
        return daemonRegistry;
    }
}
