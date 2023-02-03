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
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.OperationStartEvent;

import java.util.Map;

import static org.gradle.internal.operations.trace.BuildOperationTrace.toSerializableModel;

class SerializedOperationStart implements SerializedOperation {

    final long id;
    final Long parentId;
    final String displayName;

    final long startTime;

    final Object details;
    final String detailsClassName;

    SerializedOperationStart(BuildOperationDescriptor descriptor, OperationStartEvent startEvent) {
        this.id = descriptor.getId().getId();
        this.parentId = descriptor.getParentId() == null ? null : descriptor.getParentId().getId();
        this.displayName = descriptor.getDisplayName();
        this.startTime = startEvent.getStartTime();
        this.details = toSerializableModel(descriptor.getDetails());
        this.detailsClassName = details == null ? null : descriptor.getDetails().getClass().getName();
    }

    SerializedOperationStart(Map<String, ?> map) {
        this.id = ((Integer) map.get("id")).longValue();
        Integer parentId = (Integer) map.get("parentId");
        this.parentId = parentId == null ? null : parentId.longValue();
        this.displayName = (String) map.get("displayName");
        this.startTime = (Long) map.get("startTime");
        this.details = map.get("details");
        this.detailsClassName = (String) map.get("detailsClassName");
    }

    @Override
    public Map<String, ?> toMap() {
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
