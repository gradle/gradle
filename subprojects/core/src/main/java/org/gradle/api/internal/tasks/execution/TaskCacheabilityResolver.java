/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.tasks.execution;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.properties.TaskProperties;
import org.gradle.internal.execution.caching.CachingDisabledReason;
import org.gradle.internal.execution.history.OverlappingOutputs;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;

public interface TaskCacheabilityResolver {
    Optional<CachingDisabledReason> shouldDisableCaching(
        TaskInternal task,
        TaskProperties taskProperties,
        Collection<SelfDescribingSpec<TaskInternal>> cacheIfSpecs,
        Collection<SelfDescribingSpec<TaskInternal>> doNotCacheIfSpecs,
        @Nullable OverlappingOutputs overlappingOutputs
    );
}
