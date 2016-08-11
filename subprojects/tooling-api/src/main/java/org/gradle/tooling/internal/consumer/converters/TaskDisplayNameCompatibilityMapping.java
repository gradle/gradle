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

package org.gradle.tooling.internal.consumer.converters;

import org.gradle.api.Action;
import org.gradle.tooling.internal.adapter.ViewBuilder;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.model.GradleTask;

public class TaskDisplayNameCompatibilityMapping implements Action<ViewBuilder<?>> {
    private final boolean supportsTaskDisplayName;

    public TaskDisplayNameCompatibilityMapping(VersionDetails versionDetails) {
        supportsTaskDisplayName = versionDetails.supportsTaskDisplayName();
    }

    @Override
    public void execute(ViewBuilder<?> viewBuilder) {
        if (!supportsTaskDisplayName) {
            viewBuilder.mixInTo(GradleTask.class, TaskDisplayNameMixin.class);
        }
    }
}
