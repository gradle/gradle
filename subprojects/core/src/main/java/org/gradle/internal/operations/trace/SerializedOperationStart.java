/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.operations.trace;

import com.google.common.collect.ImmutableMap;
import org.gradle.api.internal.plugins.ApplyPluginBuildOperationType;
import org.gradle.internal.execution.ExecuteTaskBuildOperationType;
import org.gradle.internal.logging.events.OperationIdentifier;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.internal.progress.OperationStartEvent;

import java.util.HashMap;
import java.util.Map;

class SerializedOperationStart {

    final Object id;
    final Object parentId;
    final String displayName;

    final long startTime;

    final Object details;
    final String detailsClassName;

    SerializedOperationStart(BuildOperationDescriptor descriptor, OperationStartEvent startEvent) {
        this.id = ((OperationIdentifier) descriptor.getId()).getId();
        this.parentId = descriptor.getParentId() == null ? null : ((OperationIdentifier) descriptor.getParentId()).getId();
        this.displayName = descriptor.getDisplayName();
        this.startTime = startEvent.getStartTime();
        this.details = transform(descriptor.getDetails());
        this.detailsClassName = details == null ? null : descriptor.getDetails().getClass().getName();
    }

    private Object transform(Object details) {
        if (details instanceof ExecuteTaskBuildOperationType.Details) {
            ExecuteTaskBuildOperationType.Details cast = (ExecuteTaskBuildOperationType.Details) details;
            Map<String, Object> map = new HashMap<String, Object>();
            map.put("buildPath", cast.getBuildPath());
            map.put("taskPath", cast.getTaskPath());
            map.put("taskClass", cast.getTaskClass().getName());
            map.put("taskId", cast.getTaskId());
            return map;
        }

        if (details instanceof ApplyPluginBuildOperationType.Details) {
            ApplyPluginBuildOperationType.Details cast = (ApplyPluginBuildOperationType.Details) details;
            Map<String, Object> map = new HashMap<String, Object>();
            map.put("pluginId", cast.getPluginId());
            map.put("pluginClass", cast.getPluginClass().getName());
            map.put("targetType", cast.getTargetType());
            map.put("targetPath", cast.getTargetPath());
            map.put("buildPath", cast.getBuildPath());
            return map;
        }

        return details;
    }

    SerializedOperationStart(Map<String, ?> map) {
        this.id = map.get("id");
        this.parentId = map.get("parentId");
        this.displayName = (String) map.get("displayName");
        this.startTime = (Long) map.get("startTime");
        this.details = map.get("details");
        this.detailsClassName = (String) map.get("detailsClassName");
    }

    Map<String, ?> toMap() {
        ImmutableMap.Builder<String, Object> map = ImmutableMap.builder();

        // Order is optimised for humans looking at the log.

        map.put("displayName", displayName);

        if (details != null) {
            map.put("details", details);
            map.put("detailsClassName", detailsClassName);
        }

        map.put("id", id);
        if (parentId != null) {
            map.put("parentId", parentId);
        }
        map.put("startTime", startTime);

        return map.build();
    }

}
