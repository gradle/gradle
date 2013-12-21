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
import org.gradle.launcher.daemon.DaemonExecHandleBuilder;
import org.gradle.launcher.daemon.bootstrap.DaemonGreeter;
import org.gradle.launcher.daemon.bootstrap.DaemonOutputConsumer;
import org.gradle.launcher.daemon.bootstrap.GradleDaemon;
import org.gradle.launcher.daemon.configuration.DaemonParameters;
import org.gradle.launcher.daemon.diagnostics.DaemonDiagnostics;
import org.gradle.launcher.daemon.diagnostics.DaemonStartupInfo;
import org.gradle.launcher.daemon.registry.DaemonDir;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.ExecHandle;
import org.gradle.util.Clock;
import org.gradle.util.CollectionUtils;
import org.gradle.util.GFileUtils;
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
    private DaemonGreeter daemonGreeter;

    public DefaultDaemonStarter(DaemonDir daemonDir, DaemonParameters daemonParameters, DaemonGreeter daemonGreeter) {
        this.daemonDir = daemonDir;
        this.daemonParameters = daemonParameters;
        this.daemonGreeter = daemonGreeter;
    }

    public DaemonStartupInfo startDaemon() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        Set<File> bootstrapClasspath = new LinkedHashSet<File>();
        bootstrapClasspath.addAll(registry.getModule("gradle-launcher").getImplementationClasspath().getAsFiles());
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
        daemonArgs.add(CollectionUtils.join(File.pathSeparator, bootstrapClasspath));
        daemonArgs.add(GradleDaemon.class.getName());
        daemonArgs.add(GradleVersion.current().getVersion());
        daemonArgs.add(daemonDir.getBaseDir().getAbsolutePath());
        daemonArgs.add(String.valueOf(daemonParameters.getIdleTimeout()));
        daemonArgs.add(daemonParameters.getUid());

        //all remaining arguments are daemon startup jvm opts.
        //we need to pass them as *program* arguments to avoid problems with getInputArguments().
        daemonArgs.addAll(daemonOpts);

        DaemonDiagnostics diagnostics = startProcess(daemonArgs, daemonDir.getVersionedDir());

        return new DaemonStartupInfo(daemonParameters.getUid(), diagnostics);
    }

    private DaemonDiagnostics startProcess(final List<String> args, final File workingDir) {
        LOGGER.info("Starting daemon process: workingDir = {}, daemonArgs: {}", workingDir, args);
        Clock clock = new Clock();
        try {
            GFileUtils.mkdirs(workingDir);

            DaemonOutputConsumer outputConsumer = new DaemonOutputConsumer();
            ExecHandle handle = new DaemonExecHandleBuilder().build(args, workingDir, outputConsumer);

            handle.start();
            LOGGER.debug("Gradle daemon process is starting. Waiting for the daemon to detach...");
            ExecResult result = handle.waitForFinish();
            LOGGER.debug("Gradle daemon process is now detached.");

            return daemonGreeter.parseDaemonOutput(outputConsumer.getProcessOutput(), result);
        } catch (GradleException e) {
            throw e;
        } catch (Exception e) {
            throw new GradleException("Could not start Gradle daemon.", e);
        } finally {
            LOGGER.info("An attempt to start the daemon took {}.", clock.getTime());
        }
    }

}