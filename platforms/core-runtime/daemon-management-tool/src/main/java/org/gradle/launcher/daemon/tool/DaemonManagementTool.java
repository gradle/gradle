/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.launcher.daemon.tool;

import org.gradle.api.logging.LogLevel;
import org.gradle.cli.CommandLineArgumentException;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.internal.logging.LoggingManagerFactory;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.services.LoggingServiceRegistry;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.launcher.cli.DefaultCommandLineActionFactory;
import org.gradle.launcher.daemon.client.DaemonClientFactory;
import org.gradle.launcher.daemon.client.DaemonClientGlobalServices;
import org.gradle.launcher.daemon.client.ManagedDaemons;
import org.gradle.launcher.daemon.configuration.DaemonBuildOptions;
import org.gradle.launcher.daemon.logging.DaemonLogConstants;

import java.io.File;
import java.io.PrintStream;
import java.util.List;

/**
 * A standalone command line tool to manage the Gradle daemons of the current Gradle version.
 *
 * <p>Usage: {@code daemon-management-tool [--gradle-user-home <dir>] <list|stop|stop-when-idle>}. It reuses
 * the same {@link ManagedDaemons} API and daemon protocol as {@code gradle --status} / {@code --stop}, but
 * without requiring a Gradle project or a full build invocation.
 */
public class DaemonManagementTool {

    private static final String GRADLE_USER_HOME = "gradle-user-home";
    private static final String REGISTRY_DIR = "registry-dir";
    private static final String LIST = "list";
    private static final String STOP = "stop";
    private static final String STOP_WHEN_IDLE = "stop-when-idle";

    public static void main(String[] args) {
        CommandLineParser parser = new CommandLineParser();
        parser.option(GRADLE_USER_HOME, "g").hasArgument();
        parser.option(REGISTRY_DIR).hasArgument();
        parser.allowMixedSubcommandsAndOptions();

        String command;
        File gradleUserHome;
        File daemonBaseDir;
        try {
            ParsedCommandLine parsedCommandLine = parser.parse(args);
            List<String> commands = parsedCommandLine.getExtraArguments();
            if (commands.size() != 1) {
                fail("Expected exactly one command.");
                return;
            }
            command = commands.get(0);
            gradleUserHome = parsedCommandLine.hasOption(GRADLE_USER_HOME)
                ? new File(parsedCommandLine.option(GRADLE_USER_HOME).getValue())
                : new File(System.getProperty("user.home"), ".gradle");
            daemonBaseDir = resolveDaemonBaseDir(parsedCommandLine, gradleUserHome);
        } catch (CommandLineArgumentException e) {
            fail(e.getMessage());
            return;
        }

        try {
            run(command, gradleUserHome, daemonBaseDir);
        } catch (IllegalArgumentException e) {
            fail(e.getMessage());
        }
    }

    /**
     * Resolves the daemon registry base directory, honouring (in order): the {@code --registry-dir}
     * option, the {@code org.gradle.daemon.registry.base} system property, then the default
     * {@code <gradleUserHome>/daemon}.
     */
    private static File resolveDaemonBaseDir(ParsedCommandLine parsedCommandLine, File gradleUserHome) {
        if (parsedCommandLine.hasOption(REGISTRY_DIR)) {
            return new File(parsedCommandLine.option(REGISTRY_DIR).getValue());
        }
        String registryBase = System.getProperty(DaemonBuildOptions.BaseDirOption.GRADLE_PROPERTY);
        if (registryBase != null) {
            return new File(registryBase);
        }
        return new File(gradleUserHome, DaemonLogConstants.DAEMON_LOG_DIR);
    }

    private static void run(String command, File gradleUserHome, File daemonBaseDir) {
        NativeServices.initializeOnClient(gradleUserHome, NativeServices.NativeServicesMode.fromSystemProperties());
        ServiceRegistry loggingServices = LoggingServiceRegistry.newCommandLineProcessLogging();
        LoggingManagerInternal loggingManager = loggingServices.get(LoggingManagerFactory.class).createLoggingManager();
        loggingManager.setLevelInternal(LogLevel.LIFECYCLE);
        loggingManager.start();
        try {
            ServiceRegistry basicServices = new DefaultCommandLineActionFactory().createBasicGlobalServices(loggingServices);
            ServiceRegistry globalServices = ServiceRegistryBuilder.builder()
                .displayName("Daemon management tool services")
                .parent(NativeServices.getInstance())
                .parent(basicServices)
                .provider(new DaemonClientGlobalServices())
                .build();
            ServiceRegistry messageServices = globalServices.get(DaemonClientFactory.class)
                .createMessageDaemonServices(loggingServices, daemonBaseDir);
            execute(command, messageServices.get(ManagedDaemons.class));
        } finally {
            loggingManager.stop();
        }
    }

    private static void execute(String command, ManagedDaemons daemons) {
        switch (command) {
            case LIST:
                daemons.reportStatus();
                break;
            case STOP:
                daemons.stopAll();
                break;
            case STOP_WHEN_IDLE:
                daemons.stopAllWhenIdle();
                break;
            default:
                throw new IllegalArgumentException("Unknown command '" + command + "'.");
        }
    }

    private static void fail(String message) {
        PrintStream err = System.err;
        err.println(message);
        err.println("Usage: daemon-management-tool [--gradle-user-home <dir>] [--registry-dir <dir>] <" + LIST + "|" + STOP + "|" + STOP_WHEN_IDLE + ">");
        System.exit(1);
    }

    private DaemonManagementTool() {
    }
}
