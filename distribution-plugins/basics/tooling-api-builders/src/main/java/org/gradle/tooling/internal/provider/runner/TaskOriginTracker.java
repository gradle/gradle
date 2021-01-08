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

package org.gradle.tooling.internal.provider.runner;

import org.gradle.api.internal.project.taskfactory.TaskIdentity;
import org.gradle.api.internal.tasks.RealizeTaskBuildOperationType;
import org.gradle.api.internal.tasks.RegisterTaskBuildOperationType;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.tooling.internal.protocol.events.InternalPluginIdentifier;
import org.gradle.tooling.internal.provider.runner.PluginApplicationTracker.PluginApplication;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class TaskOriginTracker implements BuildOperationListener {

    private final Map<Long, InternalPluginIdentifier> origins = new ConcurrentHashMap<>();
    private final PluginApplicationTracker pluginApplicationTracker;

    TaskOriginTracker(PluginApplicationTracker pluginApplicationTracker) {
        this.pluginApplicationTracker = pluginApplicationTracker;
    }

    @Nullable
    InternalPluginIdentifier getOriginPlugin(TaskIdentity<?> taskIdentity) {
        return origins.get(taskIdentity.uniqueId);
    }

    @Override
    public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
        if (buildOperation.getDetails() instanceof RealizeTaskBuildOperationType.Details) {
            RealizeTaskBuildOperationType.Details details = (RealizeTaskBuildOperationType.Details) buildOperation.getDetails();
            storeOrigin(buildOperation, details.getTaskId());
        } else if (buildOperation.getDetails() instanceof RegisterTaskBuildOperationType.Details) {
            RegisterTaskBuildOperationType.Details details = (RegisterTaskBuildOperationType.Details) buildOperation.getDetails();
            storeOrigin(buildOperation, details.getTaskId());
        }
    }

    private void storeOrigin(BuildOperationDescriptor buildOperation, long taskId) {
        origins.computeIfAbsent(taskId, key -> {
            PluginApplication pluginApplication = pluginApplicationTracker.findRunningPluginApplication(buildOperation.getParentId());
            return pluginApplication == null ? null : pluginApplication.getPlugin();
        });
    }

    @Override
    public void progress(OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent) {
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
        // origins have to be stored until the end of the build
    }

}
