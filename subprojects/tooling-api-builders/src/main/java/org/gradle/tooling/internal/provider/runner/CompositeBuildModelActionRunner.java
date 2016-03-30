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

import org.gradle.StartParameter;
import org.gradle.api.logging.LogLevel;
import org.gradle.configuration.GradleLauncherMetaData;
import org.gradle.initialization.*;
import org.gradle.internal.Cast;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.composite.*;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.BuildSessionScopeServices;
import org.gradle.launcher.daemon.configuration.DaemonUsage;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.DefaultBuildActionParameters;
import org.gradle.launcher.exec.InProcessBuildActionExecuter;
import org.gradle.tooling.internal.connection.DefaultBuildIdentifier;
import org.gradle.tooling.internal.connection.DefaultProjectIdentifier;
import org.gradle.tooling.internal.provider.BuildActionResult;
import org.gradle.tooling.internal.provider.BuildModelAction;
import org.gradle.tooling.internal.provider.PayloadSerializer;
import org.gradle.tooling.model.BuildIdentifier;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CompositeBuildModelActionRunner implements CompositeBuildActionRunner {
    public void run(BuildAction action, BuildRequestContext requestContext, CompositeBuildActionParameters actionParameters, CompositeBuildController buildController) {
        if (!(action instanceof BuildModelAction)) {
            return;
        }
        BuildModelAction buildModelAction = (BuildModelAction) action;
        Map<Object, Object> results = null;
        if (isModelRequest(buildModelAction)) {
            results = fetchCompositeModelsInProcess(buildModelAction, requestContext, actionParameters.getCompositeParameters().getBuilds(), buildController.getBuildScopeServices());
        } else {
            if (!buildModelAction.isRunTasks()) {
                throw new IllegalStateException("No tasks defined.");
            }
            executeTasksInProcess(action.getStartParameter(), actionParameters, requestContext, buildController.getBuildScopeServices());
        }
        PayloadSerializer payloadSerializer = buildController.getBuildScopeServices().get(PayloadSerializer.class);
        buildController.setResult(new BuildActionResult(payloadSerializer.serialize(results), null));
    }

    private void executeTasksInProcess(StartParameter parentStartParam, CompositeBuildActionParameters actionParameters, BuildRequestContext buildRequestContext, ServiceRegistry sharedServices) {
        CompositeParameters compositeParameters = actionParameters.getCompositeParameters();
        GradleLauncherFactory launcherFactory = sharedServices.get(GradleLauncherFactory.class);

        GradleParticipantBuild participant = compositeParameters.getTargetBuild();
        StartParameter startParameter = parentStartParam.newInstance();
        startParameter.setProjectDir(participant.getProjectDir());
        startParameter.setSearchUpwards(false);

        ServiceRegistry buildScopedServices = new BuildSessionScopeServices(sharedServices, startParameter, ClassPath.EMPTY);

        DefaultBuildRequestContext requestContext = new DefaultBuildRequestContext(new DefaultBuildRequestMetaData(new GradleLauncherMetaData(), System.currentTimeMillis()), buildRequestContext.getCancellationToken(), buildRequestContext.getEventConsumer(), buildRequestContext.getOutputListener(), buildRequestContext.getErrorListener());
        GradleLauncher launcher = launcherFactory.newInstance(startParameter, requestContext, buildScopedServices);

        try {
            launcher.run();
        } finally {
            launcher.stop();
        }
    }

    private boolean isModelRequest(BuildModelAction action) {
        final String requestedModelName = action.getModelName();
        return !requestedModelName.equals(Void.class.getName());
    }

    private Map<Object, Object> fetchCompositeModelsInProcess(BuildModelAction modelAction, BuildRequestContext buildRequestContext,
                                                              List<GradleParticipantBuild> participantBuilds,
                                                              ServiceRegistry sharedServices) {
        final Map<Object, Object> results = new HashMap<Object, Object>();
        GradleLauncherFactory gradleLauncherFactory = sharedServices.get(GradleLauncherFactory.class);

        BuildActionRunner runner = new SubscribableBuildActionRunner(new BuildModelsActionRunner());
        org.gradle.launcher.exec.BuildActionExecuter<BuildActionParameters> buildActionExecuter = new InProcessBuildActionExecuter(gradleLauncherFactory, runner);
        // TODO Need to consider how to handle builds in parallel when sharing event consumers/output streams
        DefaultBuildRequestContext requestContext = new DefaultBuildRequestContext(new DefaultBuildRequestMetaData(System.currentTimeMillis()),
            buildRequestContext.getCancellationToken(), buildRequestContext.getEventConsumer(), buildRequestContext.getOutputListener(),
            buildRequestContext.getErrorListener());

        for (GradleParticipantBuild participant : participantBuilds) {
            DefaultBuildActionParameters actionParameters = new DefaultBuildActionParameters(Collections.EMPTY_MAP, Collections.<String, String>emptyMap(), participant.getProjectDir(), LogLevel.INFO, DaemonUsage.EXPLICITLY_DISABLED, false, true, ClassPath.EMPTY);

            StartParameter startParameter = modelAction.getStartParameter().newInstance();
            startParameter.setProjectDir(participant.getProjectDir());

            ServiceRegistry buildScopedServices = new BuildSessionScopeServices(sharedServices, startParameter, ClassPath.EMPTY);

            BuildModelAction participantAction = new BuildModelAction(startParameter, modelAction.getModelName(), false, modelAction.getClientSubscriptions());
            try {
                Map<String, Object> result = Cast.uncheckedCast(buildActionExecuter.execute(participantAction, requestContext, actionParameters, buildScopedServices));
                for (Map.Entry<String, Object> e : result.entrySet()) {
                    DefaultProjectIdentifier projectIdentifier = new DefaultProjectIdentifier(participant.getProjectDir(), e.getKey());
                    Object modelValue = e.getValue();
                    results.put(projectIdentifier, modelValue);
                }
            } catch (Exception e) {
                File rootDir = participant.getProjectDir();
                BuildIdentifier buildIdentifier = new DefaultBuildIdentifier(rootDir);
                results.put(new DefaultProjectIdentifier(buildIdentifier, ":"), e);
            }
        }
        return results;
    }
}
