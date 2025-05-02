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

package org.gradle.api.internal.project.taskfactory;

import com.google.common.collect.ImmutableList;
import org.gradle.internal.properties.annotations.TypeMetadata;

import java.util.Optional;

public class TaskClassInfo {
    private final ImmutableList<TaskActionFactory> taskActionFactories;
    private final boolean cacheable;
    private final Optional<String> reasonNotToTrackState;
    private final TypeMetadata typeMetadata;

    public TaskClassInfo(ImmutableList<TaskActionFactory> taskActionFactories, boolean cacheable, Optional<String> reasonNotToTrackState, TypeMetadata typeMetadata) {
        this.taskActionFactories = taskActionFactories;
        this.cacheable = cacheable;
        this.reasonNotToTrackState = reasonNotToTrackState;
        this.typeMetadata = typeMetadata;
    }

    public ImmutableList<TaskActionFactory> getTaskActionFactories() {
        return taskActionFactories;
    }

    public boolean isCacheable() {
        return cacheable;
    }

    public Optional<String> getReasonNotToTrackState() {
        return reasonNotToTrackState;
    }

    public TypeMetadata getTypeMetadata() {
        return typeMetadata;
    }
}
