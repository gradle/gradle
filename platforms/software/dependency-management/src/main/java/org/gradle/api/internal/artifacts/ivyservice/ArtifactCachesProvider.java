/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.cache.GlobalCache;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.Closeable;
import java.util.Optional;
import java.util.function.BiFunction;

@ServiceScope(Scope.UserHome.class)
public interface ArtifactCachesProvider extends Closeable, GlobalCache {
    String READONLY_CACHE_ENV_VAR = "GRADLE_RO_DEP_CACHE";

    ArtifactCacheMetadata getWritableCacheMetadata();
    Optional<ArtifactCacheMetadata> getReadOnlyCacheMetadata();

    ArtifactCacheLockingAccessCoordinator getWritableCacheAccessCoordinator();
    Optional<ArtifactCacheLockingAccessCoordinator> getReadOnlyCacheAccessCoordinator();

    default <T> T withWritableCache(BiFunction<? super ArtifactCacheMetadata, ? super ArtifactCacheLockingAccessCoordinator, T> function) {
        return function.apply(getWritableCacheMetadata(), getWritableCacheAccessCoordinator());
    }

    default <T> Optional<T> withReadOnlyCache(BiFunction<? super ArtifactCacheMetadata, ? super ArtifactCacheLockingAccessCoordinator, T> function) {
        return getReadOnlyCacheMetadata().map(artifactCacheMetadata -> function.apply(artifactCacheMetadata, getReadOnlyCacheAccessCoordinator().get()));
    }
}
