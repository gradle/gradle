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

import org.gradle.StartParameter;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.gradleplugin.userinterface.swing.standalone.BlockingApplication;
import org.gradle.initialization.CommandLine2StartParameterConverter;
import org.gradle.initialization.DefaultCommandLine2StartParameterConverter;
import org.gradle.util.GradleVersion;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class Main {
    private static final Logger LOGGER = Logging.getLogger(Main.class);

    private final String[] args;
    private BuildCompleter buildCompleter = new ProcessExitBuildCompleter();
    private CommandLine2StartParameterConverter parameterConverter = new DefaultCommandLine2StartParameterConverter();

    public Main(String[] args) {
        this.args = args;
    }

    public static void main(String[] args) throws Throwable {
        new Main(args).execute();
    }

    void setBuildCompleter(BuildCompleter buildCompleter) {
        this.buildCompleter = buildCompleter;
    }

    public void setParameterConverter(CommandLine2StartParameterConverter parameterConverter) {
        this.parameterConverter = parameterConverter;
    }

    public void execute() throws Exception {
        StartParameter startParameter = null;

        try {
            startParameter = parameterConverter.convert(args);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            parameterConverter.showHelp(System.err);
            buildCompleter.exit(e);
        }

        if (startParameter.isShowHelp()) {
            parameterConverter.showHelp(System.out);
            buildCompleter.exit(null);
        }

        if (startParameter.isShowVersion()) {
            System.out.println(new GradleVersion().prettyPrint());
            buildCompleter.exit(null);
        }

        try {
            if (startParameter.isLaunchGUI()) {
                BlockingApplication.launchAndBlock();
            } else if (startParameter.isForeground()) {
                GradleDaemon.main(args);
            } else if (startParameter.isNoDaemon()) {
                GradleDaemon.build(new File(System.getProperty("user.dir")), args);
            } else if (startParameter.isStopDaemon()) {
                GradleDaemon.stop();
            } else {
                GradleDaemon.clientMain(new File(System.getProperty("user.dir")), args);
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            buildCompleter.exit(throwable);
        }
        buildCompleter.exit(null);
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
