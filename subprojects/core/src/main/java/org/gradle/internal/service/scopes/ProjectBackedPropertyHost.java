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
import org.gradle.api.internal.provider.provenance.PropertyCallSites;
import org.gradle.api.internal.provider.provenance.PropertyProvenanceKind;
import org.gradle.api.internal.provider.provenance.PropertyProvenanceRecord;
import org.gradle.api.internal.provider.provenance.PropertyProvenanceRegistry;
import org.gradle.internal.code.UserCodeApplicationContext;
import org.gradle.internal.code.UserCodeSource;
import org.gradle.internal.execution.WorkExecutionTracker;
import org.gradle.internal.problems.BoundedCallerStackCapturer;
import org.gradle.internal.state.ModelObject;
import org.jspecify.annotations.Nullable;

class ProjectBackedPropertyHost implements PropertyHost {
    private final ProjectInternal project;
    private final UserCodeApplicationContext userCodeApplicationContext;
    private final PropertyProvenanceRegistry provenanceRegistry;
    private final BoundedCallerStackCapturer callerStackCapturer;
    private final WorkExecutionTracker workExecutionTracker;

    public ProjectBackedPropertyHost(
        ProjectInternal project,
        UserCodeApplicationContext userCodeApplicationContext,
        PropertyProvenanceRegistry provenanceRegistry,
        BoundedCallerStackCapturer callerStackCapturer,
        WorkExecutionTracker workExecutionTracker
    ) {
        this.project = project;
        this.userCodeApplicationContext = userCodeApplicationContext;
        this.provenanceRegistry = provenanceRegistry;
        this.callerStackCapturer = callerStackCapturer;
        this.workExecutionTracker = workExecutionTracker;
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
    public boolean tracksPropertyProvenance() {
        return provenanceRegistry.isEnabled();
    }

    @Nullable
    @Override
    public PropertyProvenanceRecord currentPropertyBinding(PropertyProvenanceKind kind) {
        return provenanceRegistry.recordFor(currentUserCodeSource(), kind, usableLocation(PropertyCallSites.current()));
    }

    @Nullable
    @Override
    public PropertyProvenanceRecord currentPropertyFailure(PropertyProvenanceKind kind) {
        String location = PropertyCallSites.current();
        if (location == null) {
            // Failed operations are rare, so walking here is both cheaper and more complete than retaining
            // a stack or operation context for every successful get/set.
            location = callerStackCapturer.captureCallSite();
        }
        location = usableLocation(location);

        String origin = workExecutionTracker.getCurrentTask()
            .map(task -> "task '" + task.getPath() + "' action")
            .orElseGet(() -> {
                UserCodeSource source = currentUserCodeSource();
                return source == null ? "unknown code" : source.getDisplayName().getDisplayName();
            });
        return provenanceRegistry.failureFor(origin, kind, location);
    }

    private @Nullable UserCodeSource currentUserCodeSource() {
        UserCodeApplicationContext.Application application = userCodeApplicationContext.current();
        return application == null ? null : application.getSource();
    }

    /**
     * Full Groovy DSL call-site support needs its dynamic-dispatch interception path and is intentionally not
     * part of this slice. Other JVM user code, including Kotlin DSL, keeps its exact line.
     */
    private static @Nullable String usableLocation(@Nullable String location) {
        if (location == null) {
            return null;
        }
        int separator = location.lastIndexOf(':');
        String fileName = separator < 0 ? location : location.substring(0, separator);
        return fileName.endsWith(".gradle") ? null : location;
    }
}
