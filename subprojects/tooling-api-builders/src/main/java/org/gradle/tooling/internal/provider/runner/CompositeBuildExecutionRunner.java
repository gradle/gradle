/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.tooling.internal.provider.runner;

import org.gradle.initialization.BuildRequestContext;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.composite.CompositeBuildActionParameters;
import org.gradle.internal.composite.CompositeBuildActionRunner;
import org.gradle.internal.composite.CompositeBuildController;
import org.gradle.internal.composite.GradleParticipantBuild;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.BuildSessionScopeServices;
import org.gradle.launcher.cli.ExecuteBuildAction;
import org.gradle.launcher.exec.BuildActionExecuter;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.InProcessBuildActionExecuter;
import org.gradle.tooling.internal.provider.ExecuteBuildActionRunner;

import java.util.List;

public class CompositeBuildExecutionRunner implements CompositeBuildActionRunner {
    public void run(BuildAction action, BuildRequestContext requestContext, CompositeBuildActionParameters actionParameters, CompositeBuildController buildController) {
        if (!(action instanceof ExecuteBuildAction)) {
            return;
        }
        BuildSessionScopeServices compositeServices = new BuildSessionScopeServices(buildController.getBuildScopeServices(), action.getStartParameter(), ClassPath.EMPTY);
        compositeServices.addProvider(new CompositeBuildServices.CompositeBuildSessionScopeServices());

        executeTasksInProcess((ExecuteBuildAction) action, actionParameters.getCompositeParameters().getBuilds(), requestContext, compositeServices);
        buildController.setResult(null);
    }

    private void executeTasksInProcess(ExecuteBuildAction action, List<GradleParticipantBuild> participantBuilds, BuildRequestContext buildRequestContext, ServiceRegistry sharedServices) {
        DefaultCompositeContextBuilder contextBuilder = new DefaultCompositeContextBuilder(action.getStartParameter(), buildRequestContext, sharedServices, true);
        contextBuilder.addToCompositeContext(participantBuilds);

        BuildActionRunner runner = new ExecuteBuildActionRunner();
        GradleLauncherFactory gradleLauncherFactory = sharedServices.get(GradleLauncherFactory.class);
        BuildActionExecuter<BuildActionParameters> buildActionExecuter = new InProcessBuildActionExecuter(gradleLauncherFactory, runner);
        buildActionExecuter.execute(action, buildRequestContext, null, sharedServices);
    }
}
