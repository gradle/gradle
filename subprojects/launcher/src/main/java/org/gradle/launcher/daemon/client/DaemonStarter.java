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
import org.gradle.initialization.DefaultCommandLineConverter;
import org.gradle.launcher.daemon.server.DaemonIdleTimeout;
import org.gradle.launcher.daemon.bootstrap.GradleDaemon;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
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

    private final File userHomeDir;
    private final int idleTimeout;

    public DaemonStarter(File userHomeDir, int idleTimeout) {
        this.userHomeDir = userHomeDir;
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
        daemonArgs.add("-Xmx1024m");
        daemonArgs.add("-XX:MaxPermSize=256m");
        daemonArgs.add("-XX:MaxPermSize=256m");
        //Useful for debugging purposes - simply uncomment and connect to debug
//        daemonArgs.add("-Xdebug");
//        daemonArgs.add("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5006");
        daemonArgs.add("-cp");
        daemonArgs.add(GUtil.join(bootstrapClasspath, File.pathSeparator));
        daemonArgs.add(GradleDaemon.class.getName());
        daemonArgs.add(String.format("-%s", DefaultCommandLineConverter.GRADLE_USER_HOME));
        daemonArgs.add(userHomeDir.getAbsolutePath());

        DaemonIdleTimeout idleTimeout = new DaemonIdleTimeout(System.getenv("GRADLE_OPTS"), this.idleTimeout);
        daemonArgs.add(idleTimeout.toSysArg());

        LOGGER.info("starting daemon process: workingDir = {}, daemonArgs: {}", userHomeDir, daemonArgs);
        startProcess(daemonArgs, userHomeDir);
    }

    private void startProcess(List<String> args, File workingDir) {
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
                ProcessBuilder builder = new ProcessBuilder(args);
                builder.directory(workingDir);
                Process process = builder.start();
                process.getOutputStream().close();
                process.getErrorStream().close();
                process.getInputStream().close();
            }
        } catch (Exception e) {
            throw new GradleException("Could not start Gradle daemon.", e);
        }
    }
}