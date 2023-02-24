/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.caching;

import org.gradle.api.Incubating;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public interface AsyncBuildCacheService extends BuildCacheService { // TODO temp fix to reduce the scope of the changes and just downcast where required -> DefaultNextGenBuildCacheAccess
    @Incubating
    default CompletableFuture<Boolean> containsAsync(BuildCacheKey key) {
        return loadAsync(key, __ -> {});
    }

    CompletableFuture<Boolean> loadAsync(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException;

    CompletableFuture<Void> storeAsync(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException;


    @Override
    default boolean contains(BuildCacheKey key) {
        try {
            return containsAsync(key).get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    default boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
        try {
            return loadAsync(key, reader).get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    default void store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException {
        try {
            storeAsync(key, writer).get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
