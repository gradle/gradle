/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.launcher.daemon.client;

import org.gradle.api.internal.classpath.DefaultModuleRegistry;
import org.gradle.launcher.daemon.registry.DaemonDir;
import org.gradle.launcher.daemon.bootstrap.GradleDaemon;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.util.GradleVersion;
import org.gradle.util.Jvm;
import org.gradle.util.GUtil;
import org.gradle.api.GradleException;
import org.gradle.os.OperatingSystem;
import org.gradle.os.jna.WindowsProcessStarter;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DaemonStarter implements Runnable {

    private static final Logger LOGGER = Logging.getLogger(DaemonStarter.class);

    private final DaemonDir daemonDir;
    private final List<String> daemonOpts;
    private final int idleTimeout;

    public DaemonStarter(DaemonDir daemonDir, List<String> daemonOpts, int idleTimeout) {
        this.daemonDir = daemonDir;
        this.daemonOpts = daemonOpts;
        this.idleTimeout = idleTimeout;
    }

    public void run() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        Set<File> bootstrapClasspath = new LinkedHashSet<File>();
        bootstrapClasspath.addAll(registry.getModule("gradle-launcher").getImplementationClasspath());
        if (registry.getGradleHome() == null) {
            // Running from the classpath - chuck in everything we can find
            bootstrapClasspath.addAll(registry.getFullClasspath());
        }
        if (bootstrapClasspath.isEmpty()) {
            throw new IllegalStateException("Unable to construct a bootstrap classpath when starting the daemon");
        }

        List<String> daemonArgs = new ArrayList<String>();
        daemonArgs.add(Jvm.current().getJavaExecutable().getAbsolutePath());
        LOGGER.info("Setting GRADLE_DAEMON_OPTS: {}", daemonOpts);
        daemonArgs.addAll(daemonOpts);
        //Useful for debugging purposes - simply uncomment and connect to debug
//        daemonArgs.add("-Xdebug");
//        daemonArgs.add("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5006");
        daemonArgs.add("-cp");
        daemonArgs.add(GUtil.join(bootstrapClasspath, File.pathSeparator));
        daemonArgs.add(GradleDaemon.class.getName());
        daemonArgs.add(GradleVersion.current().getVersion());
        daemonArgs.add(daemonDir.getBaseDir().getAbsolutePath());
        daemonArgs.add(String.valueOf(idleTimeout));

        startProcess(daemonArgs, daemonDir.getVersionedDir());
    }

    private void startProcess(List<String> args, File workingDir) {
        LOGGER.info("Starting daemon process: workingDir = {}, daemonArgs: {}", workingDir, args);
        try {
            workingDir.mkdirs();
            if (OperatingSystem.current().isWindows()) {
                StringBuilder commandLine = new StringBuilder();
                for (String arg : args) {
                    commandLine.append('"');
                    commandLine.append(arg);
                    commandLine.append("\" ");
                }

                new WindowsProcessStarter().start(workingDir, commandLine.toString());
            } else {
                Process process = new ProcessBuilder(args).directory(workingDir).start();
                process.getOutputStream().close();
                process.getErrorStream().close();
                process.getInputStream().close();
            }
        } catch (Exception e) {
            throw new GradleException("Could not start Gradle daemon.", e);
        }
    }
}