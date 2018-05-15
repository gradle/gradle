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

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.IntermediateResultHandler;

import javax.annotation.Nullable;

public class DefaultPhasedBuildAction implements PhasedBuildAction {
    @Nullable private final BuildActionWrapper<?> projectsLoadedAction;
    @Nullable private final BuildActionWrapper<?> buildFinishedAction;

    DefaultPhasedBuildAction(@Nullable BuildActionWrapper<?> projectsLoadedAction,
                             @Nullable BuildActionWrapper<?> buildFinishedAction) {
        this.projectsLoadedAction = projectsLoadedAction;
        this.buildFinishedAction = buildFinishedAction;
    }

    @Nullable
    @Override
    public BuildActionWrapper<?> getProjectsLoadedAction() {
        return projectsLoadedAction;
    }

    @Nullable
    @Override
    public BuildActionWrapper<?> getBuildFinishedAction() {
        return buildFinishedAction;
    }

    static class DefaultBuildActionWrapper<T> implements BuildActionWrapper<T> {
        private final BuildAction<T> buildAction;
        private final IntermediateResultHandler<? super T> resultHandler;

        DefaultBuildActionWrapper(BuildAction<T> buildAction, IntermediateResultHandler<? super T> resultHandler) {
            this.buildAction = buildAction;
            this.resultHandler = resultHandler;
        }

        @Override
        public BuildAction<T> getAction() {
            return buildAction;
        }

        @Override
        public IntermediateResultHandler<? super T> getHandler() {
            return resultHandler;
        }
    }
}
