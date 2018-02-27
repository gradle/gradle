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

package org.gradle.tooling.internal.consumer.connection;

import org.gradle.tooling.internal.protocol.InternalBuildActionVersion2;
import org.gradle.tooling.internal.protocol.InternalPhasedAction;

import javax.annotation.Nullable;

public class InternalPhasedActionAdapter implements InternalPhasedAction {
    @Nullable private final InternalBuildActionVersion2<?> afterLoadingAction;
    @Nullable private final InternalBuildActionVersion2<?> afterConfigurationAction;
    @Nullable private final InternalBuildActionVersion2<?> afterBuildAction;

    InternalPhasedActionAdapter(@Nullable InternalBuildActionVersion2<?> afterLoadingAction,
                                @Nullable InternalBuildActionVersion2<?> afterConfigurationAction,
                                @Nullable InternalBuildActionVersion2<?> afterBuildAction) {
        this.afterLoadingAction = afterLoadingAction;
        this.afterConfigurationAction = afterConfigurationAction;
        this.afterBuildAction = afterBuildAction;
    }

    @Nullable
    @Override
    public InternalBuildActionVersion2<?> getAfterLoadingAction() {
        return afterLoadingAction;
    }

    @Nullable
    @Override
    public InternalBuildActionVersion2<?> getAfterConfigurationAction() {
        return afterConfigurationAction;
    }

    @Nullable
    @Override
    public InternalBuildActionVersion2<?> getAfterBuildAction() {
        return afterBuildAction;
    }
}
