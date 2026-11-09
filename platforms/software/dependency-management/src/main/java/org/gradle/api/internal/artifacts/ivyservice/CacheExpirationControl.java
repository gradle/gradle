/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.internal.artifacts.cache.ArtifactResolutionControl;
import org.gradle.api.internal.artifacts.cache.DependencyResolutionControl;
import org.gradle.api.internal.artifacts.cache.ModuleResolutionControl;

import java.time.Duration;

/**
 * Determines whether cached external artifacts and metadata should be considered expired.
 */
public interface CacheExpirationControl {

    Expiry versionListExpiry(boolean hasVersions, Duration age);

    Expiry moduleExpiry(Duration age, boolean changing);

    Expiry moduleArtifactsExpiry(Duration age, boolean belongsToChangingModule, boolean moduleDescriptorInSync);

    Expiry artifactExpiry(boolean missing, Duration age, boolean belongsToChangingModule, boolean moduleDescriptorInSync);

    @VisibleForTesting
    ImmutableList<Action<? super DependencyResolutionControl>> getDependencyCacheRules();

    @VisibleForTesting
    ImmutableList<Action<? super ModuleResolutionControl>> getModuleCacheRules();

    @VisibleForTesting
    ImmutableList<Action<? super ArtifactResolutionControl>> getArtifactCacheRules();

    interface Expiry {

        boolean isMustCheck();

        Duration getKeepFor();

    }
}
