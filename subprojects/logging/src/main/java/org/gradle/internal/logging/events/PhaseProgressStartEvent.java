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
package org.gradle.internal.logging.events;

import com.google.common.base.Preconditions;
import org.gradle.api.Nullable;

/**
 * Specialized start event for build phases.
 * These are assumed to be serial for now and have a known number of direct child build operations.
 */
public class PhaseProgressStartEvent extends ProgressStartEvent {
    private final long children;

    public PhaseProgressStartEvent(
            OperationIdentifier progressOperationId,
            @Nullable OperationIdentifier parentProgressOperationId,
            long timestamp,
            String category,
            String description,
            @Nullable String shortDescription,
            @Nullable String loggingHeader,
            String status,
            Object buildOperationId,
            @Nullable Object parentBuildOperationId,
            long children) {
        super(progressOperationId, parentProgressOperationId, timestamp, category, description, shortDescription, loggingHeader, status, buildOperationId, parentBuildOperationId);
        Preconditions.checkNotNull(buildOperationId, "Cannot start a build phase without a build operation id");
        this.children = children;
    }

    public long getChildren() {
        return children;
    }
}
