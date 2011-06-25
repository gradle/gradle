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
package org.gradle.launcher;

import org.gradle.BuildResult;
import org.gradle.GradleLauncher;
import org.gradle.StartParameter;
import org.gradle.initialization.DefaultCommandLineConverter;
import org.gradle.initialization.GradleLauncherAction;
import org.gradle.initialization.ParsedCommandLine;

import java.io.File;
import java.io.Serializable;

public class ExecuteBuildAction implements GradleLauncherAction<Void>, InitializationAware, Serializable {
    private final File currentDir;
    private final ParsedCommandLine args;

    public ExecuteBuildAction(File currentDir, ParsedCommandLine args) {
        this.currentDir = currentDir;
        this.args = args;
    }

    public void configureStartParameter(StartParameter startParameter) {
        DefaultCommandLineConverter converter = new DefaultCommandLineConverter();
        startParameter.setCurrentDir(currentDir);
        converter.convert(args, startParameter);
    }

    public BuildResult run(GradleLauncher launcher) {
        return launcher.run();
    }

    public Void getResult() {
        return null;
    }
}
