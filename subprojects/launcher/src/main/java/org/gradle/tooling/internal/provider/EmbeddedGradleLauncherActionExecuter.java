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
import org.gradle.tooling.internal.protocol.BuildExceptionVersion1;
import org.gradle.tooling.internal.protocol.BuildOperationParametersVersion1;

/**
 * A {@link GradleLauncherActionExecuter} which executes an action locally.
 */
public class EmbeddedGradleLauncherActionExecuter implements GradleLauncherActionExecuter<BuildOperationParametersVersion1> {
    private final GradleLauncherFactory gradleLauncherFactory;

    public EmbeddedGradleLauncherActionExecuter(GradleLauncherFactory gradleLauncherFactory) {
        this.gradleLauncherFactory = gradleLauncherFactory;
    }

    public <T> T execute(GradleLauncherAction<T> action, BuildOperationParametersVersion1 actionParameters) {
        StartParameter startParameter = new StartParameter();
        if (action instanceof InitializationAware) {
            InitializationAware initializationAware = (InitializationAware) action;
            initializationAware.configureStartParameter(startParameter);
        }
        GradleLauncher gradleLauncher = gradleLauncherFactory.newInstance(startParameter);
        BuildResult result = action.run(gradleLauncher);
        if (result.getFailure() != null) {
            throw new BuildExceptionVersion1(result.getFailure());
        }
        return action.getResult();
    }
}
