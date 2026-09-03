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
import org.gradle.api.internal.provider.provenance.PropertyCallSites;
import org.gradle.api.internal.provider.provenance.MutationRecord;
import org.gradle.internal.code.UserCodeApplicationContext;
import org.gradle.internal.code.UserCodeSource;
import org.gradle.internal.problems.BoundedCallerStackCapturer;
import org.gradle.internal.state.ModelObject;
import org.jspecify.annotations.Nullable;

class ProjectBackedPropertyHost implements PropertyHost {
    private final ProjectInternal project;
    private final UserCodeApplicationContext userCodeApplicationContext;
    private final MutationOriginRegistry originRegistry;
    private final BoundedCallerStackCapturer callerStackCapturer;

    public ProjectBackedPropertyHost(
        ProjectInternal project,
        UserCodeApplicationContext userCodeApplicationContext,
        MutationOriginRegistry originRegistry,
        BoundedCallerStackCapturer callerStackCapturer
    ) {
        this.project = project;
        this.userCodeApplicationContext = userCodeApplicationContext;
        this.originRegistry = originRegistry;
        this.callerStackCapturer = callerStackCapturer;
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
        UserCodeSource source = application != null ? application.getSource() : null;
        return originRegistry.recordFor(source, kind, currentLocation());
    }

    /**
     * The call site performing the mutation, as {@code file:line}, or null when locations are not being
     * captured, the budget is spent, or no user frame with a line is on the stack.
     * <p>
     * Reuses the bounded stack walk the problems infrastructure already performs for the same purpose: it
     * stops at the first registered script rather than materialising a whole stack trace.
     */
    private @Nullable String currentLocation() {
        String instrumented = PropertyCallSites.current();
        if (instrumented != null) {
            // baked in at the call site, so it costs nothing and cannot fail to find a frame
            return instrumented;
        }
        if (!originRegistry.isWalkingStackForLocations() || !originRegistry.claimLocationBudget()) {
            return null;
        }
        return callerStackCapturer.captureCallSite();
    }
}
