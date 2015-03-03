/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.initialization.BuildAction;
import org.gradle.initialization.BuildController;
import org.gradle.launcher.cli.ExecuteBuildAction;
import org.gradle.launcher.exec.BuildActionRunner;

public class DefaultBuildActionRunner implements BuildActionRunner {
    @Override
    public Object run(BuildAction action, BuildController buildController) {
        if (action instanceof ExecuteBuildAction) {
            buildController.run();
            return null;
        }
        if (action instanceof BuildModelAction) {
            BuildModelAction buildModelAction = (BuildModelAction) action;
            return buildModelAction.run(buildController);
        }
        if (action instanceof ClientProvidedBuildAction) {
            ClientProvidedBuildAction clientProvidedBuildAction = (ClientProvidedBuildAction) action;
            return clientProvidedBuildAction.run(buildController);
        }

        throw new UnsupportedOperationException(String.format("Don't know how to run a build action of type %s.", action.getClass().getSimpleName()));
    }
}
