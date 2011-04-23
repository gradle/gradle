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
package org.gradle.tooling.internal.provider;

import org.gradle.BuildResult;
import org.gradle.GradleLauncher;
import org.gradle.StartParameter;
import org.gradle.initialization.GradleLauncherAction;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.launcher.GradleLauncherActionExecuter;
import org.gradle.launcher.InitializationAware;
import org.gradle.logging.internal.*;
import org.gradle.tooling.internal.protocol.BuildExceptionVersion1;
import org.gradle.tooling.internal.protocol.BuildOperationParametersVersion1;
import org.gradle.tooling.internal.protocol.ProgressListenerVersion1;

/**
 * A {@link GradleLauncherActionExecuter} which executes an action locally.
 */
public class LocalGradleLauncherActionExecuter implements GradleLauncherActionExecuter<BuildOperationParametersVersion1> {
    private final GradleLauncherFactory gradleLauncherFactory;
    private final LoggingOutputInternal loggingOutput;

    public LocalGradleLauncherActionExecuter(GradleLauncherFactory gradleLauncherFactory, LoggingOutputInternal loggingOutput) {
        this.gradleLauncherFactory = gradleLauncherFactory;
        this.loggingOutput = loggingOutput;
    }

    public <T> T execute(GradleLauncherAction<T> action, BuildOperationParametersVersion1 actionParameters) {
        StartParameter startParameter = new ConnectionToStartParametersConverter().convert(actionParameters);
        if (action instanceof InitializationAware) {
            InitializationAware initializationAware = (InitializationAware) action;
            initializationAware.configureStartParameter(startParameter);
        }

        GradleLauncher gradleLauncher = gradleLauncherFactory.newInstance(startParameter);

        if (actionParameters.getStandardOutput() != null) {
            gradleLauncher.addStandardOutputListener(new StreamBackedStandardOutputListener(actionParameters.getStandardOutput()));
        }
        if (actionParameters.getStandardError() != null) {
            gradleLauncher.addStandardErrorListener(new StreamBackedStandardOutputListener(actionParameters.getStandardError()));
        }
        ProgressListenerVersion1 progressListener = actionParameters.getProgressListener();
        OutputEventListenerAdapter listener = new OutputEventListenerAdapter(progressListener);
        loggingOutput.addOutputEventListener(listener);
        try {
            BuildResult result = action.run(gradleLauncher);
            if (result.getFailure() != null) {
                throw new BuildExceptionVersion1(result.getFailure());
            }
        } finally {
            loggingOutput.removeOutputEventListener(listener);
        }

        return action.getResult();
    }

    private static class OutputEventListenerAdapter implements OutputEventListener {
        private final ProgressListenerVersion1 progressListener;

        public OutputEventListenerAdapter(ProgressListenerVersion1 progressListener) {
            this.progressListener = progressListener;
        }

        public void onOutput(OutputEvent event) {
            if (event instanceof ProgressStartEvent) {
                ProgressStartEvent startEvent = (ProgressStartEvent) event;
                progressListener.onOperationStart(startEvent.getDescription());
            } else if (event instanceof ProgressCompleteEvent) {
                progressListener.onOperationEnd();
            }
        }
    }
}
