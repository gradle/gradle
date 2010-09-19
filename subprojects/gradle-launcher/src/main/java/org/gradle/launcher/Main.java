/*
 * Copyright 2009 the original author or authors.
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

/**
 * @author Hans Dockter
 */
public class Main {
    private static Logger logger = Logging.getLogger(Main.class);

    private final String[] args;
    private BuildCompleter buildCompleter = new ProcessExitBuildCompleter();
    private CommandLine2StartParameterConverter parameterConverter = new DefaultCommandLine2StartParameterConverter();

    public Main(String[] args) {
        this.args = args;
    }

    public static void main(String[] args) {
        new Main(args).execute();
    }

    void setBuildCompleter(BuildCompleter buildCompleter) {
        this.buildCompleter = buildCompleter;
    }

    public void setParameterConverter(CommandLine2StartParameterConverter parameterConverter) {
        this.parameterConverter = parameterConverter;
    }

    public void execute() {
        Clock buildTimeClock = new Clock();

        StartParameter startParameter = null;

        try {
            startParameter = parameterConverter.convert(args);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            showUsage(System.err);
            buildCompleter.exit(e);
        }

        if (startParameter.isShowHelp()) {
            showUsage(System.out);
            buildCompleter.exit(null);
        }

        if (startParameter.isShowVersion()) {
            System.out.println(new GradleVersion().prettyPrint());
            buildCompleter.exit(null);
        }

        if (startParameter.isLaunchGUI()) {
            try {
                BlockingApplication.launchAndBlock();
            } catch (Throwable e) {
                logger.error("Failed to run the UI.", e);
                buildCompleter.exit(e);
            }

            buildCompleter.exit(null);
        }

        BuildListener resultLogger = new BuildLogger(logger, buildTimeClock, startParameter);
        try {
            GradleLauncher gradleLauncher = GradleLauncher.newInstance(startParameter);

            gradleLauncher.useLogger(resultLogger);

            BuildResult buildResult = gradleLauncher.run();
            if (buildResult.getFailure() != null) {
                buildCompleter.exit(buildResult.getFailure());
            }
        } catch (Throwable e) {
            resultLogger.buildFinished(new BuildResult(null, e));
            buildCompleter.exit(e);
        }
        buildCompleter.exit(null);
    }

    private void showUsage(PrintStream out) {
        String appName = System.getProperty("org.gradle.appname", "gradle");
        out.println();
        out.print("USAGE: ");
        new GradleLauncherMetaData().describeCommand(out, "[option...]", "[task...]");
        out.println();
        out.println();
        parameterConverter.showHelp(out);
    }

    public interface BuildCompleter {
        void exit(Throwable failure);
    }

    private static class ProcessExitBuildCompleter implements BuildCompleter {
        public void exit(Throwable failure) {
            System.exit(failure == null ? 0 : 1);
        }
    }
}
