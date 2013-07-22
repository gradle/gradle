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
package org.gradle.openapi.wrappers.runner;

import org.gradle.gradleplugin.foundation.runner.GradleRunner;
import org.gradle.openapi.external.runner.GradleRunnerInteractionVersion1;
import org.gradle.openapi.external.runner.GradleRunnerVersion1;

import java.io.File;

/**
 * Wrapper to shield version changes in GradleRunner from an external user of gradle open API.
 */
public class GradleRunnerWrapper implements GradleRunnerVersion1 {
    private GradleRunner gradleRunner;
    private File gradleHomeDirectory;
    private File workingDirectory;
    private GradleRunnerInteractionWrapper interactionWrapper;

    public GradleRunnerWrapper(File gradleHomeDirectory, GradleRunnerInteractionVersion1 interactionVersion1) {
        this.gradleHomeDirectory = gradleHomeDirectory;
        this.workingDirectory = interactionVersion1.getWorkingDirectory();
        interactionWrapper = new GradleRunnerInteractionWrapper(interactionVersion1);
        File customGradleExecutable = interactionVersion1.getCustomGradleExecutable();

        gradleRunner = new GradleRunner(workingDirectory, gradleHomeDirectory, customGradleExecutable);
    }

    public void executeCommand(String commandLine) {
        gradleRunner.executeCommand(commandLine, interactionWrapper.getLogLevel(), interactionWrapper.getStackTraceLevel(), interactionWrapper);
    }

    /*
       Call this to stop the gradle command. This is killing the process, not
       gracefully exiting.
    */

    public void killProcess() {
        gradleRunner.killProcess();
    }
}
