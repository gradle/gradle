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
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.installation.CurrentGradleInstallation;
import org.gradle.internal.installation.GradleInstallation;
import org.gradle.internal.instrumentation.agent.AgentUtils;
import org.gradle.internal.io.StreamByteBuffer;
import org.gradle.internal.jvm.JavaInfo;
import org.gradle.internal.jvm.JpmsConfiguration;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata;
import org.gradle.internal.jvm.inspection.JvmVersionDetector;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.serialize.FlushableEncoder;
import org.gradle.internal.serialize.kryo.KryoBackedEncoder;
import org.gradle.internal.stream.EncodedStream;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.gradle.launcher.daemon.DaemonExecHandleBuilder;
import org.gradle.launcher.daemon.bootstrap.DaemonOutputConsumer;
import org.gradle.launcher.daemon.configuration.DaemonParameters;
import org.gradle.launcher.daemon.configuration.DaemonPriority;
import org.gradle.launcher.daemon.context.DaemonRequestContext;
import org.gradle.launcher.daemon.diagnostics.DaemonStartupInfo;
import org.gradle.launcher.daemon.registry.DaemonDir;
import org.gradle.launcher.daemon.toolchain.DaemonJavaToolchainQueryService;
import org.gradle.launcher.daemon.toolchain.DaemonJvmCriteria;
import org.gradle.process.internal.DefaultClientExecHandleBuilderFactory;
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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class DefaultDaemonStarter implements DaemonStarter {
    private static final Logger LOGGER = Logging.getLogger(DefaultDaemonStarter.class);

    private final DaemonDir daemonDir;
    private final DaemonParameters daemonParameters;
    private final DaemonRequestContext daemonRequestContext;
    private final DaemonGreeter daemonGreeter;
    private final JvmVersionValidator versionValidator;
    private final JvmVersionDetector jvmVersionDetector;
    private final DaemonJavaToolchainQueryService daemonJavaToolchainQueryService;

    public DefaultDaemonStarter(DaemonDir daemonDir, DaemonParameters daemonParameters, DaemonRequestContext daemonRequestContext, DaemonGreeter daemonGreeter, JvmVersionValidator versionValidator, JvmVersionDetector jvmVersionDetector, DaemonJavaToolchainQueryService daemonJavaToolchainQueryService) {
        this.daemonDir = daemonDir;
        this.daemonParameters = daemonParameters;
        this.daemonRequestContext = daemonRequestContext;
        this.daemonGreeter = daemonGreeter;
        this.versionValidator = versionValidator;
        this.jvmVersionDetector = jvmVersionDetector;
        this.daemonJavaToolchainQueryService = daemonJavaToolchainQueryService;
    }

    @Override
    public DaemonStartupInfo startDaemon(boolean singleUse) {
        String daemonUid = UUID.randomUUID().toString();

        DaemonJvmCriteria criteria = daemonRequestContext.getJvmCriteria();
        final File resolvedJava;
        final int majorJavaVersion;

        if (criteria instanceof DaemonJvmCriteria.Spec) {
            // Gradle daemon properties have been defined
            IncubationLogger.incubatingFeatureUsed("Daemon JVM discovery");
            JvmInstallationMetadata jvmInstallationMetadata = daemonJavaToolchainQueryService.findMatchingToolchain((DaemonJvmCriteria.Spec) criteria);
            JavaInfo resolvedJvm = Jvm.forHome(jvmInstallationMetadata.getJavaHome().toFile());
            majorJavaVersion = ((DaemonJvmCriteria.Spec) criteria).getJavaVersion().asInt();
            resolvedJava = resolvedJvm.getJavaExecutable();
        } else if (criteria instanceof DaemonJvmCriteria.JavaHome) {
            JavaInfo resolvedJvm = Jvm.forHome(((DaemonJvmCriteria.JavaHome) criteria).getJavaHome());
            majorJavaVersion = jvmVersionDetector.getJavaVersionMajor(resolvedJvm);
            resolvedJava = resolvedJvm.getJavaExecutable();
        } else if (criteria instanceof DaemonJvmCriteria.LauncherJvm) {
            JavaInfo resolvedJvm = Jvm.current();
            majorJavaVersion = Jvm.current().getJavaVersionMajor();
            resolvedJava = resolvedJvm.getJavaExecutable();
        } else {
            throw new IllegalStateException("Unknown DaemonJvmCriteria type: " + criteria.getClass().getName());
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

        versionValidator.validate(majorJavaVersion);

        List<String> daemonArgs = new ArrayList<>();
        daemonArgs.addAll(getPriorityArgs(daemonRequestContext.getPriority()));
        daemonArgs.add(resolvedJava.getAbsolutePath());
        Collection<String> daemonOpts = daemonRequestContext.getDaemonOpts();
        if (majorJavaVersion >= 9) {
            daemonArgs.addAll(JpmsConfiguration.GRADLE_DAEMON_JPMS_ARGS);
        }
        daemonArgs.addAll(daemonOpts);
        daemonArgs.add("-cp");
        daemonArgs.add(CollectionUtils.join(File.pathSeparator, classpath.getAsFiles()));

        if (Boolean.getBoolean("org.gradle.daemon.debug")) {
            daemonArgs.add(JvmOptions.getDebugArgument(true, true, "5005"));
        }

        ClassPath agentClasspath = registry.getModule(AgentUtils.AGENT_MODULE_NAME).getImplementationClasspath();
        if (daemonRequestContext.shouldApplyInstrumentationAgent()) {
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
            encoder.writeSmallInt(daemonParameters.getLogLevel().ordinal());
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

    private List<String> getPriorityArgs(DaemonPriority priority) {
        if (priority == DaemonPriority.NORMAL) {
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
            DefaultClientExecHandleBuilderFactory execActionFactory = DefaultClientExecHandleBuilderFactory.root(gradleUserHome);
            try {
                ExecHandle handle = new DaemonExecHandleBuilder().build(args, workingDir, outputConsumer, stdInput, execActionFactory.newExecHandleBuilder());

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
