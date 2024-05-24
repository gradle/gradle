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
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.classpath.DefaultModuleRegistry;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.agents.AgentUtils;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.installation.CurrentGradleInstallation;
import org.gradle.internal.installation.GradleInstallation;
import org.gradle.internal.io.StreamByteBuffer;
import org.gradle.internal.jvm.JavaInfo;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.serialize.FlushableEncoder;
import org.gradle.internal.serialize.kryo.KryoBackedEncoder;
import org.gradle.internal.stream.EncodedStream;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.gradle.launcher.daemon.DaemonExecHandleBuilder;
import org.gradle.launcher.daemon.bootstrap.DaemonOutputConsumer;
import org.gradle.launcher.daemon.configuration.DaemonParameters;
import org.gradle.launcher.daemon.diagnostics.DaemonStartupInfo;
import org.gradle.launcher.daemon.registry.DaemonDir;
import org.gradle.launcher.daemon.toolchain.DaemonJavaToolchainQueryService;
import org.gradle.launcher.daemon.toolchain.DaemonJvmCriteria;
import org.gradle.process.internal.DefaultExecActionFactory;
import org.gradle.process.internal.ExecHandle;
import org.gradle.process.internal.JvmOptions;
import org.gradle.util.GradleVersion;
import org.gradle.util.internal.CollectionUtils;
import org.gradle.util.internal.GFileUtils;
import org.gradle.util.internal.IncubationLogger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class DefaultDaemonStarter implements DaemonStarter {
    private static final Logger LOGGER = Logging.getLogger(DefaultDaemonStarter.class);

    private final DaemonDir daemonDir;
    private final DaemonParameters daemonParameters;
    private final DaemonGreeter daemonGreeter;
    private final JvmVersionValidator versionValidator;
    private final DaemonJavaToolchainQueryService daemonJavaToolchainQueryService;

    public DefaultDaemonStarter(DaemonDir daemonDir, DaemonParameters daemonParameters, DaemonGreeter daemonGreeter, JvmVersionValidator versionValidator, DaemonJavaToolchainQueryService daemonJavaToolchainQueryService) {
        this.daemonDir = daemonDir;
        this.daemonParameters = daemonParameters;
        this.daemonGreeter = daemonGreeter;
        this.versionValidator = versionValidator;
        this.daemonJavaToolchainQueryService = daemonJavaToolchainQueryService;
    }

    @Override
    public DaemonStartupInfo startDaemon(boolean singleUse) {
        String daemonUid = UUID.randomUUID().toString();

        final JavaInfo resolvedJvm;
        // Gradle daemon properties have been defined
        if (daemonParameters.getRequestedJvmCriteria() != null) {
            IncubationLogger.incubatingFeatureUsed("Daemon JVM discovery");
            DaemonJvmCriteria criteria = daemonParameters.getRequestedJvmCriteria();
            JvmInstallationMetadata jvmInstallationMetadata = daemonJavaToolchainQueryService.findMatchingToolchain(criteria);
            resolvedJvm = Jvm.forHome(jvmInstallationMetadata.getJavaHome().toFile());
        } else if (daemonParameters.getRequestedJvmBasedOnJavaHome() != null && daemonParameters.getRequestedJvmBasedOnJavaHome() != Jvm.current()) {
            // Either the TAPI client or org.gradle.java.home has been provided
            resolvedJvm = Jvm.forHome(daemonParameters.getRequestedJvmBasedOnJavaHome().getJavaHome());
        } else {
            resolvedJvm = Jvm.current();
        }

        GradleInstallation gradleInstallation = CurrentGradleInstallation.get();
        ModuleRegistry registry = new DefaultModuleRegistry(gradleInstallation);
        ClassPath classpath;
        List<File> searchClassPath;

        if (gradleInstallation == null) {
            // When not running from a Gradle distro, need the daemon main jar and the daemon server implementation plus the search path to look for other modules
            classpath = registry.getModule("gradle-daemon-server").getAllRequiredModulesClasspath();
            classpath = classpath.plus(registry.getModule("gradle-daemon-main").getImplementationClasspath());
            searchClassPath = registry.getAdditionalClassPath().getAsFiles();
        } else {
            // When running from a Gradle distro, only need the daemon main jar. The daemon can find everything from there.
            classpath = registry.getModule("gradle-daemon-main").getImplementationClasspath();
            searchClassPath = Collections.emptyList();
        }
        if (classpath.isEmpty()) {
            throw new IllegalStateException("Unable to construct a bootstrap classpath when starting the daemon");
        }

        versionValidator.validate(resolvedJvm);

        List<String> daemonArgs = new ArrayList<>();
        daemonArgs.addAll(getPriorityArgs(daemonParameters.getPriority()));
        daemonArgs.add(resolvedJvm.getJavaExecutable().getAbsolutePath());

        List<String> daemonOpts = daemonParameters.getEffectiveJvmArgs();
        daemonArgs.addAll(daemonOpts);
        daemonArgs.add("-cp");
        daemonArgs.add(CollectionUtils.join(File.pathSeparator, classpath.getAsFiles()));

        if (Boolean.getBoolean("org.gradle.daemon.debug")) {
            daemonArgs.add(JvmOptions.getDebugArgument(true, true, "5005"));
        }

        ClassPath agentClasspath = registry.getModule(AgentUtils.AGENT_MODULE_NAME).getImplementationClasspath();
        if (daemonParameters.shouldApplyInstrumentationAgent()) {
            if (agentClasspath.isEmpty()) {
                throw new IllegalStateException("Cannot find the agent JAR");
            }
            for (File agentJar : agentClasspath.getAsFiles()) {
                daemonArgs.add("-javaagent:" + agentJar);
            }
        }

        LOGGER.debug("Using daemon args: {}", daemonArgs);

        daemonArgs.add("org.gradle.launcher.daemon.bootstrap.GradleDaemon");
        // Version isn't used, except by a human looking at the output of jps.
        daemonArgs.add(GradleVersion.current().getVersion());

        // Serialize configuration to daemon via the process' stdin
        StreamByteBuffer buffer = new StreamByteBuffer();
        FlushableEncoder encoder = new KryoBackedEncoder(new EncodedStream.EncodedOutput(buffer.getOutputStream()));
        try {
            encoder.writeString(daemonParameters.getGradleUserHomeDir().getAbsolutePath());
            encoder.writeString(daemonDir.getBaseDir().getAbsolutePath());
            encoder.writeSmallInt(daemonParameters.getIdleTimeout());
            encoder.writeSmallInt(daemonParameters.getPeriodicCheckInterval());
            encoder.writeBoolean(singleUse);
            encoder.writeSmallInt(daemonParameters.getNativeServicesMode().ordinal());
            encoder.writeString(daemonUid);
            encoder.writeSmallInt(daemonParameters.getPriority().ordinal());
            encoder.writeSmallInt(daemonOpts.size());
            for (String daemonOpt : daemonOpts) {
                encoder.writeString(daemonOpt);
            }
            encoder.writeSmallInt(searchClassPath.size());
            for (File file : searchClassPath) {
                encoder.writeString(file.getAbsolutePath());
            }
            encoder.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        InputStream stdInput = buffer.getInputStream();

        return startProcess(
            daemonArgs,
            daemonDir.getVersionedDir(),
            daemonParameters.getGradleUserHomeDir().getAbsoluteFile(),
            stdInput
        );
    }

    private List<String> getPriorityArgs(DaemonParameters.Priority priority) {
        if (priority == DaemonParameters.Priority.NORMAL) {
            return Collections.emptyList();
        }
        OperatingSystem os = OperatingSystem.current();
        if (os.isUnix()) {
            return Arrays.asList("nice", "-n", "10");
        } else if (os.isWindows()) {
            return Arrays.asList("cmd.exe", "/d", "/c", "start", "\"Gradle build daemon\"", "/b", "/belownormal", "/wait");
        } else {
            return Collections.emptyList();
        }
    }

    private DaemonStartupInfo startProcess(List<String> args, File workingDir, File gradleUserHome, InputStream stdInput) {
        LOGGER.debug("Starting daemon process: workingDir = {}, daemonArgs: {}", workingDir, args);
        Timer clock = Time.startTimer();
        try {
            GFileUtils.mkdirs(workingDir);

            DaemonOutputConsumer outputConsumer = new DaemonOutputConsumer();

            // This factory should be injected but leaves non-daemon threads running when used from the tooling API client
            @SuppressWarnings("deprecation")
            DefaultExecActionFactory execActionFactory = DefaultExecActionFactory.root(gradleUserHome);
            try {
                ExecHandle handle = new DaemonExecHandleBuilder().build(args, workingDir, outputConsumer, stdInput, execActionFactory.newExec());

                handle.start();
                LOGGER.debug("Gradle daemon process is starting. Waiting for the daemon to detach...");
                handle.waitForFinish();
                LOGGER.debug("Gradle daemon process is now detached.");
            } finally {
                CompositeStoppable.stoppable(execActionFactory).stop();
            }

            return daemonGreeter.parseDaemonOutput(outputConsumer.getProcessOutput(), args);
        } catch (GradleException e) {
            throw e;
        } catch (Exception e) {
            throw new GradleException("Could not start Gradle daemon.", e);
        } finally {
            LOGGER.info("An attempt to start the daemon took {}.", clock.getElapsed());
        }
    }

}
