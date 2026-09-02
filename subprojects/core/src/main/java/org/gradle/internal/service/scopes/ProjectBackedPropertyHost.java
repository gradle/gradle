/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.service.scopes;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.provider.PropertyHost;
import org.gradle.api.internal.provider.provenance.MutationKind;
import org.gradle.api.internal.provider.provenance.MutationOriginRegistry;
import org.gradle.api.internal.provider.provenance.MutationRecord;
import org.gradle.internal.code.UserCodeApplicationContext;
import org.gradle.internal.state.ModelObject;
import org.jspecify.annotations.Nullable;

class ProjectBackedPropertyHost implements PropertyHost {
    private final ProjectInternal project;
    private final UserCodeApplicationContext userCodeApplicationContext;
    private final MutationOriginRegistry originRegistry;

    public ProjectBackedPropertyHost(
        ProjectInternal project,
        UserCodeApplicationContext userCodeApplicationContext,
        MutationOriginRegistry originRegistry
    ) {
        this.project = project;
        this.userCodeApplicationContext = userCodeApplicationContext;
        this.originRegistry = originRegistry;
    }

    @Nullable
    @Override
    public String beforeRead(@Nullable ModelObject producer) {
        if (!project.getState().hasCompleted()) {
            return "configuration of " + project.getDisplayName() + " has not completed yet";
        } else if (producer != null) {
            TaskInternal producerTask = (TaskInternal) producer.getTaskThatOwnsThisObject();
            if (producerTask != null && producerTask.getState().isConfigurable()) {
                // Currently cannot tell the difference between access from the producing task and access from outside, so assume
                // all access after the task has started execution is ok
                return producerTask + " has not completed yet";
            }
        }
        return null;
    }

    @Override
    public boolean tracksMutationProvenance() {
        return originRegistry.isEnabled();
    }

    @Nullable
    @Override
    public MutationRecord currentMutation(MutationKind kind) {
        UserCodeApplicationContext.Application application = userCodeApplicationContext.current();
        return originRegistry.recordFor(application != null ? application.getSource() : null, kind);
    }
}
