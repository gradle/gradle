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

package org.gradle.tooling.internal.provider;

import org.gradle.api.internal.StartParameterInternal;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.internal.buildtree.BuildActionRunner;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry;
import org.gradle.internal.session.BuildSessionState;
import org.gradle.internal.session.CrossBuildSessionState;
import org.gradle.launcher.exec.BuildActionExecuter;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.BuildActionResult;
import org.gradle.launcher.exec.BuildExecuter;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;
import org.gradle.tooling.internal.provider.serialization.SerializedPayload;

/**
 * A {@link BuildExecuter} responsible for establishing the {@link BuildSessionState} to execute a {@link BuildAction} within.
 */
public class BuildSessionLifecycleBuildActionExecuter implements BuildActionExecuter<BuildActionParameters, BuildRequestContext> {
    private final ServiceRegistry globalServices;
    private final GradleUserHomeScopeServiceRegistry userHomeServiceRegistry;

    public BuildSessionLifecycleBuildActionExecuter(GradleUserHomeScopeServiceRegistry userHomeServiceRegistry, ServiceRegistry globalServices) {
        this.userHomeServiceRegistry = userHomeServiceRegistry;
        this.globalServices = globalServices;
    }

    @Override
    public BuildActionResult execute(BuildAction action, BuildActionParameters actionParameters, BuildRequestContext requestContext) {
        StartParameterInternal startParameter = action.getStartParameter();
        if (action.isCreateModel()) {
            // When creating a model, do not use continuous mode
            startParameter.setContinuous(false);
        }
        try (CrossBuildSessionState crossBuildSessionState = new CrossBuildSessionState(globalServices, startParameter)) {
            try (BuildSessionState buildSessionState = new BuildSessionState(userHomeServiceRegistry, crossBuildSessionState, startParameter, requestContext, actionParameters.getInjectedPluginClasspath(), requestContext.getCancellationToken(), requestContext.getClient(), requestContext.getEventConsumer())) {
                return buildSessionState.run(context -> {
                    BuildActionRunner.Result result = context.execute(action);
                    PayloadSerializer payloadSerializer = context.getServices().get(PayloadSerializer.class);
                    if (result.getBuildFailure() == null) {
                        if (result.getClientResult() instanceof SerializedPayload) {
                            // Already serialized
                            return BuildActionResult.of((SerializedPayload) result.getClientResult());
                        } else {
                            return BuildActionResult.of(payloadSerializer.serialize(result.getClientResult()));
                        }
                    }
                    if (requestContext.getCancellationToken().isCancellationRequested()) {
                        return BuildActionResult.cancelled(payloadSerializer.serialize(result.getBuildFailure()));
                    }
                    return BuildActionResult.failed(payloadSerializer.serialize(result.getClientFailure()));
                });
            }
        }
    }
}
