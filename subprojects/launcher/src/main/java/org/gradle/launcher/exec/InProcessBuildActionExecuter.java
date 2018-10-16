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

import org.gradle.api.Transformer;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.RootBuildState;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.invocation.BuildController;
import org.gradle.internal.operations.notify.BuildOperationNotificationValve;
import org.gradle.internal.service.ServiceRegistry;

public class InProcessBuildActionExecuter implements BuildActionExecuter<BuildActionParameters> {
    private final BuildActionRunner buildActionRunner;

    public InProcessBuildActionExecuter(BuildActionRunner buildActionRunner) {
        this.buildActionRunner = buildActionRunner;
    }

    public Object execute(final BuildAction action, BuildRequestContext buildRequestContext, BuildActionParameters actionParameters, ServiceRegistry contextServices) {
        BuildStateRegistry buildRegistry = contextServices.get(BuildStateRegistry.class);
        BuildOperationNotificationValve buildOperationNotificationValve = contextServices.get(BuildOperationNotificationValve.class);

        buildOperationNotificationValve.start();
        try {
            RootBuildState rootBuild = buildRegistry.addRootBuild(BuildDefinition.fromStartParameter(action.getStartParameter(), null), buildRequestContext);
            return rootBuild.run(new Transformer<Object, BuildController>() {
                @Override
                public Object transform(BuildController buildController) {
                    buildActionRunner.run(action, buildController);
                    return buildController.getResult();
                }
            });
        } finally {
            buildOperationNotificationValve.stop();
        }
    }
}
