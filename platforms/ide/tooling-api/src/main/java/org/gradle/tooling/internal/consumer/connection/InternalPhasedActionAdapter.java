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
import org.jspecify.annotations.Nullable;

public class InternalPhasedActionAdapter implements InternalPhasedAction {
    @Nullable private final InternalBuildActionVersion2<?> projectsLoadedAction;
    @Nullable private final InternalBuildActionVersion2<?> buildFinishedAction;

    InternalPhasedActionAdapter(@Nullable InternalBuildActionVersion2<?> projectsLoadedAction,
                                @Nullable InternalBuildActionVersion2<?> buildFinishedAction) {
        this.projectsLoadedAction = projectsLoadedAction;
        this.buildFinishedAction = buildFinishedAction;
    }

    @Nullable
    @Override
    public InternalBuildActionVersion2<?> getProjectsLoadedAction() {
        return projectsLoadedAction;
    }

    @Nullable
    @Override
    public InternalBuildActionVersion2<?> getBuildFinishedAction() {
        return buildFinishedAction;
    }
}
