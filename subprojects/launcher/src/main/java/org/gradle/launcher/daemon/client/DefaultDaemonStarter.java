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

import org.gradle.api.GradleException;
import org.gradle.api.internal.classpath.DefaultModuleRegistry;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.nativeplatform.jna.WindowsProcessStarter;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.launcher.daemon.bootstrap.GradleDaemon;
import org.gradle.launcher.daemon.configuration.DaemonParameters;
import org.gradle.launcher.daemon.logging.DaemonGreeter;
import org.gradle.launcher.daemon.registry.DaemonDir;
import org.gradle.util.GUtil;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DefaultDaemonStarter implements DaemonStarter {

    private static final Logger LOGGER = Logging.getLogger(DefaultDaemonStarter.class);

    private final DaemonDir daemonDir;
    private final DaemonParameters daemonParameters;

    public DefaultDaemonStarter(DaemonDir daemonDir, DaemonParameters daemonParameters) {
        this.daemonDir = daemonDir;
        this.daemonParameters = daemonParameters;
    }

    public String startDaemon() {
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
        daemonArgs.add(daemonParameters.getEffectiveJavaExecutable());

        List<String> daemonOpts = daemonParameters.getEffectiveJvmArgs();
        LOGGER.debug("Using daemon opts: {}", daemonOpts);
        daemonArgs.addAll(daemonOpts);
        //Useful for debugging purposes - simply uncomment and connect to debug
//        daemonArgs.add("-Xdebug");
//        daemonArgs.add("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5006");
        daemonArgs.add("-cp");
        daemonArgs.add(GUtil.join(bootstrapClasspath, File.pathSeparator));
        daemonArgs.add(GradleDaemon.class.getName());
        daemonArgs.add(GradleVersion.current().getVersion());
        daemonArgs.add(daemonDir.getBaseDir().getAbsolutePath());
        daemonArgs.add(String.valueOf(daemonParameters.getIdleTimeout()));
        daemonArgs.add(daemonParameters.getUid());

        //all remaining arguments are daemon startup jvm opts.
        //we need to pass them as *program* arguments to avoid problems with getInputArguments().
        daemonArgs.addAll(daemonOpts);

        startProcess(daemonArgs, daemonDir.getVersionedDir());

        return daemonParameters.getUid();
    }

    private void startProcess(List<String> args, File workingDir) {
        LOGGER.info("Starting daemon process: workingDir = {}, daemonArgs: {}", workingDir, args);
        try {
            workingDir.mkdirs();
            if (OperatingSystem.current().isWindows()) {
                new WindowsProcessStarter().start();
            }
            Process process = new ProcessBuilder(args).redirectErrorStream(true).directory(workingDir).start();
            new DaemonGreeter().verifyGreetingReceived(process);

            process.getOutputStream().close();
            process.getErrorStream().close();
            process.getInputStream().close();
        } catch (GradleException e) {
            throw e;
        } catch (Exception e) {
            throw new GradleException("Could not start Gradle daemon.", e);
        }
    }
}