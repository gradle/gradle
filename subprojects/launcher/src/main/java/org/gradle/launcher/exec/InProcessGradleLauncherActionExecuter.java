/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.launcher.exec;

import org.gradle.BuildResult;
import org.gradle.GradleLauncher;
import org.gradle.StartParameter;
import org.gradle.initialization.BuildController;
import org.gradle.initialization.GradleLauncherAction;
import org.gradle.initialization.GradleLauncherFactory;

public class InProcessGradleLauncherActionExecuter implements GradleLauncherActionExecuter<BuildActionParameters> {
    private final GradleLauncherFactory gradleLauncherFactory;

    public InProcessGradleLauncherActionExecuter(GradleLauncherFactory gradleLauncherFactory) {
        this.gradleLauncherFactory = gradleLauncherFactory;
    }

    public <T> T execute(GradleLauncherAction<T> action, BuildActionParameters actionParameters) {
        BuildResult buildResult = action.run(new DefaultBuildController(gradleLauncherFactory, actionParameters));
        Throwable failure = buildResult.getFailure();
        if (failure != null) {
            throw new ReportedException(failure);
        }
        return action.getResult();
    }

    private static class DefaultBuildController implements BuildController {
        private final BuildActionParameters actionParameters;
        private final GradleLauncherFactory gradleLauncherFactory;
        private GradleLauncher gradleLauncher;
        private StartParameter startParameter = new StartParameter();

        private DefaultBuildController(GradleLauncherFactory gradleLauncherFactory, BuildActionParameters actionParameters) {
            this.gradleLauncherFactory = gradleLauncherFactory;
            this.actionParameters = actionParameters;
        }

        public void setStartParameter(StartParameter startParameter) {
            if (gradleLauncher != null) {
                throw new IllegalStateException("Cannot change start parameter after launcher has been created.");
            }
            this.startParameter = startParameter;
        }

        public GradleLauncher getLauncher() {
            if (gradleLauncher == null) {
                gradleLauncher = gradleLauncherFactory.newInstance(startParameter, actionParameters.getBuildRequestMetaData());
            }
            return gradleLauncher;
        }
    }
}
