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

import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.BuildCancelledException;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.internal.GradleInternal;
import org.gradle.composite.internal.IncludedBuildInternal;
import org.gradle.execution.ProjectConfigurer;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.invocation.BuildController;
import org.gradle.tooling.internal.protocol.BuildExceptionVersion1;
import org.gradle.tooling.internal.protocol.InternalBuildAction;
import org.gradle.tooling.internal.protocol.InternalBuildActionFailureException;
import org.gradle.tooling.internal.protocol.InternalBuildCancelledException;
import org.gradle.tooling.internal.protocol.InternalBuildController;
import org.gradle.tooling.internal.provider.BuildActionResult;
import org.gradle.tooling.internal.provider.ClientProvidedBuildAction;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;

public class ClientProvidedBuildActionRunner implements BuildActionRunner {
    @Override
    public void run(BuildAction action, final BuildController buildController) {
        if (!(action instanceof ClientProvidedBuildAction)) {
            return;
        }

        final GradleInternal gradle = buildController.getGradle();

        ClientProvidedBuildAction clientProvidedBuildAction = (ClientProvidedBuildAction) action;
        PayloadSerializer payloadSerializer = getPayloadSerializer(gradle);
        final InternalBuildAction<?> clientAction = (InternalBuildAction<?>) payloadSerializer.deserialize(clientProvidedBuildAction.getAction());

        gradle.addBuildListener(new BuildAdapter() {
            @Override
            public void buildFinished(BuildResult result) {
                if (result.getFailure() == null) {
                    buildController.setResult(buildResult(clientAction, gradle));
                }
            }
        });

        buildController.configure();
    }

    private BuildActionResult buildResult(InternalBuildAction<?> clientAction, GradleInternal gradle) {
        forceFullConfiguration(gradle);

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

        PayloadSerializer payloadSerializer = getPayloadSerializer(gradle);
        if (failure != null) {
            return new BuildActionResult(null, payloadSerializer.serialize(failure));
        } else {
            return new BuildActionResult(payloadSerializer.serialize(model), null);
        }
    }

    private void forceFullConfiguration(GradleInternal gradle) {
        try {
            gradle.getServices().get(ProjectConfigurer.class).configureHierarchyFully(gradle.getRootProject());
            for (IncludedBuild includedBuild : gradle.getIncludedBuilds()) {
                GradleInternal build = ((IncludedBuildInternal) includedBuild).getConfiguredBuild();
                forceFullConfiguration(build);
            }
        } catch (BuildCancelledException e) {
            throw new InternalBuildCancelledException(e);
        } catch (RuntimeException e) {
            throw new BuildExceptionVersion1(e);
        }
    }

    private PayloadSerializer getPayloadSerializer(GradleInternal gradle) {
        return gradle.getServices().get(PayloadSerializer.class);
    }

}
