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

import org.gradle.*;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.configuration.GradleLauncherMetaData;
import org.gradle.gradleplugin.userinterface.swing.standalone.BlockingApplication;
import org.gradle.initialization.*;
import org.gradle.initialization.CommandLineConverter;
import org.gradle.initialization.DefaultCommandLineConverter;
import org.gradle.util.Clock;
import org.gradle.util.GradleVersion;

import java.io.PrintStream;
import java.util.List;

public class CommandLineActionFactory {
    private static final String HELP = "h";
    private static final String GUI = "gui";
    private static final String VERSION = "v";
    private static Logger logger = Logging.getLogger(CommandLineActionFactory.class);
    private final BuildCompleter buildCompleter;
    private final CommandLineConverter<StartParameter> startParameterConverter;
    private final Clock buildTimeClock = new Clock();

    public CommandLineActionFactory(BuildCompleter buildCompleter) {
        this(buildCompleter, new DefaultCommandLineConverter());
    }

    CommandLineActionFactory(BuildCompleter buildCompleter, CommandLineConverter<StartParameter> startParameterConverter) {
        this.buildCompleter = buildCompleter;
        this.startParameterConverter = startParameterConverter;
    }

    /**
     * Converts the given command-line arguments to a {@code Runnable} action which performs the action requested by the
     * command-line args.
     *
     * @param args The command-line arguments.
     * @return The action to execute.
     */
    Runnable convert(List<String> args) {
        CommandLineParser parser = new CommandLineParser();
        startParameterConverter.configure(parser);
        parser.option(HELP, "?", "help").hasDescription("Shows this help message");
        parser.option(VERSION, "version").hasDescription("Print version info.");
        parser.option(GUI).hasDescription("Launches a GUI application");

        try {
            ParsedCommandLine commandLine = parser.parse(args);
            if (commandLine.hasOption(HELP)) {
                return new ShowUsageAction(parser);
            }
            if (commandLine.hasOption(VERSION)) {
                return new ShowVersionAction();
            }
            if (commandLine.hasOption(GUI)) {
                return new ShowGuiAction();
            }

            StartParameter startParameter = new StartParameter();
            startParameterConverter.convert(commandLine, startParameter);
            return new RunBuildAction(startParameter);
        } catch (CommandLineArgumentException e) {
            return new CommandLineParseFailureAction(parser, e);
        }
    }

    private void showUsage(PrintStream out, CommandLineParser parser) {
        out.println();
        out.print("USAGE: ");
        new GradleLauncherMetaData().describeCommand(out, "[option...]", "[task...]");
        out.println();
        out.println();
        parser.printUsage(out);
        out.println();
    }

    private class CommandLineParseFailureAction implements Runnable {
        private final Exception e;
        private final CommandLineParser parser;

        public CommandLineParseFailureAction(CommandLineParser parser, Exception e) {
            this.parser = parser;
            this.e = e;
        }

        public void run() {
            System.err.println();
            System.err.println(e.getMessage());
            showUsage(System.err, parser);
            buildCompleter.exit(e);
        }
    }

    private class ShowUsageAction implements Runnable {
        private final CommandLineParser parser;

        public ShowUsageAction(CommandLineParser parser) {
            this.parser = parser;
        }

        public void run() {
            showUsage(System.out, parser);
            buildCompleter.exit(null);
        }
    }

    private class ShowVersionAction implements Runnable {
        public void run() {
            System.out.println(new GradleVersion().prettyPrint());
            buildCompleter.exit(null);
        }
    }

    class ShowGuiAction implements Runnable {
        public void run() {
            BlockingApplication.launchAndBlock();
            buildCompleter.exit(null);
        }
    }

    private class RunBuildAction implements Runnable {
        private final StartParameter startParameter;

        public RunBuildAction(StartParameter startParameter) {
            this.startParameter = startParameter;
        }
        
        public void run() {
            GradleLauncher gradleLauncher = GradleLauncher.newInstance(startParameter);
            gradleLauncher.useLogger(new BuildLogger(logger, buildTimeClock, startParameter));
            BuildResult buildResult = gradleLauncher.run();
            buildCompleter.exit(buildResult.getFailure());
        }
    }
}
