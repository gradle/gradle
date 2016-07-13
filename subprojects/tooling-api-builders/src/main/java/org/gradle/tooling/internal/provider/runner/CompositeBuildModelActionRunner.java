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

import com.google.common.collect.Lists;
import org.gradle.StartParameter;
import org.gradle.api.BuildCancelledException;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.initialization.ReportedException;
import org.gradle.internal.Cast;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.composite.CompositeBuildActionParameters;
import org.gradle.internal.composite.CompositeBuildActionRunner;
import org.gradle.internal.composite.CompositeBuildController;
import org.gradle.internal.composite.CompositeContextBuilder;
import org.gradle.internal.composite.CompositeParameters;
import org.gradle.internal.composite.GradleParticipantBuild;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.exec.BuildActionExecuter;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.DefaultBuildActionParameters;
import org.gradle.launcher.exec.InProcessBuildActionExecuter;
import org.gradle.tooling.internal.protocol.BuildExceptionVersion1;
import org.gradle.tooling.internal.protocol.InternalBuildCancelledException;
import org.gradle.tooling.internal.protocol.ModelIdentifier;
import org.gradle.tooling.internal.provider.BuildActionResult;
import org.gradle.tooling.internal.provider.BuildModelAction;
import org.gradle.tooling.internal.provider.PayloadSerializer;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CompositeBuildModelActionRunner implements CompositeBuildActionRunner {
    private static final org.gradle.api.logging.Logger LOGGER = Logging.getLogger(CompositeBuildModelActionRunner.class);

    public void run(BuildAction action, BuildRequestContext requestContext, CompositeBuildActionParameters actionParameters, CompositeBuildController buildController) {
        if (!(action instanceof BuildModelAction)) {
            return;
        }
        BuildModelAction buildModelAction = (BuildModelAction) action;
        CompositeParameters compositeParameters = actionParameters.getCompositeParameters();

//        BuildSessionScopeServices compositeServices = new BuildSessionScopeServices(buildController.getBuildScopeServices(), buildModelAction.getStartParameter(), ClassPath.EMPTY);
//        compositeServices.addProvider(new CompositeBuildServices.CompositeBuildSessionScopeServices());
        ServiceRegistry compositeServices = buildController.getBuildScopeServices();

        List<Object> results = null;
        if (isModelRequest(buildModelAction)) {
            results = fetchCompositeModelsInProcess(buildModelAction, requestContext, compositeParameters, compositeServices);
        } else {
            if (!buildModelAction.isRunTasks()) {
                throw new IllegalStateException("No tasks defined.");
            }
            executeTasksInProcess(buildModelAction, compositeParameters, requestContext, compositeServices);
        }
        PayloadSerializer payloadSerializer = buildController.getBuildScopeServices().get(PayloadSerializer.class);
        buildController.setResult(new BuildActionResult(payloadSerializer.serialize(results), null));
    }

    private boolean isModelRequest(BuildModelAction action) {
        final String requestedModelName = action.getModelName();
        return !requestedModelName.equals(Void.class.getName());
    }

    private List<Object> fetchCompositeModelsInProcess(BuildModelAction modelAction, BuildRequestContext buildRequestContext,
                                                       CompositeParameters compositeParameters, ServiceRegistry sharedServices) {

        boolean propagateFailures = ModelIdentifier.NULL_MODEL.equals(modelAction.getModelName());
        registerParticipantsInContext(compositeParameters, propagateFailures, buildRequestContext, sharedServices);

        BuildActionRunner runner = new SubscribableBuildActionRunner(new BuildModelsActionRunner());
        BuildActionExecuter<BuildActionParameters> buildActionExecuter = new InProcessBuildActionExecuter(sharedServices.get(GradleLauncherFactory.class), runner);
        // TODO Need to consider how to handle builds in parallel when sharing event consumers/output streams

        final List<Object> results = Lists.newArrayList();
        for (GradleParticipantBuild participant : compositeParameters.getBuilds()) {
            DefaultBuildActionParameters actionParameters = new DefaultBuildActionParameters(Collections.EMPTY_MAP, Collections.<String, String>emptyMap(), participant.getProjectDir(), LogLevel.INFO, false, false, true, ClassPath.EMPTY);

            StartParameter startParameter = modelAction.getStartParameter().newInstance();
            startParameter.setProjectDir(participant.getProjectDir());

            BuildModelAction participantAction = new BuildModelAction(startParameter, modelAction.getModelName(), false, modelAction.getClientSubscriptions());
            try {
                Map<String, Object> result = Cast.uncheckedCast(buildActionExecuter.execute(participantAction, buildRequestContext, actionParameters, sharedServices));
                for (Map.Entry<String, Object> e : result.entrySet()) {
                    String projectPath = e.getKey();
                    Object modelValue = e.getValue();
                    results.add(compositeModelResult(participant, projectPath, modelValue));
                }
            } catch (BuildCancelledException e) {
                InternalBuildCancelledException buildCancelledException = new InternalBuildCancelledException(e.getCause());
                results.add(compositeModelResult(participant, null, buildCancelledException));
            } catch (ReportedException e) {
                results.add(compositeModelResult(participant, null, unwrap(e)));
            } catch (Exception e) {
                results.add(compositeModelResult(participant, null, e));
            }
        }
        return results;
    }

    private Exception unwrap(ReportedException e) {
        Throwable t = e.getCause();
        while (t != null) {
            if (t instanceof BuildCancelledException) {
                return new InternalBuildCancelledException(e.getCause());
            }
            t = t.getCause();
        }
        return new BuildExceptionVersion1(e.getCause());
    }

    private Object compositeModelResult(GradleParticipantBuild participant, String projectPath, Object modelValue) {
        return new Object[]{participant.getProjectDir(), projectPath, modelValue};
    }

    private void executeTasksInProcess(BuildModelAction compositeAction, CompositeParameters compositeParameters, BuildRequestContext buildRequestContext, ServiceRegistry sharedServices) {
        registerParticipantsInContext(compositeParameters, true, buildRequestContext, sharedServices);

        StartParameter startParameter = compositeAction.getStartParameter().newInstance();
        GradleParticipantBuild targetParticipant = compositeParameters.getTargetBuild();
        startParameter.setProjectDir(targetParticipant.getProjectDir());
        startParameter.setSearchUpwards(false);

        LOGGER.lifecycle("[composite-build] Executing tasks " + startParameter.getTaskNames() + " for participant: " + targetParticipant.getProjectDir());

        // Use a ModelActionRunner to ensure that model events are emitted
        BuildActionRunner runner = new SubscribableBuildActionRunner(new BuildModelActionRunner());
        GradleLauncherFactory gradleLauncherFactory = sharedServices.get(GradleLauncherFactory.class);
        BuildActionExecuter<BuildActionParameters> buildActionExecuter = new InProcessBuildActionExecuter(gradleLauncherFactory, runner);
        BuildModelAction participantAction = new BuildModelAction(startParameter, ModelIdentifier.NULL_MODEL, true, compositeAction.getClientSubscriptions());

        buildActionExecuter.execute(participantAction, buildRequestContext, null, sharedServices);
    }

    private void registerParticipantsInContext(CompositeParameters compositeParameters, boolean propagateFailures, BuildRequestContext buildRequestContext, ServiceRegistry sharedServices) {
        CompositeContextBuilder contextBuilder = sharedServices.get(CompositeContextBuilder.class);
        contextBuilder.addToCompositeContext(compositeParameters.getBuilds(), buildRequestContext, propagateFailures);
    }
}
