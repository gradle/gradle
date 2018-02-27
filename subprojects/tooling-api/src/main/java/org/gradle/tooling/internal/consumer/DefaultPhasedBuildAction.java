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
import org.gradle.tooling.ResultHandler;

import javax.annotation.Nullable;

public class DefaultPhasedBuildAction implements PhasedBuildAction {
    @Nullable private final BuildActionWrapper<?> afterLoadingAction;
    @Nullable private final BuildActionWrapper<?> afterConfigurationAction;
    @Nullable private final BuildActionWrapper<?> afterBuildAction;

    DefaultPhasedBuildAction(@Nullable BuildActionWrapper<?> afterLoadingAction,
                             @Nullable BuildActionWrapper<?> afterConfigurationAction,
                             @Nullable BuildActionWrapper<?> afterBuildAction) {
        this.afterLoadingAction = afterLoadingAction;
        this.afterConfigurationAction = afterConfigurationAction;
        this.afterBuildAction = afterBuildAction;
    }

    @Nullable
    @Override
    public BuildActionWrapper<?> getAfterLoadingAction() {
        return afterLoadingAction;
    }

    @Nullable
    @Override
    public BuildActionWrapper<?> getAfterConfigurationAction() {
        return afterConfigurationAction;
    }

    @Nullable
    @Override
    public BuildActionWrapper<?> getAfterBuildAction() {
        return afterBuildAction;
    }

    static class DefaultBuildActionWrapper<T> implements BuildActionWrapper<T> {
        private final BuildAction<T> buildAction;
        private final ResultHandler<? super T> resultHandler;

        DefaultBuildActionWrapper(BuildAction<T> buildAction, ResultHandler<? super T> resultHandler) {
            this.buildAction = buildAction;
            this.resultHandler = resultHandler;
        }

        @Override
        public BuildAction<T> getAction() {
            return buildAction;
        }

        @Override
        public ResultHandler<? super T> getHandler() {
            return resultHandler;
        }
    }
}
