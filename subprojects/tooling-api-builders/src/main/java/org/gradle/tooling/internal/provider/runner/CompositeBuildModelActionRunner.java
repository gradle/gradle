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
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.CompositeBuildContext;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.CompositeContextBuilder;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.CompositeScopeServices;
import org.gradle.api.logging.LogLevel;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.initialization.ReportedException;
import org.gradle.internal.Cast;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.composite.*;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.internal.service.scopes.BuildSessionScopeServices;
import org.gradle.launcher.daemon.configuration.DaemonUsage;
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
    public void run(BuildAction action, BuildRequestContext requestContext, CompositeBuildActionParameters actionParameters, CompositeBuildController buildController) {
        if (!(action instanceof BuildModelAction)) {
            return;
        }
        BuildModelAction buildModelAction = (BuildModelAction) action;
        List<Object> results = null;
        if (isModelRequest(buildModelAction)) {
            results = fetchCompositeModelsInProcess(buildModelAction, requestContext, actionParameters.getCompositeParameters(), buildController.getBuildScopeServices());
        } else {
            if (!buildModelAction.isRunTasks()) {
                throw new IllegalStateException("No tasks defined.");
            }
            executeTasksInProcess(buildModelAction, actionParameters, requestContext, buildController.getBuildScopeServices());
        }
        PayloadSerializer payloadSerializer = buildController.getBuildScopeServices().get(PayloadSerializer.class);
        buildController.setResult(new BuildActionResult(payloadSerializer.serialize(results), null));
    }

    private boolean isModelRequest(BuildModelAction action) {
        final String requestedModelName = action.getModelName();
        return !requestedModelName.equals(Void.class.getName());
    }

    private List<Object> fetchCompositeModelsInProcess(BuildModelAction modelAction, BuildRequestContext buildRequestContext,
                                                       CompositeParameters compositeParameters,
                                                       ServiceRegistry sharedServices) {

        DefaultServiceRegistry compositeServices = createCompositeAwareServices(modelAction, buildRequestContext, compositeParameters, sharedServices);

        BuildActionRunner runner = new SubscribableBuildActionRunner(new BuildModelsActionRunner());
        org.gradle.launcher.exec.BuildActionExecuter<BuildActionParameters> buildActionExecuter = new InProcessBuildActionExecuter(sharedServices.get(GradleLauncherFactory.class), runner);
        // TODO Need to consider how to handle builds in parallel when sharing event consumers/output streams

        final List<Object> results = Lists.newArrayList();
        for (GradleParticipantBuild participant : compositeParameters.getBuilds()) {
            DefaultBuildActionParameters actionParameters = new DefaultBuildActionParameters(Collections.EMPTY_MAP, Collections.<String, String>emptyMap(), participant.getProjectDir(), LogLevel.INFO, DaemonUsage.EXPLICITLY_DISABLED, false, true, ClassPath.EMPTY);

            StartParameter startParameter = modelAction.getStartParameter().newInstance();
            startParameter.setProjectDir(participant.getProjectDir());

            ServiceRegistry buildScopedServices = new BuildSessionScopeServices(compositeServices, startParameter, ClassPath.EMPTY);

            BuildModelAction participantAction = new BuildModelAction(startParameter, modelAction.getModelName(), false, modelAction.getClientSubscriptions());
            try {
                Map<String, Object> result = Cast.uncheckedCast(buildActionExecuter.execute(participantAction, buildRequestContext, actionParameters, buildScopedServices));
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

    private void executeTasksInProcess(BuildModelAction compositeAction, CompositeBuildActionParameters actionParameters, BuildRequestContext buildRequestContext, ServiceRegistry sharedServices) {
        GradleLauncherFactory gradleLauncherFactory = sharedServices.get(GradleLauncherFactory.class);
        CompositeParameters compositeParameters = actionParameters.getCompositeParameters();

        DefaultServiceRegistry compositeServices = createCompositeAwareServices(compositeAction, buildRequestContext, compositeParameters, sharedServices);

        StartParameter startParameter = compositeAction.getStartParameter().newInstance();
        startParameter.setProjectDir(compositeParameters.getTargetBuild().getProjectDir());
        startParameter.setSearchUpwards(false);
        startParameter.setSystemPropertiesArgs(Collections.singletonMap("org.gradle.resolution.assumeFluidDependencies", "true"));

        // Use a ModelActionRunner to ensure that model events are emitted
        BuildActionRunner runner = new SubscribableBuildActionRunner(new BuildModelActionRunner());
        org.gradle.launcher.exec.BuildActionExecuter<BuildActionParameters> buildActionExecuter = new InProcessBuildActionExecuter(gradleLauncherFactory, runner);
        BuildModelAction participantAction = new BuildModelAction(startParameter, ModelIdentifier.NULL_MODEL, true, compositeAction.getClientSubscriptions());
        ServiceRegistry buildScopedServices = new BuildSessionScopeServices(compositeServices, startParameter, ClassPath.EMPTY);

        buildActionExecuter.execute(participantAction, buildRequestContext, null, buildScopedServices);
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

    private DefaultServiceRegistry createCompositeAwareServices(BuildModelAction modelAction, BuildRequestContext buildRequestContext,
                                                                CompositeParameters compositeParameters, ServiceRegistry sharedServices) {
        boolean propagateFailures = ModelIdentifier.NULL_MODEL.equals(modelAction.getModelName());
        CompositeBuildContext context = constructCompositeContext(modelAction, buildRequestContext, compositeParameters, sharedServices, propagateFailures);

        DefaultServiceRegistry compositeServices = (DefaultServiceRegistry) ServiceRegistryBuilder.builder()
            .displayName("Composite services")
            .parent(sharedServices)
            .build();
        compositeServices.add(CompositeBuildContext.class, context);
        compositeServices.addProvider(new CompositeScopeServices(modelAction.getStartParameter(), compositeServices));
        return compositeServices;
    }

    private CompositeBuildContext constructCompositeContext(BuildModelAction modelAction, BuildRequestContext buildRequestContext,
                                                            CompositeParameters compositeParameters, ServiceRegistry sharedServices, boolean propagateFailures) {
        GradleLauncherFactory gradleLauncherFactory = sharedServices.get(GradleLauncherFactory.class);
        CompositeContextBuilder builder = new CompositeContextBuilder(propagateFailures);
        BuildActionExecuter<BuildActionParameters> buildActionExecuter = new InProcessBuildActionExecuter(gradleLauncherFactory, builder);

        for (GradleParticipantBuild participant : compositeParameters.getBuilds()) {
            StartParameter startParameter = modelAction.getStartParameter().newInstance();
            startParameter.setProjectDir(participant.getProjectDir());
            startParameter.setConfigureOnDemand(false);

            BuildModelAction configureAction = new BuildModelAction(startParameter, ModelIdentifier.NULL_MODEL, false, modelAction.getClientSubscriptions());
            buildActionExecuter.execute(configureAction, buildRequestContext, null, sharedServices);
        }
        return builder.build();
    }
}
