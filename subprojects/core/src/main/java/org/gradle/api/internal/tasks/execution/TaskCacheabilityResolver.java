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

import com.google.common.collect.ImmutableSortedSet;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.properties.OutputFilePropertySpec;
import org.gradle.internal.execution.caching.CachingDisabledReason;
import org.gradle.internal.fingerprint.overlap.OverlappingOutputs;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Optional;

public interface TaskCacheabilityResolver {
    Optional<CachingDisabledReason> shouldDisableCaching(
        boolean hasDeclaredOutputs,
        ImmutableSortedSet<OutputFilePropertySpec> outputFileProperties,
        TaskInternal task,
        Collection<SelfDescribingSpec<TaskInternal>> cacheIfSpecs,
        Collection<SelfDescribingSpec<TaskInternal>> doNotCacheIfSpecs,
        @Nullable OverlappingOutputs overlappingOutputs
    );
}
