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

package org.gradle.internal.execution.caching;

import com.google.common.collect.ImmutableList;
import org.gradle.caching.BuildCacheKey;
import org.gradle.internal.execution.history.BeforeExecutionState;

import javax.annotation.Nullable;

public interface CachingStateFactory {

    /**
     * Creates a CachingState for beforeExecutionState, that can be either Enabled or Disabled.
     *
     * A method accepts also a cacheSalt, that can be used to generate different {@link BuildCacheKey} keys for different build cache types.
     * CacheSalt is currently set just for "Next Gen" build cache and can be removed once "Next Gen" build cache becomes the only build cache.
     */
    CachingState createCachingState(BeforeExecutionState beforeExecutionState, @Nullable String cacheSalt, ImmutableList<CachingDisabledReason> cachingDisabledReasons);
}
