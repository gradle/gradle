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

package org.gradle.tooling.internal.provider.runner;

import org.gradle.api.BuildCancelledException;
import org.gradle.api.internal.GradleInternal;
import org.gradle.execution.ProjectConfigurer;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.invocation.BuildController;
import org.gradle.tooling.internal.protocol.InternalBuildAction;
import org.gradle.tooling.internal.protocol.InternalBuildActionFailureException;
import org.gradle.tooling.internal.protocol.InternalBuildCancelledException;
import org.gradle.tooling.internal.protocol.InternalBuildController;
import org.gradle.tooling.internal.provider.BuildActionResult;
import org.gradle.tooling.internal.provider.ClientProvidedBuildAction;
import org.gradle.tooling.internal.provider.PayloadSerializer;

public class ClientProvidedBuildActionRunner implements BuildActionRunner {
    @Override
    public void run(BuildAction action, BuildController buildController) {
        if (!(action instanceof ClientProvidedBuildAction)) {
            return;
        }

        ClientProvidedBuildAction clientProvidedBuildAction = (ClientProvidedBuildAction) action;
        GradleInternal gradle = buildController.getGradle();
        PayloadSerializer payloadSerializer = gradle.getServices().get(PayloadSerializer.class);
        InternalBuildAction<?> clientAction = (InternalBuildAction<?>) payloadSerializer.deserialize(clientProvidedBuildAction.getAction());

        buildController.configure();
        // Currently need to force everything to be configured
        gradle.getServices().get(ProjectConfigurer.class).configureHierarchy(gradle.getRootProject());

        InternalBuildController internalBuildController = new DefaultBuildController(gradle);
        Object model = null;
        Throwable failure = null;
        try {
            model = clientAction.execute(internalBuildController);
        } catch (BuildCancelledException e) {
            failure = new InternalBuildCancelledException(e);
        } catch (RuntimeException e) {
            failure = new InternalBuildActionFailureException(e);
        }

        BuildActionResult buildActionResult;
        if (failure != null) {
            buildActionResult = new BuildActionResult(null, payloadSerializer.serialize(failure));
        } else {
            buildActionResult = new BuildActionResult(payloadSerializer.serialize(model), null);
        }
        buildController.setResult(buildActionResult);
    }
}
