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
import org.gradle.internal.UncheckedException;
import org.gradle.internal.buildtree.BuildActionRunner;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry;
import org.gradle.internal.session.BuildSessionContext;
import org.gradle.internal.session.BuildSessionState;
import org.gradle.internal.session.CrossBuildSessionState;
import org.gradle.launcher.exec.BuildActionExecuter;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.BuildActionResult;
import org.gradle.launcher.exec.BuildExecuter;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;
import org.gradle.tooling.internal.provider.serialization.SerializedPayload;

import java.util.function.Function;

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

        ActionImpl actionWrapper = new ActionImpl(action, requestContext);
        try {
            try (CrossBuildSessionState crossBuildSessionState = new CrossBuildSessionState(globalServices, startParameter)) {
                try (BuildSessionState buildSessionState = new BuildSessionState(userHomeServiceRegistry, crossBuildSessionState, startParameter, requestContext, actionParameters.getInjectedPluginClasspath(), requestContext.getCancellationToken(), requestContext.getClient(), requestContext.getEventConsumer())) {
                    return buildSessionState.run(actionWrapper);
                }
            }
        } catch (Throwable t) {
            if (actionWrapper.result == null) {
                // Did not create a result
                // Note: throw the failure rather than returning a result object containing the failure, as console failure logging based on the _result_ happens down in the root build scope
                // whereas console failure logging based on the _thrown exception_ happens up outside session scope. It would be better to refactor so that a result can be returned from here
                throw UncheckedException.throwAsUncheckedException(t);
            } else {
                // Created a result which may contain failures. Combine this failure with any failures that happen to be packaged in the result
                // Note: throw the failure rather than returning a result object containing the failure, as console failure logging based on the _result_ happens down in the root build scope
                // whereas console failure logging based on the _thrown exception_ happens up outside session scope. It would be better to refactor so that a result can be returned from here
                throw UncheckedException.throwAsUncheckedException(actionWrapper.result.addFailure(t).getBuildFailure());
            }
        }
    }

    private static class ActionImpl implements Function<BuildSessionContext, BuildActionResult> {
        private final BuildAction action;
        private final BuildRequestContext requestContext;
        private BuildActionRunner.Result result;

        public ActionImpl(BuildAction action, BuildRequestContext requestContext) {
            this.action = action;
            this.requestContext = requestContext;
        }

        @Override
        public BuildActionResult apply(BuildSessionContext context) {
            result = context.execute(action);
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
        }
    }
}
