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
import org.gradle.initialization.CommandLine2StartParameterConverter;
import org.gradle.initialization.DefaultCommandLine2StartParameterConverter;
import org.gradle.util.Clock;
import org.gradle.util.GradleVersion;

import java.io.PrintStream;

public class CommandLineActionFactory {
    private static Logger logger = Logging.getLogger(CommandLineActionFactory.class);
    private final BuildCompleter buildCompleter;
    private final CommandLine2StartParameterConverter startParameterConverter;
    private final Clock buildTimeClock = new Clock();

    public CommandLineActionFactory(BuildCompleter buildCompleter) {
        this(buildCompleter, new DefaultCommandLine2StartParameterConverter());
    }

    CommandLineActionFactory(BuildCompleter buildCompleter, CommandLine2StartParameterConverter startParameterConverter) {
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
    Runnable convert(String[] args) {
        final StartParameter startParameter;
        try {
            startParameter = startParameterConverter.convert(args);
        } catch (CommandLineArgumentException e) {
            return new CommandLineParseFailureAction(e);
        }

        if (startParameter.isShowHelp()) {
            return new ShowUsageAction();
        }

        if (startParameter.isShowVersion()) {
            return new ShowVersionAction();
        }

        if (startParameter.isLaunchGUI()) {
            return new ShowGuiAction();
        }

        return new RunBuildAction(startParameter);
    }

    private void showUsage(PrintStream out) {
        out.println();
        out.print("USAGE: ");
        new GradleLauncherMetaData().describeCommand(out, "[option...]", "[task...]");
        out.println();
        out.println();
        startParameterConverter.showHelp(out);
        out.println();
    }

    private class CommandLineParseFailureAction implements Runnable {
        private final Exception e;

        public CommandLineParseFailureAction(Exception e) {
            this.e = e;
        }

        public void run() {
            System.err.println();
            System.err.println(e.getMessage());
            showUsage(System.err);
            buildCompleter.exit(e);
        }
    }

    private class ShowUsageAction implements Runnable {
        public void run() {
            showUsage(System.out);
            buildCompleter.exit(null);
        }
    }

    private class ShowVersionAction implements Runnable {
        public void run() {
            System.out.println(new GradleVersion().prettyPrint());
            buildCompleter.exit(null);
        }
    }

    private class ShowGuiAction implements Runnable {
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
