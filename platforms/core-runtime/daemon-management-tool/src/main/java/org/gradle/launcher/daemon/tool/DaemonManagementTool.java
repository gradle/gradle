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

import org.gradle.cli.CommandLineArgumentException;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.launcher.daemon.management.DaemonManagement;
import org.gradle.launcher.daemon.management.ManagedDaemons;

import java.io.File;
import java.io.PrintStream;
import java.util.List;

/**
 * A standalone command line tool to manage the Gradle daemons of the current Gradle version.
 *
 * <p>Usage: {@code daemon-management-tool [--gradle-user-home <dir>] [--registry-dir <dir>] <list|stop|stop-when-idle>}.
 *
 * <p>This tool talks to daemons using only the {@code org.gradle.launcher.daemon.management} API: it obtains a
 * {@link ManagedDaemons} from {@link DaemonManagement} and drives it. It does not reference any registry,
 * connection, protocol or service-wiring internals, which is the whole point of the daemon management API.
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
        File registryDirOverride;
        try {
            ParsedCommandLine parsedCommandLine = parser.parse(args);
            List<String> commands = parsedCommandLine.getExtraArguments();
            if (commands.size() != 1) {
                fail("Expected exactly one command.");
                return;
            }
            command = commands.get(0);
            if (!command.equals(LIST) && !command.equals(STOP) && !command.equals(STOP_WHEN_IDLE)) {
                fail("Unknown command '" + command + "'.");
                return;
            }
            gradleUserHome = parsedCommandLine.hasOption(GRADLE_USER_HOME)
                ? new File(parsedCommandLine.option(GRADLE_USER_HOME).getValue())
                : new File(System.getProperty("user.home"), ".gradle");
            registryDirOverride = parsedCommandLine.hasOption(REGISTRY_DIR)
                ? new File(parsedCommandLine.option(REGISTRY_DIR).getValue())
                : null;
        } catch (CommandLineArgumentException e) {
            fail(e.getMessage());
            return;
        }

        if (registryDirOverride != null) {
            DaemonManagement.withManagedDaemons(gradleUserHome, registryDirOverride, daemons -> execute(command, daemons));
        } else {
            DaemonManagement.withManagedDaemons(gradleUserHome, daemons -> execute(command, daemons));
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
                throw new IllegalStateException("Unknown command '" + command + "'.");
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
