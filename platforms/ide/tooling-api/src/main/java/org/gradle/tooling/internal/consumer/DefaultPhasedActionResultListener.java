/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.tooling.internal.consumer;

import org.gradle.internal.Cast;
import org.gradle.tooling.IntermediateResultHandler;
import org.gradle.tooling.internal.protocol.PhasedActionResult;
import org.gradle.tooling.internal.protocol.PhasedActionResultListener;

import javax.annotation.Nullable;

/**
 * Adapts individual result handlers of actions in a {@link PhasedBuildAction} to a unified listener to be provided to the connection.
 */
public class DefaultPhasedActionResultListener implements PhasedActionResultListener {
    @Nullable private final IntermediateResultHandler<?> projectsLoadedHandler;
    @Nullable private final IntermediateResultHandler<?> buildFinishedHandler;

    public DefaultPhasedActionResultListener(@Nullable IntermediateResultHandler<?> projectsLoadedHandler,
                                             @Nullable IntermediateResultHandler<?> buildFinishedHandler) {
        this.projectsLoadedHandler = projectsLoadedHandler;
        this.buildFinishedHandler = buildFinishedHandler;
    }

    @Override
    public void onResult(PhasedActionResult<?> result) {
        Object model = result.getResult();
        PhasedActionResult.Phase type = result.getPhase();
        if (type == PhasedActionResult.Phase.PROJECTS_LOADED) {
            onComplete(model, projectsLoadedHandler);
        } else if (type == PhasedActionResult.Phase.BUILD_FINISHED) {
            onComplete(model, buildFinishedHandler);
        }
    }

    private <T> void onComplete(Object result, @Nullable IntermediateResultHandler<T> handler) {
        if (handler != null) {
            handler.onComplete(Cast.<T>uncheckedNonnullCast(result));
        }
    }
}
