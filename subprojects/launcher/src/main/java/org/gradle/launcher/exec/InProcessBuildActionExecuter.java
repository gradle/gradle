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
import org.gradle.initialization.BuildAction;
import org.gradle.initialization.GradleLauncherFactory;

public class InProcessBuildActionExecuter implements BuildActionExecuter<BuildActionParameters> {
    private final GradleLauncherFactory gradleLauncherFactory;

    public InProcessBuildActionExecuter(GradleLauncherFactory gradleLauncherFactory) {
        this.gradleLauncherFactory = gradleLauncherFactory;
    }

    public <T> T execute(BuildAction<T> action, BuildActionParameters actionParameters) {
        DefaultBuildController buildController = new DefaultBuildController(gradleLauncherFactory, actionParameters);
        return action.run(buildController);
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

        public void run() {
            check(getLauncher().run());
        }

        public void configure() {
            check(getLauncher().getBuildAnalysis());
        }

        private void check(BuildResult buildResult) {
            if (buildResult.getFailure() != null) {
                throw new ReportedException(buildResult.getFailure());
            }
        }
    }
}
