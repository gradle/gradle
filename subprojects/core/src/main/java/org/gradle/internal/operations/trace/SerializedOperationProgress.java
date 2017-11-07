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
import org.gradle.internal.logging.events.OperationIdentifier;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.internal.progress.OperationProgressEvent;

import java.util.Map;

class SerializedOperationProgress {

    final Object id;
    final long time;
    final Object details;
    final String detailsClassName;

    SerializedOperationProgress(BuildOperationDescriptor descriptor, OperationProgressEvent progressEvent) {
        this.id = ((OperationIdentifier) descriptor.getId()).getId();
        this.time = progressEvent.getTime();
        this.details = transform(progressEvent.getDetails());
        this.detailsClassName = details == null ? null : progressEvent.getDetails().getClass().getName();
    }

    private Object transform(Object details) {
        return details;
    }

    SerializedOperationProgress(Map<String, ?> map) {
        this.id = map.get("id");
        this.time = (Long) map.get("time");
        this.details = map.get("details");
        this.detailsClassName = (String) map.get("detailsClassName");
    }

    Map<String, ?> toMap() {
        ImmutableMap.Builder<String, Object> map = ImmutableMap.builder();

        // Order is optimised for humans looking at the log.

        if (details != null) {
            map.put("details", details);
            map.put("detailsClassName", detailsClassName);
        }

        map.put("id", id);
        map.put("time", time);

        return map.build();
    }

}
